package dev.maxsauerwein.qis.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.maxsauerwein.qis.model.Credentials;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.network.QISClient;
import dev.maxsauerwein.qis.notification.NotificationService;
import dev.maxsauerwein.qis.storage.CredentialStore;
import dev.maxsauerwein.qis.storage.GradeCacheStore;
import dev.maxsauerwein.qis.storage.SeenGradesStore;

/** Periodischer Hintergrund-Abruf des Notenspiegels via WorkManager. Entspricht
 *  BackgroundGradeRefresher.swift auf iOS: lädt die gespeicherte Session/Credentials, ruft
 *  QISClient auf und benachrichtigt bei neu benoteten Modulen. */
public final class GradeRefreshWorker extends Worker {

    private static final String TAG = "GradeRefreshWorker";

    public GradeRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.i(TAG, "performRefresh gestartet");

        if (!NotificationService.isEnabled(context)) {
            Log.i(TAG, "Abbruch: Benachrichtigungen sind deaktiviert");
            return Result.success();
        }

        Credentials credentials = new CredentialStore(context).load();
        if (credentials == null) {
            Log.i(TAG, "Abbruch: keine Credentials gespeichert");
            return Result.success();
        }

        try {
            QISClient client = new QISClient(context);
            GradeTable table = client.fetchGradesBlocking(credentials.username, credentials.password, true);
            new GradeCacheStore(context).saveTable(table);

            SeenGradesStore seenGradesStore = new SeenGradesStore(context);
            Set<String> newModules = seenGradesStore.newlyGradedForNotification(table);
            Log.i(TAG, "Abruf erfolgreich, " + newModules.size() + " neu benotete Module");
            if (!newModules.isEmpty()) {
                List<String> sorted = new ArrayList<>(newModules);
                java.util.Collections.sort(sorted);
                NotificationService.notifyNewGrades(context, sorted);
            }
            return Result.success();
        } catch (Exception e) {
            // Hintergrundfehler still ignorieren; beim nächsten periodischen Lauf wird es erneut
            // versucht -- kein Retry-Backoff nötig, WorkManager plant den nächsten Durchlauf ohnehin.
            Log.w(TAG, "Abbruch durch Fehler: " + e.getMessage());
            return Result.success();
        }
    }
}
