package dev.maxsauerwein.qis.work;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import dev.maxsauerwein.qis.BuildConfig;
import dev.maxsauerwein.qis.notification.NotificationService;

/** Plant/storniert den periodischen Hintergrund-Abruf. Entspricht BackgroundGradeRefresher.swifts
 *  schedule()/cancel() auf iOS -- mit einem wichtigen Unterschied: WorkManager verwaltet die
 *  Periodizität selbst (inkl. Neustart nach Reboot), ein manuelles "beim nächsten Lauf neu
 *  einplanen" wie bei BGTaskScheduler ist hier nicht nötig. ExistingPeriodicWorkPolicy.KEEP sorgt
 *  dafür, dass wiederholte schedule()-Aufrufe (z. B. bei jedem App-Start) die bereits laufende
 *  Periode nicht immer wieder auf "jetzt + Intervall" zurücksetzen. */
public final class GradeRefreshScheduler {

    private static final String WORK_NAME = "grade_refresh_work";

    // WorkManager erzwingt ein Mindestintervall von 15 Minuten fuer periodische Arbeit; im
    // Debug-Build nutzen wir genau dieses Minimum, um beim Testen nicht Stunden warten zu muessen.
    private static final long PROD_INTERVAL_MINUTES = 4 * 60;
    private static final long DEBUG_INTERVAL_MINUTES = 15;

    private GradeRefreshScheduler() {
    }

    public static void scheduleIfEnabled(Context context) {
        if (NotificationService.isEnabled(context)) {
            schedule(context);
        }
    }

    public static void schedule(Context context) {
        long intervalMinutes = BuildConfig.DEBUG ? DEBUG_INTERVAL_MINUTES : PROD_INTERVAL_MINUTES;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                GradeRefreshWorker.class, intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
