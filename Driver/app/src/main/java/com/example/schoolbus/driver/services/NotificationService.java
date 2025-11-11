package com.example.schoolbus.driver.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.schoolbus.driver.MainActivity;
import com.example.schoolbus.driver.R;
import com.example.schoolbus.driver.models.SafetyAlert;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class NotificationService {
    private static final String TAG = "NotificationService";
    private static final String CHANNEL_ID = "safety_alerts_channel";
    private static final String CHANNEL_NAME = "Safety Alerts";

    private Context context;
    private FirebaseFirestore db;

    public NotificationService(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        createNotificationChannel();
    }

    // Send notification to specific topics
    public void sendSafetyAlertNotification(SafetyAlert alert) {
        // Send to admin topic
        sendToTopic("admin_topic", alert);

        // Send to parents of this bus's route
        sendToTopic("route_" + alert.getRouteId() + "_parents", alert);

        // Send to specific bus topic
        sendToTopic("bus_" + alert.getBusId(), alert);

        // Also show local notification
        showLocalNotification(alert);
    }

    private void sendToTopic(String topic, SafetyAlert alert) {
        // This would typically be done from a cloud function
        // For now, we'll simulate by storing in Firestore
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("topic", topic);
        notificationData.put("alertType", alert.getAlertType());
        notificationData.put("message", alert.getMessage());
        notificationData.put("busId", alert.getBusId());
        notificationData.put("busNumber", alert.getBusNumber());
        notificationData.put("timestamp", alert.getTimestamp());
        notificationData.put("severity", alert.getSeverity());

        db.collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Notification queued for topic: " + topic);
                    saveToArrivalNotifications(alert);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error queueing notification", e);
                });
    }

    private void saveToArrivalNotifications(SafetyAlert alert) {
        Map<String, Object> arrivalNotification = new HashMap<>();

        arrivalNotification.put("type", "SAFETY_ALERT");
        arrivalNotification.put("message", alert.getMessage());
        arrivalNotification.put("busNumber", alert.getBusNumber());
        arrivalNotification.put("routeId", alert.getRouteId());
        arrivalNotification.put("busId", alert.getBusId());
        arrivalNotification.put("timestamp", System.currentTimeMillis());


        // Use unique ID to prevent duplicates
        String docId = "safety_" + alert.getBusId() + "_" + alert.getAlertType() + "_" + (System.currentTimeMillis() / 300000);

        db.collection("arrival_notifications")
                .document(docId)
                .set(arrivalNotification)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Safety alert saved to arrival_notifications");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving to arrival_notifications", e);
                });
    }

    public void showLocalNotification(SafetyAlert alert) {
        // Create unique notification ID based on alert content to prevent duplicates
        String uniqueContent = alert.getAlertType() + "_" + alert.getBusId() + "_" + alert.getMessage();
        int notificationId = Math.abs(uniqueContent.hashCode());

        // Check if we recently showed this notification (within 1 minute)
        long currentTime = System.currentTimeMillis();
        Long lastNotificationTime = getLastNotificationTime(notificationId);

        if (lastNotificationTime != null && (currentTime - lastNotificationTime) < 60000) {
            Log.d(TAG, "Notification " + notificationId + " recently shown, skipping");
            return;
        }

        // Save this notification time
        saveNotificationTime(notificationId, currentTime);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        String notificationTitle = getNotificationTitle(alert.getAlertType());

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bus_alert)
                        .setContentTitle(notificationTitle)
                        .setContentText(alert.getMessage())
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true) // Prevent sound/vibration on update
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.d(TAG, "Notification shown with ID: " + notificationId);
        }
    }

    // Helper methods to track notification times
    private void saveNotificationTime(int notificationId, long time) {
        SharedPreferences prefs = context.getSharedPreferences("notification_times", Context.MODE_PRIVATE);
        prefs.edit().putLong(String.valueOf(notificationId), time).apply();
    }

    private Long getLastNotificationTime(int notificationId) {
        SharedPreferences prefs = context.getSharedPreferences("notification_times", Context.MODE_PRIVATE);
        long time = prefs.getLong(String.valueOf(notificationId), 0);
        return time == 0 ? null : time;
    }

    private String getNotificationTitle(String alertType) {
        switch (alertType) {
            case "SPEED_VIOLATION":
                return "🚨 Speed Alert";
            case "ROUTE_DEVIATION":
                return "📍 Route Deviation";
            case "PROLONGED_STOP":
                return "⏰ Prolonged Stop";
            default:
                return "Bus Safety Alert";
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
            channel.setDescription("Safety alerts and notifications");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // Subscribe to relevant topics
    public void subscribeToTopics(String busId, String routeId) {
        // Subscribe to bus-specific topic
        FirebaseMessaging.getInstance().subscribeToTopic("bus_" + busId)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to bus topic: bus_" + busId);
                    }
                });

        // Subscribe to admin topic (for drivers)
        FirebaseMessaging.getInstance().subscribeToTopic("admin_topic")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to admin topic");
                    }
                });
    }
}