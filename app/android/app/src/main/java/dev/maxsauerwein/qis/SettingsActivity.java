package dev.maxsauerwein.qis;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import dev.maxsauerwein.qis.model.Credentials;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.network.QISClient;
import dev.maxsauerwein.qis.notification.NotificationService;
import dev.maxsauerwein.qis.storage.CredentialStore;
import dev.maxsauerwein.qis.storage.GradeCacheStore;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.storage.SeenGradesStore;
import dev.maxsauerwein.qis.storage.SessionCookieStore;
import dev.maxsauerwein.qis.util.QISLabels;
import dev.maxsauerwein.qis.work.GradeRefreshScheduler;
import dev.maxsauerwein.qis.work.GradeRefreshWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public final class SettingsActivity extends AppCompatActivity {

    private CredentialStore credentialStore;
    private GradeSettingsStore gradeSettingsStore;
    private GradeCacheStore gradeCacheStore;
    private SessionCookieStore sessionCookieStore;
    private QISClient qisClient;

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputLayout abschlussLayout;
    private MaterialAutoCompleteTextView abschlussInput;
    private TextInputLayout fachLayout;
    private MaterialAutoCompleteTextView fachInput;
    private TextView degreeFooter;
    private TextInputEditText semesterInput;
    private TextInputEditText targetEctsInput;
    private TextView errorText;
    private GradeSettingsStore.Settings settings;
    private List<String> abschlussRawValues = new ArrayList<>();
    private List<String> fachRawValues = new ArrayList<>();
    private MaterialButton saveButton;
    private View progress;
    private MaterialSwitch notifyNewGradesSwitch;
    private TextView debugStatusText;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    enableNotifications();
                } else {
                    notifyNewGradesSwitch.setChecked(false);
                    Snackbar.make(notifyNewGradesSwitch, R.string.notifications_denied_message,
                            Snackbar.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        credentialStore = new CredentialStore(getApplicationContext());
        gradeSettingsStore = new GradeSettingsStore(getApplicationContext());
        gradeCacheStore = new GradeCacheStore(getApplicationContext());
        sessionCookieStore = new SessionCookieStore(getApplicationContext());
        qisClient = new QISClient(getApplicationContext());

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        usernameInput = findViewById(R.id.settingsUsernameInput);
        passwordInput = findViewById(R.id.settingsPasswordInput);
        abschlussLayout = findViewById(R.id.settingsAbschlussLayout);
        abschlussInput = findViewById(R.id.settingsAbschlussInput);
        fachLayout = findViewById(R.id.settingsFachLayout);
        fachInput = findViewById(R.id.settingsFachInput);
        degreeFooter = findViewById(R.id.settingsDegreeFooter);
        semesterInput = findViewById(R.id.settingsSemesterInput);
        targetEctsInput = findViewById(R.id.settingsTargetEctsInput);
        errorText = findViewById(R.id.settingsError);
        saveButton = findViewById(R.id.settingsSaveButton);
        progress = findViewById(R.id.settingsProgress);
        MaterialButton logoutButton = findViewById(R.id.settingsLogoutButton);

        Credentials existing = credentialStore.load();
        if (existing != null) {
            usernameInput.setText(existing.username);
        }

        settings = gradeSettingsStore.load();
        renderDegreeFields();
        if (settings.semester > 0) {
            semesterInput.setText(String.valueOf(settings.semester));
        }
        if (settings.targetEcts > 0) {
            targetEctsInput.setText(String.valueOf(settings.targetEcts));
        }

        saveButton.setOnClickListener(v -> save(existing));
        logoutButton.setOnClickListener(v -> {
            credentialStore.clear();
            gradeCacheStore.clear();
            sessionCookieStore.clear();
            setResult(RESULT_FIRST_USER);
            finish();
        });

        setupNotificationToggle();
        // Debug-Testbuttons vorübergehend deaktiviert (nicht gelöscht, siehe setupDebugButtons()
        // unten). Bei Bedarf Aufruf wieder einkommentieren.
        // setupDebugButtons();
    }

    private void setupNotificationToggle() {
        notifyNewGradesSwitch = findViewById(R.id.notifyNewGradesSwitch);
        notifyNewGradesSwitch.setChecked(NotificationService.isEnabled(getApplicationContext()));
        notifyNewGradesSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                requestNotificationsEnabled();
            } else {
                NotificationService.setEnabled(getApplicationContext(), false);
                GradeRefreshScheduler.cancel(getApplicationContext());
            }
        });
    }

    /** Fragt bei Bedarf die POST_NOTIFICATIONS-Laufzeitberechtigung an (ab API 33), analog zu
     *  NotificationService.requestAuthorization() auf iOS. Darunter genügt der Notification-Channel. */
    private void requestNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationService.hasSystemPermission(getApplicationContext())) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            enableNotifications();
        }
    }

    private void enableNotifications() {
        NotificationService.setEnabled(getApplicationContext(), true);
        GradeRefreshScheduler.schedule(getApplicationContext());
    }

    /** Nur im Debug-Build sichtbar (siehe activity_settings.xml, Default visibility="gone").
     *  Testet den Hintergrund-Abruf direkt statt über den fehleranfälligen Umweg, den echten
     *  WorkManager-Trigger abzuwarten -- entspricht den Debug-Buttons in SettingsView.swift. */
    private void setupDebugButtons() {
        MaterialButton testRefreshButton = findViewById(R.id.debugTestRefreshButton);
        MaterialButton resetBaselineButton = findViewById(R.id.debugResetBaselineButton);
        debugStatusText = findViewById(R.id.debugStatusText);

        if (!BuildConfig.DEBUG) {
            return;
        }
        testRefreshButton.setVisibility(View.VISIBLE);
        resetBaselineButton.setVisibility(View.VISIBLE);

        testRefreshButton.setOnClickListener(v -> {
            debugStatusText.setText("Läuft…");
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(GradeRefreshWorker.class).build();
            WorkManager workManager = WorkManager.getInstance(getApplicationContext());
            workManager.enqueue(request);
            workManager.getWorkInfoByIdLiveData(request.getId()).observe(this, info -> {
                if (info != null && info.getState().isFinished()) {
                    debugStatusText.setText("Fertig – Details in Logcat (Tag: GradeRefreshWorker)");
                }
            });
        });

        resetBaselineButton.setOnClickListener(v -> {
            new SeenGradesStore(getApplicationContext()).debugForceEmptyNotifiedBaseline();
            debugStatusText.setText("Baseline geleert – jetzt \"Hintergrund-Abruf jetzt testen\" tippen");
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        GradeSettingsStore.Settings current = gradeSettingsStore.load();
        current.semester = parseIntOrDefault(textOf(semesterInput), 0);
        current.targetEcts = parseIntOrDefault(textOf(targetEctsInput), 0);
        gradeSettingsStore.save(current);
    }

    /** Baut die Abschluss-/Studiengang-Felder aus settings.availableDegreeOptions neu auf: je ein
     *  "Exposed Dropdown Menu" sobald mehr als eine Option existiert, sonst ein deaktiviertes Feld
     *  mit dem bereits feststehenden Wert als reiner Anzeige-Text (Port von degreeTypeRow/fachRow
     *  in SettingsView.swift). */
    private void renderDegreeFields() {
        List<String> abschlussOptions = settings.distinctAbschlussOptions();
        abschlussRawValues = abschlussOptions;
        List<String> abschlussLabels = new ArrayList<>();
        for (String abschluss : abschlussOptions) {
            abschlussLabels.add(QISLabels.degreeFullName(abschluss));
        }
        boolean abschlussEditable = abschlussOptions.size() > 1;
        abschlussInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, abschlussLabels));
        abschlussInput.setText(settings.selectedAbschluss.isEmpty() ? "" : QISLabels.degreeFullName(settings.selectedAbschluss), false);
        abschlussInput.setEnabled(abschlussEditable);
        abschlussLayout.setEndIconVisible(abschlussEditable);
        abschlussLayout.setVisibility(settings.selectedAbschluss.isEmpty() && !abschlussEditable ? View.GONE : View.VISIBLE);
        abschlussInput.setOnItemClickListener((parent, view, position, id) -> selectAbschluss(abschlussRawValues.get(position)));

        renderFachField();

        boolean needsChoice = abschlussOptions.size() > 1
                || settings.fachOptions(settings.selectedAbschluss).size() > 1;
        degreeFooter.setVisibility(needsChoice ? View.VISIBLE : View.GONE);
    }

    private void renderFachField() {
        List<String> fachOptions = settings.fachOptions(settings.selectedAbschluss);
        fachRawValues = fachOptions;
        List<String> fachLabels = new ArrayList<>();
        for (String fach : fachOptions) {
            fachLabels.add(QISLabels.fachName(fach));
        }
        boolean fachEditable = fachOptions.size() > 1;
        fachInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fachLabels));
        fachInput.setText(settings.selectedFach.isEmpty() ? "" : QISLabels.fachName(settings.selectedFach), false);
        fachInput.setEnabled(fachEditable);
        fachLayout.setEndIconVisible(fachEditable);
        fachLayout.setVisibility(settings.selectedAbschluss.isEmpty()
                || (settings.selectedFach.isEmpty() && !fachEditable) ? View.GONE : View.VISIBLE);
        fachInput.setOnItemClickListener((parent, view, position, id) -> selectFach(fachRawValues.get(position)));
    }

    private void selectAbschluss(String abschluss) {
        settings.selectedAbschluss = abschluss;
        List<String> fachOptions = settings.fachOptions(abschluss);
        settings.selectedFach = fachOptions.size() == 1 ? fachOptions.get(0) : "";
        gradeSettingsStore.save(settings);
        renderDegreeFields();
    }

    private void selectFach(String fach) {
        settings.selectedFach = fach;
        gradeSettingsStore.save(settings);
        renderFachField();
    }

    private int parseIntOrDefault(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save(Credentials existing) {
        String username = textOf(usernameInput);
        String newPassword = textOf(passwordInput);
        String password = newPassword.isEmpty() && existing != null ? existing.password : newPassword;
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }
        setLoading(true);
        qisClient.fetchGrades(username, password, false, new QISClient.Callback() {
            @Override
            public void onSuccess(List<GradeTable> gradeTables) {
                setLoading(false);
                credentialStore.save(new Credentials(username, password));
                gradeCacheStore.saveTables(gradeTables);
                // Neu anmelden kann (z. B. bei einem Accountwechsel) andere Abschluss-/Fach-
                // Kombinationen zutage fördern -- availableDegreeOptions und die Auswahl müssen
                // daher wie beim Login neu abgeglichen werden, nicht nur die rohen Felder.
                GradeSettingsStore.loadApplyingAutoDetection(getApplicationContext(), gradeTables);
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                errorText.setText(error.getMessage());
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setLoading(boolean loading) {
        saveButton.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            errorText.setVisibility(View.GONE);
        }
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
