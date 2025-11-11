package com.example.schoolbus.parent.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.schoolbus.parent.ParentMainActivity;
import com.example.schoolbus.parent.R;

public class ParentNotificationService {
    private static final String TAG = "ParentNotificationService";
    private static final String CHANNEL_ID = "parent_alerts_channel";
    private static final String CHANNEL_NAME = "Parent Alerts";

    private Context context;

    public ParentNotificationService(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    public void showSafetyAlertNotification(String alertType, String message, String busNumber, String severity) {
        Intent intent = new Intent(context, ParentMainActivity.class);
        intent.putExtra("show_alerts", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if ("HIGH".equals(severity)) {
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }

        String notificationTitle = getAlertTitle(alertType, busNumber);
        int priority = "HIGH".equals(severity) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bus_alert)
                        .setContentTitle(notificationTitle)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setSound(soundUri)
                        .setPriority(priority)
                        .setContentIntent(pendingIntent);

        // Add style for longer messages
        if (message.length() > 50) {
            NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
            bigTextStyle.bigText(message);
            bigTextStyle.setBigContentTitle(notificationTitle);
            notificationBuilder.setStyle(bigTextStyle);
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(generateNotificationId(), notificationBuilder.build());
            Log.d(TAG, "Safety alert notification shown: " + notificationTitle);
        }
    }

    public void showSimpleNotification(String title, String message) {
        Intent intent = new Intent(context, ParentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bus_alert)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(generateNotificationId(), notificationBuilder.build());
        }
    }

    private String getAlertTitle(String alertType, String busNumber) {
        String busInfo = busNumber != null ? "Bus " + busNumber : "Bus";

        switch (alertType) {
            case "SPEED_VIOLATION":
                return "🚨 Speed Alert - " + busInfo;
            case "ROUTE_DEVIATION":
                return "📍 Route Alert - " + busInfo;
            case "PROLONGED_STOP":
                return "⏰ Stop Alert - " + busInfo;
            default:
                return "🚌 Safety Alert - " + busInfo;
        }
    }

    private int generateNotificationId() {
        return (int) System.currentTimeMillis();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Safety alerts and bus notifications");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public void showArrivalPredictionNotification(String busNumber, String stop, String eta, String message) {
        Intent intent = new Intent(context, ParentMainActivity.class);
        intent.putExtra("show_bus", busNumber);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        String title = "🚌 Bus " + busNumber + " Arriving Soon";
        String notificationMessage = message != null ? message :
                "Bus " + busNumber + " will arrive at " + stop + " in " + eta + " minutes";

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bus_alert)
                        .setContentTitle(title)
                        .setContentText(notificationMessage)
                        .setAutoCancel(true)
                        .setSound(soundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.bigText(notificationMessage);
        bigTextStyle.setBigContentTitle(title);
        notificationBuilder.setStyle(bigTextStyle);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(generateNotificationId(), notificationBuilder.build());
            Log.d(TAG, "Arrival prediction notification shown for Bus " + busNumber);
        }
    }
}