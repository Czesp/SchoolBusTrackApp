package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.schoolbus.admin.adapters.AdminNotificationHistoryAdapter;
import com.example.schoolbus.admin.models.NotificationHistory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminNotificationHistoryActivity extends AppCompatActivity {

    private static final String TAG = "AdminNotificationHistory";
    private RecyclerView recyclerNotifications;
    private MaterialCardView cardNoNotifications;
    private List<NotificationHistory> notificationsList = new ArrayList<>();
    private AdminNotificationHistoryAdapter adapter;
    private FirebaseFirestore db;
    private int loadedCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupToolbar();
        setupNotificationClickListener();
        loadNotificationHistory();
    }

    private void initializeViews() {
        recyclerNotifications = findViewById(R.id.recycler_notifications);
        cardNoNotifications = findViewById(R.id.card_no_notifications);

        adapter = new AdminNotificationHistoryAdapter(notificationsList);
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        recyclerNotifications.setAdapter(adapter);

        ImageButton btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupNotificationClickListener() {
        adapter.setOnNotificationClickListener(new AdminNotificationHistoryAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(NotificationHistory notification) {
                // Mark as read when user clicks the notification
                if (notification.getNotificationId() != null &&
                        (notification.getRead() == null || !notification.getRead())) {
                    markNotificationAsRead(notification.getNotificationId());

                    // Update local UI to show as read
                    int position = notificationsList.indexOf(notification);
                    if (position != -1) {
                        adapter.markNotificationAsRead(position);
                    }
                }

                // Optional: Show notification details
                showNotificationDetails(notification);
            }
        });
    }

    private void markNotificationAsRead(String notificationId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        updates.put("readAt", System.currentTimeMillis());

        db.collection("arrival_notifications").document(notificationId)
                .update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Notification marked as read: " + notificationId);
                    } else {
                        Log.e(TAG, "Failed to mark notification as read: " + notificationId);
                    }
                });
    }

    private void showNotificationDetails(NotificationHistory notification) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📬 Notification Details")
                .setMessage(notification.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void loadNotificationHistory() {
        db.collection("arrival_notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed for notifications", error);
                        showErrorMessage("Failed to load notifications: " + error.getMessage());
                        return;
                    }

                    notificationsList.clear();
                    loadedCount = 0;

                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot doc : value) {
                            try {
                                NotificationHistory notification = doc.toObject(NotificationHistory.class);
                                notification.setNotificationId(doc.getId());
                                notificationsList.add(notification);
                                loadedCount++;
                            } catch (RuntimeException e) {
                                Log.e(TAG, "Error converting document " + doc.getId(), e);
                                // Create basic notification from document data
                                createFallbackNotification(doc);
                            }
                        }
                        Log.d(TAG, "Successfully loaded " + loadedCount + " notifications");
                        updateUIWithNotifications();
                    } else {
                        Log.d(TAG, "No notifications found in database");
                        updateUIWithNoNotifications();
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showErrorMessage(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateUIWithNotifications() {
        runOnUiThread(() -> {
            recyclerNotifications.setVisibility(View.VISIBLE);
            cardNoNotifications.setVisibility(View.GONE);
        });
    }

    private void updateUIWithNoNotifications() {
        runOnUiThread(() -> {
            recyclerNotifications.setVisibility(View.GONE);
            cardNoNotifications.setVisibility(View.VISIBLE);
        });
    }

    private void createFallbackNotification(QueryDocumentSnapshot doc) {
        try {
            NotificationHistory fallback = new NotificationHistory();
            fallback.setNotificationId(doc.getId());

            // Set fields that should always exist
            if (doc.contains("message")) fallback.setMessage(doc.getString("message"));
            if (doc.contains("busNumber")) fallback.setBusNumber(doc.getString("busNumber"));
            if (doc.contains("type")) fallback.setType(doc.getString("type"));

            // Handle timestamp - it might be Timestamp or Long
            if (doc.contains("timestamp")) {
                Object timestamp = doc.get("timestamp");
                if (timestamp instanceof com.google.firebase.Timestamp) {
                    fallback.setTimestamp((com.google.firebase.Timestamp) timestamp);
                }
            }

            notificationsList.add(fallback);
            loadedCount++;
            Log.d(TAG, "✓ Created fallback notification: " + fallback.getMessage());

        } catch (Exception e) {
            Log.e(TAG, "Failed to create fallback notification", e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}