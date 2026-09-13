package dev.maxsauerwein.qis;

import android.app.Application;

import dev.maxsauerwein.qis.notification.NotificationService;
import dev.maxsauerwein.qis.work.GradeRefreshScheduler;

public final class QISApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationService.createChannel(this);
        // Sichert bereits eingeplante Arbeit erneut ab (z. B. nach App-Update); dank
        // ExistingPeriodicWorkPolicy.KEEP in GradeRefreshScheduler kein Reset einer laufenden
        // Periode.
        GradeRefreshScheduler.scheduleIfEnabled(this);
    }
}
