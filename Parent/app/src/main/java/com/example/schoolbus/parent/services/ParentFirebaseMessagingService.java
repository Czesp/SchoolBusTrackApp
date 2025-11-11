package com.example.schoolbus.parent.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class ParentFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "ParentFCMService";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
        sendRegistrationToServer(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleSafetyAlertMessage(remoteMessage.getData());
            handleArrivalPredictionMessage(remoteMessage.getData());
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            showNotification(remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody());
        }
    }

    private void handleSafetyAlertMessage(java.util.Map<String, String> data) {
        String alertType = data.get("alertType");
        String message = data.get("message");
        String busId = data.get("busId");
        String busNumber = data.get("busNumber");
        String severity = data.get("severity");

        if (alertType != null && message != null) {
            Log.d(TAG, "Processing safety alert: " + alertType + " for bus: " + busNumber);

            // Show notification using ParentNotificationService
            ParentNotificationService notificationService = new ParentNotificationService(this);
            notificationService.showSafetyAlertNotification(alertType, message, busNumber, severity);

            // Save to Firestore for history
            saveNotificationToHistory("SAFETY_ALERT", message, busNumber, busId);
        }
    }

    private void handleArrivalPredictionMessage(java.util.Map<String, String> data) {
        String type = data.get("type");
        String busNumber = data.get("busNumber");
        String eta = data.get("etaMinutes");
        String stop = data.get("nextStop");
        String message = data.get("message");

        if ("ARRIVAL_PREDICTION".equals(type) && busNumber != null && eta != null) {
            Log.d(TAG, "Processing arrival prediction: Bus " + busNumber + ", ETA: " + eta + "min");

            ParentNotificationService notificationService = new ParentNotificationService(this);
            notificationService.showArrivalPredictionNotification(busNumber, stop, eta, message);

            // Save to Firestore for history
            saveNotificationToHistory("ARRIVAL_PREDICTION", message, busNumber, null);
        }
    }

    private void saveNotificationToHistory(String type, String message, String busNumber, String busId) {
        try {
            // This would save the notification to Firestore for the notification history
            // You can implement this based on your Firestore structure
            Log.d(TAG, "Would save notification to history: " + type + " - " + message);
        } catch (Exception e) {
            Log.e(TAG, "Error saving notification to history: " + e.getMessage());
        }
    }

    private void showNotification(String title, String body) {
        ParentNotificationService notificationService = new ParentNotificationService(this);
        notificationService.showSimpleNotification(title != null ? title : "Bus Alert",
                body != null ? body : "New notification");
    }

    private void sendRegistrationToServer(String token) {
        Log.d(TAG, "FCM Token for parent: " + token);

        // Subscribe to relevant topics
        FirebaseMessaging.getInstance().subscribeToTopic("parent_alerts")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to parent_alerts topic");
                    } else {
                        Log.e(TAG, "Failed to subscribe to parent_alerts topic");
                    }
                });

        // Subscribe to general bus topics
        FirebaseMessaging.getInstance().subscribeToTopic("bus_updates")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to bus_updates topic");
                    } else {
                        Log.e(TAG, "Failed to subscribe to bus_updates topic");
                    }
                });
    }
}