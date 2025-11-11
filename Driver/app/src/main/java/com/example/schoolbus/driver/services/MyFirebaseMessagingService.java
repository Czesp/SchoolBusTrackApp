package com.example.schoolbus.driver.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.schoolbus.driver.models.SafetyAlert;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
        // Send token to your server if needed
        sendRegistrationToServer(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            // Show notification using NotificationService
            showNotification(remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody());
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String alertType = data.get("alertType");
        String message = data.get("message");
        String busId = data.get("busId");
        String severity = data.get("severity");

        if (alertType != null && message != null) {
            // Create SafetyAlert from FCM data
            SafetyAlert alert = new SafetyAlert(alertType, message, busId);
            alert.setSeverity(severity != null ? severity : "MEDIUM");

            // Show notification
            NotificationService notificationService = new NotificationService(this);
            notificationService.showLocalNotification(alert);
        }
    }

    private void showNotification(String title, String body) {
        if (title == null) title = "Bus Alert";
        if (body == null) body = "New safety alert";

        SafetyAlert alert = new SafetyAlert("GENERAL", body, "unknown");
        NotificationService notificationService = new NotificationService(this);
        notificationService.showLocalNotification(alert);
    }

    private void sendRegistrationToServer(String token) {
        // TODO: Implement this method to send token to your app server
        Log.d(TAG, "FCM Token: " + token);
    }
}