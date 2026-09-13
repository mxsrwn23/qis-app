package dev.maxsauerwein.qis;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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
import dev.maxsauerwein.qis.storage.CredentialStore;
import dev.maxsauerwein.qis.storage.GradeCacheStore;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.storage.SeenGradesStore;
import dev.maxsauerwein.qis.storage.SessionCookieStore;
import dev.maxsauerwein.qis.util.GradeAnalysis;
import dev.maxsauerwein.qis.util.GradeCardBuilder;

public final class MainActivity extends AppCompatActivity {

    private CredentialStore credentialStore;
    private GradeSettingsStore gradeSettingsStore;
    private GradeCacheStore gradeCacheStore;
    private SeenGradesStore seenGradesStore;
    private SessionCookieStore sessionCookieStore;
    private QISClient qisClient;
    private GradeTable currentGradeTable;
    private Set<String> newModuleKeys = Collections.emptySet();

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
                } else if (currentGradeTable != null) {
                    // Einfache Zurück-Navigation: Die Zugangsdaten sind unverändert, daher wird
                    // der SSO-Login nicht erneut ausgeführt. Studiengang, Semester oder
                    // Ziel-ECTS könnten sich aber geändert haben, daher wird die Kopfzeile aus
                    // der bereits geladenen Notentabelle neu gezeichnet.
                    showGrades(currentGradeTable);
                }
            });

    private final ActivityResultLauncher<Intent> displayOptionsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (currentGradeTable != null) {
                    showGrades(currentGradeTable);
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
        qisClient = new QISClient(getApplicationContext());

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
            if (id == R.id.filterChipPassed) {
                currentFilter = GradeCardBuilder.Filter.PASSED;
            } else if (id == R.id.filterChipOpen) {
                currentFilter = GradeCardBuilder.Filter.OPEN;
            } else {
                currentFilter = GradeCardBuilder.Filter.ALL;
            }
            saveViewState();
            if (currentGradeTable != null) {
                showGrades(currentGradeTable);
            }
        });

        sortButton = findViewById(R.id.sortButton);
        sortButton.setOnClickListener(this::showSortMenu);
        updateSortButtonLabel();

        gradesSwipeRefresh = findViewById(R.id.gradesSwipeRefresh);
        gradesRecyclerView = findViewById(R.id.gradesRecyclerView);
        gradesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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
        GradeTable cached = gradeCacheStore.loadTable();
        if (cached != null) {
            loadGrades(cached);
        } else {
            showOnly(loadingContainer);
        }
        performFetch(credentials, false);
    }

    /** Zentrale Stelle für jeden Wechsel zu einer (neu geladenen oder gecachten) Notentabelle:
     *  ermittelt einmalig die frisch benoteten Module gegenüber dem zuletzt gesehenen Stand,
     *  bevor die Tabelle angezeigt wird. So läuft der Vergleich nur bei echten Datenwechseln,
     *  nicht bei jedem Neuzeichnen durch Filter/Sortierung. */
    private void loadGrades(GradeTable gradeTable) {
        newModuleKeys = seenGradesStore.newlyGradedModuleKeys(gradeTable);
        showGrades(gradeTable);
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
            if (currentGradeTable != null) {
                showGrades(currentGradeTable);
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
            case PASSED: return R.id.filterChipPassed;
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
            GradeTable cached = gradeCacheStore.loadTable();
            if (cached != null) {
                showGrades(cached);
            }
            return;
        }
        gradeCacheStore.recordAttempt();
        qisClient.fetchGrades(credentials.username, credentials.password, true, new QISClient.Callback() {
            @Override
            public void onSuccess(GradeTable gradeTable) {
                gradeCacheStore.saveTable(gradeTable);
                gradesSwipeRefresh.setRefreshing(false);
                loadGrades(gradeTable);
            }

            @Override
            public void onError(Exception error) {
                gradesSwipeRefresh.setRefreshing(false);
                GradeTable cached = gradeCacheStore.loadTable();
                if (cached != null) {
                    showGrades(cached);
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
            public void onSuccess(GradeTable gradeTable) {
                setLoginLoading(false);
                credentialStore.save(new Credentials(username, password));
                gradeCacheStore.saveTable(gradeTable);
                loadGrades(gradeTable);
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

    private void showGrades(GradeTable gradeTable) {
        currentGradeTable = gradeTable;
        GradeSettingsStore.Settings settings = gradeSettingsStore.load();

        // Befüllt Studiengang/degreeType nur, wenn der Nutzer noch keinen gesetzt hat. Eine
        // manuelle Änderung bleibt erhalten.
        if (settings.studiengang == null || settings.studiengang.trim().isEmpty()) {
            String label = detectedStudiengangLabel(gradeTable);
            if (label != null) {
                settings.studiengang = label;
                settings.degreeType = detectedDegreeType(gradeTable);
                gradeSettingsStore.save(settings);
            }
        }

        List<ModuleCardData> allCards = GradeCardBuilder.buildCards(gradeTable, settings, newModuleKeys);
        List<ModuleCardData> visibleCards = GradeCardBuilder.sorted(
                GradeCardBuilder.filtered(allCards, currentFilter), currentSort);

        Double average = GradeAnalysis.average(gradeTable, settings.averageMode);
        double totalEcts = GradeCardBuilder.totalEarnedEcts(allCards);

        kpiOverline.setText(overlineText(settings));
        kpiAverageValue.setText(average != null ? String.format(Locale.GERMANY, "%.2f", average) : "–");
        kpiEctsValue.setText(totalEcts == Math.floor(totalEcts)
                ? String.format(Locale.GERMANY, "%.0f", totalEcts)
                : String.format(Locale.GERMANY, "%.1f", totalEcts));
        double progress = Math.min(Math.max(totalEcts / settings.effectiveTargetEcts(), 0), 1);
        kpiEctsProgress.setProgress((int) Math.round(progress * 100));

        gradesRecyclerView.setAdapter(
                new ModuleCardAdapter(visibleCards, settings.visibleAttemptFields, settings.customColors));
        showOnly(gradesContainer);
    }

    private String overlineText(GradeSettingsStore.Settings settings) {
        List<String> parts = new ArrayList<>();
        if (settings.studiengang != null && !settings.studiengang.trim().isEmpty()) {
            parts.add(settings.studiengang.trim());
        }
        if (settings.semester > 0) {
            parts.add(settings.semester + ". Semester");
        }
        return parts.isEmpty() ? getString(R.string.grades_title) : String.join(" · ", parts);
    }

    private static final Map<String, String> DEGREE_ABBREVIATIONS = new HashMap<>();
    static {
        DEGREE_ABBREVIATIONS.put("Bachelor of Science", "B.Sc.");
        DEGREE_ABBREVIATIONS.put("Master of Science", "M.Sc.");
        DEGREE_ABBREVIATIONS.put("Bachelor of Arts", "B.A.");
        DEGREE_ABBREVIATIONS.put("Master of Arts", "M.A.");
        DEGREE_ABBREVIATIONS.put("Bachelor of Engineering", "B.Eng.");
        DEGREE_ABBREVIATIONS.put("Master of Engineering", "M.Eng.");
    }

    private String detectedDegreeAbbreviation(GradeTable gradeTable) {
        if (gradeTable.abschluss == null) {
            return null;
        }
        String normalized = gradeTable.abschluss.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String abbreviation = DEGREE_ABBREVIATIONS.get(normalized);
        return abbreviation != null ? abbreviation : normalized;
    }

    private String detectedFachName(GradeTable gradeTable) {
        if (gradeTable.fach == null) {
            return null;
        }
        String stripped = gradeTable.fach.replaceAll("\\s*\\(PO-Version\\s*\\d{4}\\)", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private String detectedStudiengangLabel(GradeTable gradeTable) {
        String abbreviation = detectedDegreeAbbreviation(gradeTable);
        String fach = detectedFachName(gradeTable);
        if (abbreviation == null && fach == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (abbreviation != null) {
            builder.append(abbreviation);
        }
        if (fach != null) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(fach);
        }
        return builder.toString();
    }

    private String detectedDegreeType(GradeTable gradeTable) {
        if (gradeTable.abschluss == null) {
            return "";
        }
        return gradeTable.abschluss.toLowerCase(Locale.GERMAN).contains("master") ? "master" : "bachelor";
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
