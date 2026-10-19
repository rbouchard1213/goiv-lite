package com.kamron.pogoiv.pokeflycomponents;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.widget.RemoteViews;

import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.R;
import com.kamron.pogoiv.activities.MainActivity;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;

/**
 * Created by johan on 2017-07-06.
 * <p>
 * An object which can display a running or paused notification for goiv
 */

public class GoIVNotificationManager {

    private static final int NOTIFICATION_REQ_CODE = 8959;

    private static final String NOTIFICATION_CHANNEL_ID = "8959";

    private static Pokefly pokefly;


    public GoIVNotificationManager(Pokefly pokefly) {
        this.pokefly = pokefly;
    }


    /**
     * Show a paused notification in the system notification tray.
     */
    public void showPausedNotification() {
        // Prepare views
        RemoteViews contentView =
                new RemoteViews(pokefly.getPackageName(), R.layout.notification_pokefly_paused);
        RemoteViews contentBigView =
                new RemoteViews(pokefly.getPackageName(), R.layout.notification_pokefly_paused_expanded);

        // Open app action
        Intent openAppIntent = new Intent(pokefly, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                pokefly, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.root, openAppPendingIntent);
        contentBigView.setOnClickPendingIntent(R.id.root, openAppPendingIntent);

        // Open app action (settings screen was cut in goiv-lite)
        Intent startSettingAppIntent = new Intent(pokefly, MainActivity.class);
        PendingIntent startSettingsPendingIntent = PendingIntent.getActivity(
                pokefly, 0, startSettingAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.settings, startSettingsPendingIntent);
        contentBigView.setOnClickPendingIntent(R.id.settings, startSettingsPendingIntent);

        // Start pokefly action
        Intent startServiceIntent = new Intent(pokefly, MainActivity.class)
                .setAction(MainActivity.ACTION_START_POKEFLY);
        PendingIntent startServicePendingIntent = PendingIntent.getActivity(
                pokefly, 0, startServiceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.start, startServicePendingIntent);
        contentBigView.setOnClickPendingIntent(R.id.start, startServicePendingIntent);

        // Build notification
        NotificationCompat.Builder notification = new NotificationCompat.Builder(pokefly, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(pokefly.getString(R.string.notification_title_goiv_stopped))
                .setContentText(pokefly.getString(R.string.notification_title_tap_to_open))
                .setColor(ContextCompat.getColor(pokefly, R.color.colorAccent))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContent(contentView)
                .setCustomBigContentView(contentBigView)
                .setOngoing(false);

        NotificationManager notificationManager =
                (NotificationManager) pokefly.getSystemService(Context.NOTIFICATION_SERVICE);

        initNotificationChannel(notificationManager);

        notificationManager.notify(NOTIFICATION_REQ_CODE, notification.build());
    }

    /**
     * Show a running notification in the system notification tray.
     */
    public void showRunningNotification() {
        // Prepare views
        RemoteViews contentView =
                new RemoteViews(pokefly.getPackageName(), R.layout.notification_pokefly_started);
        RemoteViews contentBigView =
                new RemoteViews(pokefly.getPackageName(), R.layout.notification_pokefly_started_expanded);
        contentView.setTextViewText(R.id.notification_title,
                pokefly.getString(R.string.notification_title_short, pokefly.getTrainerLevel()));
        contentBigView.setTextViewText(R.id.notification_title,
                pokefly.getString(R.string.notification_title, pokefly.getTrainerLevel()));

        // Open app action
        Intent openAppIntent = new Intent(pokefly, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                pokefly, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.root, openAppPendingIntent);
        contentBigView.setOnClickPendingIntent(R.id.root, openAppPendingIntent);

        // Recalibration was cut in goiv-lite: hide its button
        contentView.setViewVisibility(R.id.recalibrate, android.view.View.GONE);
        contentBigView.setViewVisibility(R.id.recalibrate, android.view.View.GONE);

        // Stop service action
        Intent stopServiceIntent = Pokefly.createStopIntent(pokefly);
        PendingIntent stopServicePendingIntent = PendingIntent.getService(
                pokefly, 0, stopServiceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.pause, stopServicePendingIntent);
        contentBigView.setOnClickPendingIntent(R.id.pause, stopServicePendingIntent);

        // Build notification
        NotificationCompat.Builder notification = new NotificationCompat.Builder(pokefly, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon_play)
                .setContentTitle(pokefly.getString(R.string.notification_title, pokefly.getTrainerLevel()))
                .setContentText(pokefly.getString(R.string.notification_title_tap_to_open))
                .setColor(ContextCompat.getColor(pokefly, R.color.colorPrimary))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContent(contentView)
                .setCustomBigContentView(contentBigView)
                .setOngoing(true);

        NotificationManager notificationManager =
                (NotificationManager) pokefly.getSystemService(Context.NOTIFICATION_SERVICE);

        initNotificationChannel(notificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pokefly.startForeground(NOTIFICATION_REQ_CODE, notification.build(),
                    FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            pokefly.startForeground(NOTIFICATION_REQ_CODE, notification.build());
        }
    }

    /**
     * The class which create channel notification for oreo.
     */
    private void initNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager
                .getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            // Create notification channel
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    pokefly.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);

            channel.setShowBadge(false);
            channel.enableLights(false);

            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Receives notification actions. Recalibration was cut in goiv-lite, so this is a no-op
     * placeholder kept because the manifest declares the service.
     */
    public static class NotificationActionService extends IntentService {
        public NotificationActionService() {
            super(NotificationActionService.class.getSimpleName());
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            // No actions handled in goiv-lite v1.
        }
    }
}
