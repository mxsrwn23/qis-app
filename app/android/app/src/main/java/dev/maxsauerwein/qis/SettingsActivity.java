package dev.maxsauerwein.qis;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import dev.maxsauerwein.qis.model.Credentials;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.network.QISClient;
import dev.maxsauerwein.qis.notification.NotificationService;
import dev.maxsauerwein.qis.storage.CredentialStore;
import dev.maxsauerwein.qis.storage.GradeCacheStore;
import dev.maxsauerwein.qis.storage.GradeSettingsStore;
import dev.maxsauerwein.qis.storage.SeenGradesStore;
import dev.maxsauerwein.qis.storage.SessionCookieStore;
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
    private TextInputEditText studiengangInput;
    private TextInputEditText semesterInput;
    private TextInputEditText targetEctsInput;
    private TextView errorText;
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
        studiengangInput = findViewById(R.id.settingsStudiengangInput);
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

        GradeSettingsStore.Settings settings = gradeSettingsStore.load();
        studiengangInput.setText(settings.studiengang);
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
        GradeSettingsStore.Settings settings = gradeSettingsStore.load();
        settings.studiengang = textOf(studiengangInput);
        settings.semester = parseIntOrDefault(textOf(semesterInput), 0);
        settings.targetEcts = parseIntOrDefault(textOf(targetEctsInput), 0);
        gradeSettingsStore.save(settings);
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
            public void onSuccess(GradeTable gradeTable) {
                setLoading(false);
                credentialStore.save(new Credentials(username, password));
                gradeCacheStore.saveTable(gradeTable);
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
