package com.example.schoolbus.admin.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AdminFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "AdminFCMService";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

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

            // NEW: Save alert to Firestore first, then handle notification
            if (remoteMessage.getData().containsKey("alertType")) {
                saveSafetyAlertToFirestore(remoteMessage.getData());
            } else {
                handleSafetyAlertMessage(remoteMessage.getData());
                handleArrivalPredictionMessage(remoteMessage.getData());
            }
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            showNotification(remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody());
        }
    }

    // NEW: Save safety alert to Firestore so badge system can detect it
    private void saveSafetyAlertToFirestore(Map<String, String> data) {
        String alertType = data.get("alertType");
        String message = data.get("message");
        String busId = data.get("busId");
        String busNumber = data.get("busNumber");
        String severity = data.get("severity");
        String driverId = data.get("driverId");
        String driverName = data.get("driverName");

        if (alertType == null || message == null) {
            Log.e(TAG, "Missing required alert data");
            return;
        }

        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("message", message);
        alert.put("busId", busId != null ? busId : "");
        alert.put("busNumber", busNumber != null ? busNumber : "Unknown Bus");
        alert.put("severity", severity != null ? severity : "MEDIUM");
        alert.put("driverId", driverId != null ? driverId : "");
        alert.put("driverName", driverName != null ? driverName : "Unknown Driver");
        alert.put("resolved", false);
        alert.put("acknowledged", false); // NEW: Make sure this is false
        alert.put("timestamp", com.google.firebase.Timestamp.now());
        alert.put("createdAt", com.google.firebase.Timestamp.now());

        // Save to Firestore
        db.collection("safety_alerts")
                .add(alert)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Safety alert saved to Firestore: " + documentReference.getId());

                    // Now show notification
                    handleSafetyAlertMessage(data);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving safety alert to Firestore: " + e.getMessage());
                    // Still show notification even if Firestore save fails
                    handleSafetyAlertMessage(data);
                });
    }

    private void handleSafetyAlertMessage(java.util.Map<String, String> data) {
        String alertType = data.get("alertType");
        String message = data.get("message");
        String busId = data.get("busId");
        String busNumber = data.get("busNumber");
        String severity = data.get("severity");

        if (alertType != null && message != null) {
            // Show notification using AdminNotificationService
            AdminNotificationService notificationService = new AdminNotificationService(this);
            notificationService.showSafetyAlertNotification(alertType, message, busNumber, severity);

            // NEW: Trigger badge update
            triggerBadgeUpdate();
        }
    }

    // NEW: Trigger badge update by incrementing a counter or sending broadcast
    private void triggerBadgeUpdate() {
        // Option 1: Send broadcast to activity (if it's running)
        /*
        Intent intent = new Intent("UPDATE_ALERTS_BADGE");
        sendBroadcast(intent);
        */

        // Option 2: Simply log for now - the Firestore listener should pick it up
        Log.d(TAG, "Safety alert received - badge should update via Firestore listener");
    }

    private void showNotification(String title, String body) {
        AdminNotificationService notificationService = new AdminNotificationService(this);
        notificationService.showSimpleNotification(title != null ? title : "Admin Alert",
                body != null ? body : "New notification");
    }

    private void sendRegistrationToServer(String token) {
        Log.d(TAG, "FCM Token for admin: " + token);

        // Subscribe to admin topics
        FirebaseMessaging.getInstance().subscribeToTopic("admin_topic")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to admin_topic");
                    }
                });

        FirebaseMessaging.getInstance().subscribeToTopic("safety_alerts")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to safety_alerts topic");
                    }
                });
    }

    private void handleArrivalPredictionMessage(java.util.Map<String, String> data) {
        String type = data.get("type");
        String busNumber = data.get("busNumber");
        String eta = data.get("etaMinutes");
        String stop = data.get("nextStop");
        String message = data.get("message");

        if ("ARRIVAL_PREDICTION".equals(type) && busNumber != null && eta != null) {
            AdminNotificationService notificationService = new AdminNotificationService(this);
            notificationService.showArrivalPredictionNotification(busNumber, stop, eta, message);

            Log.d(TAG, "Arrival prediction received - Bus: " + busNumber + ", ETA: " + eta + "min");
        }
    }
}