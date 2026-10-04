package dev.maxsauerwein.qis;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import dev.maxsauerwein.qis.model.Credentials;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.model.ModuleCardData;
import dev.maxsauerwein.qis.network.QISClient;
import dev.maxsauerwein.qis.storage.AverageInclusionStore;
import dev.maxsauerwein.qis.storage.CredentialStore;
import dev.maxsauerwein.qis.storage.GradeCacheStore;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.storage.ModuleArchiveStore;
import dev.maxsauerwein.qis.storage.SeenGradesStore;
import dev.maxsauerwein.qis.storage.SessionCookieStore;
import dev.maxsauerwein.qis.util.GradeAnalysis;
import dev.maxsauerwein.qis.util.GradeCardBuilder;

public final class MainActivity extends AppCompatActivity implements ModuleCardAdapter.Listener {

    private CredentialStore credentialStore;
    private GradeSettingsStore gradeSettingsStore;
    private GradeCacheStore gradeCacheStore;
    private SeenGradesStore seenGradesStore;
    private SessionCookieStore sessionCookieStore;
    private ModuleArchiveStore moduleArchiveStore;
    private AverageInclusionStore averageInclusionStore;
    private QISClient qisClient;

    private List<GradeTable> currentGradeTables;
    private Set<String> newModuleKeys = Collections.emptySet();
    private Set<String> archivedModuleKeys;
    private Set<String> excludedFromAverageModuleKeys;
    private boolean isArchiveExpanded = false;

    private View loadingContainer;
    private View loginContainer;
    private View gradesContainer;
    private View errorContainer;

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextView loginError;
    private MaterialButton loginButton;
    private View loginProgress;

    private TextView kpiOverline;
    private TextView kpiAverageValue;
    private TextView kpiEctsValue;
    private LinearProgressIndicator kpiEctsProgress;
    private ChipGroup filterChipGroup;
    private MaterialButton sortButton;
    private SwipeRefreshLayout gradesSwipeRefresh;
    private RecyclerView gradesRecyclerView;
    private ModuleCardAdapter gradesAdapter;

    private TextView errorMessage;

    private GradeCardBuilder.Filter currentFilter = GradeCardBuilder.Filter.ALL;
    private GradeCardBuilder.Sort currentSort = GradeCardBuilder.Sort.NONE;

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Zugangsdaten geändert, Noten mit dem neuen Login erneut abrufen.
                    refresh();
                } else if (result.getResultCode() == RESULT_FIRST_USER) {
                    // In den Einstellungen abgemeldet.
                    showOnly(loginContainer);
                } else if (currentGradeTables != null) {
                    // Einfache Zurück-Navigation: Die Zugangsdaten sind unverändert, daher wird
                    // der SSO-Login nicht erneut ausgeführt. Studiengang, Semester oder
                    // Ziel-ECTS könnten sich aber geändert haben, daher wird die Kopfzeile aus
                    // den bereits geladenen Notentabellen neu gezeichnet.
                    showGrades(currentGradeTables);
                }
            });

    private final ActivityResultLauncher<Intent> displayOptionsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (currentGradeTables != null) {
                    showGrades(currentGradeTables);
                }
            });

    /** Einmaliger Einrichtungsschritt, wenn QIS mehrere Abschluss-/Fach-Kombinationen meldet.
     *  DegreeSetupActivity lässt sich nicht ohne vollständige Auswahl verlassen (siehe dort), das
     *  Ergebnis ist daher immer eine gültige Auswahl. */
    private final ActivityResultLauncher<Intent> degreeSetupLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (currentGradeTables != null) {
                    presentTables(currentGradeTables);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        credentialStore = new CredentialStore(getApplicationContext());
        gradeSettingsStore = new GradeSettingsStore(getApplicationContext());
        gradeCacheStore = new GradeCacheStore(getApplicationContext());
        seenGradesStore = new SeenGradesStore(getApplicationContext());
        sessionCookieStore = new SessionCookieStore(getApplicationContext());
        moduleArchiveStore = new ModuleArchiveStore(getApplicationContext());
        averageInclusionStore = new AverageInclusionStore(getApplicationContext());
        qisClient = new QISClient(getApplicationContext());
        archivedModuleKeys = moduleArchiveStore.load();
        excludedFromAverageModuleKeys = averageInclusionStore.loadExcludedModuleKeys();

        loadingContainer = findViewById(R.id.loadingContainer);
        loginContainer = findViewById(R.id.loginContainer);
        gradesContainer = findViewById(R.id.gradesContainer);
        errorContainer = findViewById(R.id.errorContainer);

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginError = findViewById(R.id.loginError);
        loginButton = findViewById(R.id.loginButton);
        loginProgress = findViewById(R.id.loginProgress);
        loginButton.setOnClickListener(v -> attemptLogin());

        MaterialToolbar gradesToolbar = findViewById(R.id.gradesToolbar);
        gradesToolbar.setOnMenuItemClickListener(this::onGradesMenuItemClick);

        kpiOverline = findViewById(R.id.kpiOverline);
        kpiAverageValue = findViewById(R.id.kpiAverageValue);
        kpiEctsValue = findViewById(R.id.kpiEctsValue);
        kpiEctsProgress = findViewById(R.id.kpiEctsProgress);

        GradeSettingsStore.Settings savedViewState = gradeSettingsStore.load();
        currentFilter = parseFilter(savedViewState.filter);
        currentSort = parseSort(savedViewState.sort);

        filterChipGroup = findViewById(R.id.filterChipGroup);
        filterChipGroup.check(filterToChipId(currentFilter));
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.filterChipOpen) {
                currentFilter = GradeCardBuilder.Filter.OPEN;
            } else {
                currentFilter = GradeCardBuilder.Filter.ALL;
            }
            saveViewState();
            if (currentGradeTables != null) {
                showGrades(currentGradeTables);
            }
        });

        sortButton = findViewById(R.id.sortButton);
        sortButton.setOnClickListener(this::showSortMenu);
        updateSortButtonLabel();

        gradesSwipeRefresh = findViewById(R.id.gradesSwipeRefresh);
        gradesRecyclerView = findViewById(R.id.gradesRecyclerView);
        gradesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        gradesAdapter = new ModuleCardAdapter(this);
        gradesRecyclerView.setAdapter(gradesAdapter);
        new ItemTouchHelper(new SwipeCallback()).attachToRecyclerView(gradesRecyclerView);
        gradesSwipeRefresh.setOnRefreshListener(this::refresh);

        errorMessage = findViewById(R.id.errorMessage);
        MaterialButton errorRetryButton = findViewById(R.id.errorRetryButton);
        MaterialButton errorLogoutButton = findViewById(R.id.errorLogoutButton);
        errorRetryButton.setOnClickListener(v -> refresh());
        errorLogoutButton.setOnClickListener(v -> logout());

        bootstrap();
    }

    /** Passiver Abruf beim App-Start: zeigt vorhandene gecachte Daten sofort an (kein leerer
     *  Ladescreen) und holt nur bei Bedarf im Hintergrund nach. */
    private void bootstrap() {
        Credentials credentials = credentialStore.load();
        if (credentials == null) {
            showOnly(loginContainer);
            return;
        }
        List<GradeTable> cached = gradeCacheStore.loadTables();
        if (cached != null) {
            loadGrades(cached);
        } else {
            showOnly(loadingContainer);
        }
        performFetch(credentials, false);
    }

    /** Zentrale Stelle für jeden Wechsel zu (neu geladenen oder gecachten) Notentabellen:
     *  ermittelt einmalig die frisch benoteten Module der aktiven Tabelle gegenüber dem zuletzt
     *  gesehenen Stand, bevor die Tabelle angezeigt wird. So läuft der Vergleich nur bei echten
     *  Datenwechseln, nicht bei jedem Neuzeichnen durch Filter/Sortierung/Archivieren. */
    private void loadGrades(List<GradeTable> gradeTables) {
        currentGradeTables = gradeTables;
        presentTables(gradeTables);
    }

    /** Wendet die Auto-Erkennung auf die Profil-Einstellungen an und zeigt bei mehreren noch
     *  unentschiedenen Abschluss-/Fach-Kombinationen erst den Ersteinrichtungs-Dialog, statt
     *  direkt eine (möglicherweise falsche) Tabelle anzuzeigen. */
    private void presentTables(List<GradeTable> gradeTables) {
        GradeSettingsStore.Settings settings = GradeSettingsStore.loadApplyingAutoDetection(this, gradeTables);
        if (settings.needsDegreeSetup()) {
            degreeSetupLauncher.launch(DegreeSetupActivity.createIntent(this));
            return;
        }
        GradeTable active = settings.activeTable(gradeTables);
        if (active != null) {
            newModuleKeys = seenGradesStore.newlyGradedModuleKeys(active);
        }
        showGrades(gradeTables);
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, R.string.sort_none);
        popup.getMenu().add(0, 1, 1, R.string.sort_grade);
        popup.getMenu().add(0, 2, 2, R.string.sort_semester);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                currentSort = GradeCardBuilder.Sort.GRADE;
            } else if (id == 2) {
                currentSort = GradeCardBuilder.Sort.SEMESTER;
            } else {
                currentSort = GradeCardBuilder.Sort.NONE;
            }
            updateSortButtonLabel();
            saveViewState();
            if (currentGradeTables != null) {
                showGrades(currentGradeTables);
            }
            return true;
        });
        popup.show();
    }

    private void saveViewState() {
        GradeSettingsStore.Settings settings = gradeSettingsStore.load();
        settings.filter = currentFilter.name();
        settings.sort = currentSort.name();
        gradeSettingsStore.save(settings);
    }

    private GradeCardBuilder.Filter parseFilter(String value) {
        try {
            return GradeCardBuilder.Filter.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return GradeCardBuilder.Filter.ALL;
        }
    }

    private GradeCardBuilder.Sort parseSort(String value) {
        try {
            return GradeCardBuilder.Sort.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return GradeCardBuilder.Sort.NONE;
        }
    }

    private int filterToChipId(GradeCardBuilder.Filter filter) {
        switch (filter) {
            case OPEN: return R.id.filterChipOpen;
            case ALL:
            default: return R.id.filterChipAll;
        }
    }

    private void updateSortButtonLabel() {
        switch (currentSort) {
            case GRADE:
                sortButton.setText(R.string.sort_grade);
                break;
            case SEMESTER:
                sortButton.setText(R.string.sort_semester);
                break;
            case NONE:
            default:
                sortButton.setText(R.string.sort_default);
                break;
        }
    }

    private boolean onGradesMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            settingsLauncher.launch(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_display_options) {
            displayOptionsLauncher.launch(DisplayOptionsActivity.createIntent(this));
            return true;
        }
        return false;
    }

    /** Bewusster Force-Refresh, ausgelöst durch Pull-to-refresh oder den Retry-Button. */
    private void refresh() {
        Credentials credentials = credentialStore.load();
        if (credentials == null) {
            showOnly(loginContainer);
            return;
        }
        performFetch(credentials, true);
    }

    /** Zentrale Stelle für alle Notenspiegel-Abrufe: respektiert Cache-Frische, Force-Refresh und
     *  die harte 30-Sekunden-Untergrenze zwischen Versuchen. Ruft das QIS-Portal nur, wenn
     *  wirklich nötig, und fällt bei einem Fehler auf zuletzt gecachte Daten zurück, falls
     *  vorhanden -- kein automatischer Sofort-Retry. */
    private void performFetch(Credentials credentials, boolean force) {
        if (!gradeCacheStore.canAttempt()) {
            gradesSwipeRefresh.setRefreshing(false);
            return;
        }
        if (!force && gradeCacheStore.isFresh()) {
            gradesSwipeRefresh.setRefreshing(false);
            List<GradeTable> cached = gradeCacheStore.loadTables();
            if (cached != null) {
                loadGrades(cached);
            }
            return;
        }
        gradeCacheStore.recordAttempt();
        qisClient.fetchGrades(credentials.username, credentials.password, true, new QISClient.Callback() {
            @Override
            public void onSuccess(List<GradeTable> gradeTables) {
                gradeCacheStore.saveTables(gradeTables);
                gradesSwipeRefresh.setRefreshing(false);
                loadGrades(gradeTables);
            }

            @Override
            public void onError(Exception error) {
                gradesSwipeRefresh.setRefreshing(false);
                List<GradeTable> cached = gradeCacheStore.loadTables();
                if (cached != null) {
                    loadGrades(cached);
                    Snackbar.make(gradesRecyclerView, error.getMessage(), Snackbar.LENGTH_LONG).show();
                } else {
                    showError(error.getMessage());
                }
            }
        });
    }

    private void attemptLogin() {
        String username = textOf(usernameInput);
        String password = textOf(passwordInput);
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }
        setLoginLoading(true);
        qisClient.fetchGrades(username, password, false, new QISClient.Callback() {
            @Override
            public void onSuccess(List<GradeTable> gradeTables) {
                setLoginLoading(false);
                credentialStore.save(new Credentials(username, password));
                gradeCacheStore.saveTables(gradeTables);
                loadGrades(gradeTables);
            }

            @Override
            public void onError(Exception error) {
                setLoginLoading(false);
                loginError.setText(error.getMessage());
                loginError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setLoginLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            loginError.setVisibility(View.GONE);
        }
    }

    /** Baut Kopfzeile, Liste und Archiv-Abschnitt für die bestätigte (oder einzig möglichen)
     *  Abschluss-/Fach-Wahl neu auf. Setzt voraus, dass needsDegreeSetup() bereits false ist
     *  (siehe presentTables) -- der Fallback auf die erste Tabelle greift daher nur defensiv. */
    private void showGrades(List<GradeTable> gradeTables) {
        currentGradeTables = gradeTables;
        GradeSettingsStore.Settings settings = gradeSettingsStore.load();
        GradeTable active = settings.activeTable(gradeTables);
        if (active == null) {
            active = gradeTables.get(0);
        }

        List<ModuleCardData> allCards = GradeCardBuilder.buildCards(active, settings, newModuleKeys);
        List<ModuleCardData> activeCards = new ArrayList<>();
        List<ModuleCardData> archivedCards = new ArrayList<>();
        for (ModuleCardData card : allCards) {
            if (archivedModuleKeys.contains(card.id())) {
                archivedCards.add(card);
            } else {
                activeCards.add(card);
            }
        }
        List<ModuleCardData> visibleCards = GradeCardBuilder.sorted(
                GradeCardBuilder.filtered(activeCards, currentFilter), currentSort);

        Double average = GradeAnalysis.average(active, settings.averageMode, excludedFromAverageModuleKeys);
        double totalEcts = GradeCardBuilder.totalEarnedEcts(allCards);

        kpiOverline.setText(overlineText(settings));
        kpiAverageValue.setText(average != null ? String.format(Locale.GERMANY, "%.2f", average) : "–");
        kpiEctsValue.setText(totalEcts == Math.floor(totalEcts)
                ? String.format(Locale.GERMANY, "%.0f", totalEcts)
                : String.format(Locale.GERMANY, "%.1f", totalEcts));
        double progress = Math.min(Math.max(totalEcts / settings.effectiveTargetEcts(), 0), 1);
        kpiEctsProgress.setProgress((int) Math.round(progress * 100));

        List<ModuleCardAdapter.Row> rows = buildRows(visibleCards, archivedCards);
        gradesAdapter.submitRows(rows, settings.visibleAttemptFields, settings.customColors, excludedFromAverageModuleKeys);
        showOnly(gradesContainer);
    }

    /** Port von GradesView.swifts displaySections/groupedSections: bei Sortierung "Standard"
     *  gruppiert nach dem QIS-Abschnitt (Kernmodule/Pflichtmodule/Wahlpflichtmodule), bei
     *  "Semester" nach dem Semester des letzten Versuchs, bei "Note" keine Gruppierung. Hängt bei
     *  vorhandenen archivierten Karten die klappbare Archiv-Zeile an. */
    private List<ModuleCardAdapter.Row> buildRows(List<ModuleCardData> visibleCards, List<ModuleCardData> archivedCards) {
        List<ModuleCardAdapter.Row> rows = new ArrayList<>();
        switch (currentSort) {
            case SEMESTER:
                appendGrouped(rows, visibleCards, this::semesterTitle, true);
                break;
            case GRADE:
                for (ModuleCardData card : visibleCards) {
                    rows.add(ModuleCardAdapter.Row.moduleCard(card, false));
                }
                break;
            case NONE:
            default:
                appendGrouped(rows, visibleCards, card -> card.sectionTitle, false);
                break;
        }
        if (!archivedCards.isEmpty()) {
            rows.add(ModuleCardAdapter.Row.archiveToggle(archivedCards.size(), isArchiveExpanded));
            if (isArchiveExpanded) {
                for (ModuleCardData card : archivedCards) {
                    rows.add(ModuleCardAdapter.Row.moduleCard(card, true));
                }
            }
        }
        return rows;
    }

    private interface TitleFn {
        String titleFor(ModuleCardData card);
    }

    private void appendGrouped(List<ModuleCardAdapter.Row> rows, List<ModuleCardData> cards, TitleFn titleFn,
                                boolean isSemesterGrouping) {
        String lastTitle = null;
        boolean first = true;
        for (ModuleCardData card : cards) {
            String title = titleFn.titleFor(card);
            if (first || !Objects.equals(title, lastTitle)) {
                if (title != null) {
                    rows.add(isSemesterGrouping
                            ? ModuleCardAdapter.Row.semesterDivider(title)
                            : ModuleCardAdapter.Row.sectionHeader(title));
                }
                lastTitle = title;
                first = false;
            }
            rows.add(ModuleCardAdapter.Row.moduleCard(card, false));
        }
    }

    private String semesterTitle(ModuleCardData card) {
        String semester = card.attempts.isEmpty() ? "" : card.attempts.get(card.attempts.size() - 1).semester.trim();
        return semester.isEmpty() ? getString(R.string.no_semester_section_title) : semester;
    }

    private String overlineText(GradeSettingsStore.Settings settings) {
        List<String> parts = new ArrayList<>();
        String studiengang = settings.studiengang().trim();
        if (!studiengang.isEmpty()) {
            parts.add(studiengang);
        }
        if (settings.semester > 0) {
            parts.add(settings.semester + ". Semester");
        }
        return parts.isEmpty() ? getString(R.string.grades_title) : String.join(" · ", parts);
    }

    @Override
    public void onToggleAverageInclusion(ModuleCardData card, boolean includedInAverage) {
        if (includedInAverage) {
            excludedFromAverageModuleKeys.remove(card.id());
        } else {
            excludedFromAverageModuleKeys.add(card.id());
        }
        averageInclusionStore.saveExcludedModuleKeys(excludedFromAverageModuleKeys);
        if (currentGradeTables != null) {
            showGrades(currentGradeTables);
        }
    }

    @Override
    public void onArchive(ModuleCardData card) {
        archivedModuleKeys.add(card.id());
        moduleArchiveStore.save(archivedModuleKeys);
        if (currentGradeTables != null) {
            showGrades(currentGradeTables);
        }
    }

    @Override
    public void onRestore(ModuleCardData card) {
        archivedModuleKeys.remove(card.id());
        moduleArchiveStore.save(archivedModuleKeys);
        if (currentGradeTables != null) {
            showGrades(currentGradeTables);
        }
    }

    @Override
    public void onToggleArchiveSection() {
        isArchiveExpanded = !isArchiveExpanded;
        if (currentGradeTables != null) {
            showGrades(currentGradeTables);
        }
    }

    /** Erlaubt das Wegwischen nur für Modul-Karten (nicht für Header/Trenner/Archiv-Zeile) und
     *  ruft je nach Herkunft Archivieren oder Wiederherstellen auf. Port von GradesView.swifts
     *  .swipeActions. */
    private final class SwipeCallback extends ItemTouchHelper.SimpleCallback {

        SwipeCallback() {
            super(0, ItemTouchHelper.LEFT);
        }

        @Override
        public int getMovementFlags(@androidx.annotation.NonNull RecyclerView recyclerView,
                                     @androidx.annotation.NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof ModuleCardAdapter.CardHolder) {
                return super.getMovementFlags(recyclerView, viewHolder);
            }
            return 0;
        }

        @Override
        public boolean onMove(@androidx.annotation.NonNull RecyclerView recyclerView,
                               @androidx.annotation.NonNull RecyclerView.ViewHolder viewHolder,
                               @androidx.annotation.NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@androidx.annotation.NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            ModuleCardAdapter.Row row = gradesAdapter.rowAt(viewHolder.getBindingAdapterPosition());
            if (row.type != ModuleCardAdapter.RowType.MODULE_CARD) {
                return;
            }
            if (row.isArchivedCard) {
                onRestore(row.card);
            } else {
                onArchive(row.card);
            }
        }
    }

    private void showError(String message) {
        errorMessage.setText(message);
        showOnly(errorContainer);
    }

    private void logout() {
        credentialStore.clear();
        gradeCacheStore.clear();
        seenGradesStore.clear();
        sessionCookieStore.clear();
        moduleArchiveStore.clear();
        averageInclusionStore.clear();
        archivedModuleKeys = moduleArchiveStore.load();
        excludedFromAverageModuleKeys = averageInclusionStore.loadExcludedModuleKeys();
        currentGradeTables = null;
        showOnly(loginContainer);
    }

    private void showOnly(View visible) {
        for (View container : new View[]{loadingContainer, loginContainer, gradesContainer, errorContainer}) {
            container.setVisibility(container == visible ? View.VISIBLE : View.GONE);
        }
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
