package dev.maxsauerwein.qis.notification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.maxsauerwein.qis.R;

/** Kapselt die lokale Benachrichtigung über neu benotete Module sowie den zugehörigen
 *  Einstellungs-Schalter. Nutzt ausschließlich lokale Notifications -- es ist kein Server
 *  beteiligt. Entspricht NotificationService.swift auf iOS. */
public final class NotificationService {

    private static final String CHANNEL_ID = "grade_notifications";
    private static final String PREFS_NAME = "qis_notifications";
    private static final String KEY_ENABLED = "enabled";

    private static final AtomicInteger notificationIdCounter = new AtomicInteger(1);

    private NotificationService() {
    }

    /** Muss einmalig beim App-Start aufgerufen werden (Channel-Erstellung ist ab API 26 Pflicht).
     *  Importance bewusst HIGH: Die Benachrichtigung soll auch bei geöffneter App als
     *  Heads-up-Banner erscheinen -- entspricht dem UNUserNotificationCenterDelegate.willPresent
     *  mit [.banner, .sound, .list] auf iOS, das dort denselben Zweck erfüllt. */
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notifications_section_title),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notifications_footer));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    /** Vom Nutzer gewählter Wunsch, über neue Noten benachrichtigt zu werden. */
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Ob die Systemberechtigung für Benachrichtigungen bereits erteilt ist. Ab API 33 eine echte
     *  Laufzeit-Permission (POST_NOTIFICATIONS), darunter genügt der Channel. */
    public static boolean hasSystemPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Stellt eine lokale Benachrichtigung über die frisch benoteten Module zu. */
    @SuppressLint("MissingPermission") // durch hasSystemPermission(context) oben abgesichert
    public static void notifyNewGrades(Context context, List<String> moduleNames) {
        if (moduleNames.isEmpty() || !hasSystemPermission(context)) {
            return;
        }

        String title;
        String body;
        if (moduleNames.size() == 1) {
            title = context.getString(R.string.notification_single_title);
            body = moduleNames.get(0);
        } else {
            StringBuilder preview = new StringBuilder();
            int previewCount = Math.min(3, moduleNames.size());
            for (int i = 0; i < previewCount; i++) {
                if (i > 0) {
                    preview.append(", ");
                }
                preview.append(moduleNames.get(i));
            }
            if (moduleNames.size() > 3) {
                preview.append(" …");
            }
            title = context.getString(R.string.notification_multiple_title);
            body = moduleNames.size() + " Module wurden benotet: " + preview;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(notificationIdCounter.getAndIncrement(), builder.build());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
