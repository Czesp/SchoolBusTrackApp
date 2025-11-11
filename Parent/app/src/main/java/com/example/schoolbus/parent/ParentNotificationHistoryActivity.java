package com.example.schoolbus.parent;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.schoolbus.parent.adapters.NotificationHistoryAdapter;
import com.example.schoolbus.parent.models.NotificationHistory;
import com.example.schoolbus.parent.models.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.*;

public class ParentNotificationHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ParentNotifHistory";
    private RecyclerView recyclerNotifications;
    private TextView tvNoNotifications;
    private MaterialToolbar toolbar;
    private List<NotificationHistory> notificationsList = new ArrayList<>();
    private NotificationHistoryAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private User currentParent;
    private String currentParentId;
    private ListenerRegistration notificationsListener;
    private ListenerRegistration arrivalListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        initializeViews();
        setupToolbar();
        setupNotificationClickListener();
        loadParentAndNotifications();
    }

    private void initializeViews() {
        recyclerNotifications = findViewById(R.id.recycler_notifications);
        tvNoNotifications = findViewById(R.id.tv_no_notifications);
        toolbar = findViewById(R.id.toolbar);
        adapter = new NotificationHistoryAdapter(notificationsList);
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        recyclerNotifications.setAdapter(adapter);

        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupNotificationClickListener() {
        adapter.setOnNotificationClickListener(notification -> {
            if (notification.getNotificationId() != null && (notification.getRead() == null || !notification.getRead())) {
                markNotificationAsRead(notification.getNotificationId());
                int position = notificationsList.indexOf(notification);
                if (position != -1) {
                    adapter.markNotificationAsRead(position);
                }
            }
            showNotificationDetails(notification);
        });
    }

    private void markNotificationAsRead(String notificationId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        updates.put("readAt", System.currentTimeMillis());

        db.collection("notifications").document(notificationId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Marked read in notifications"))
                .addOnFailureListener(e -> {
                    db.collection("arrival_notifications").document(notificationId)
                            .update(updates)
                            .addOnSuccessListener(a -> Log.d(TAG, "Marked read in arrival_notifications"))
                            .addOnFailureListener(ex -> Log.e(TAG, "Failed to mark read", ex));
                });
    }

    private void showNotificationDetails(NotificationHistory notification) {
        StringBuilder details = new StringBuilder();
        details.append("Message: ").append(notification.getMessage()).append("\n\n");
        if (notification.getBusNumber() != null) {
            details.append("Bus: ").append(notification.getBusNumber()).append("\n");
        }
        if (notification.getType() != null) {
            details.append("Type: ").append(getNotificationTypeDisplay(notification.getType())).append("\n");
        }
        if (notification.getNextStop() != null) {
            details.append("Next Stop: ").append(notification.getNextStop()).append("\n");
        }
        if (notification.getEtaMinutes() != null && notification.getEtaMinutes() > 0) {
            details.append("ETA: ").append(notification.getEtaMinutes()).append(" minutes\n");
        }
        if (notification.getTimestamp() != null) {
            try {
                Date date = notification.getTimestampAsDate();
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                details.append("Time: ").append(sdf.format(date));
            } catch (Exception e) {
                details.append("Time: Unknown");
            }
        }
        details.append("\nStatus: ").append(notification.getRead() != null && notification.getRead() ? "Read" : "Unread");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Notification Details")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Mark as Unread", (d, w) -> markNotificationAsUnread(notification.getNotificationId()))
                .show();
    }

    private String getNotificationTypeDisplay(String type) {
        switch (type) {
            case "ARRIVAL_PREDICTION": return "Arrival Prediction";
            case "SAFETY_ALERT": return "Safety Alert";
            case "ROUTE_UPDATE": return "Route Update";
            case "ROUTE_DEVIATION": return "Route Deviation";
            case "SPEED_VIOLATION": return "Speed Alert";
            case "PROLONGED_STOP": return "Prolonged Stop";
            default: return "Notification";
        }
    }

    private void markNotificationAsUnread(String notificationId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", false);
        updates.put("readAt", FieldValue.delete());

        db.collection("notifications").document(notificationId).update(updates)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Marked as unread", Toast.LENGTH_SHORT).show();
                    loadAllNotifications();
                })
                .addOnFailureListener(e -> {
                    db.collection("arrival_notifications").document(notificationId).update(updates)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Marked as unread", Toast.LENGTH_SHORT).show();
                                loadAllNotifications();
                            });
                });
    }

    private void loadParentAndNotifications() {
        currentParentId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentParentId == null) {
            showError("User not logged in");
            return;
        }

        db.collection("users").document(currentParentId).get()
                .addOnSuccessListener(snapshot -> {
                    currentParent = snapshot.toObject(User.class);
                    if (currentParent != null && currentParent.getBusId() != null) {
                        loadAllNotifications();
                    } else {
                        showError("No bus assigned");
                    }
                })
                .addOnFailureListener(e -> showError("Failed to load profile"));
    }

    private void loadAllNotifications() {
        if (currentParent == null || currentParent.getBusId() == null) return;

        String busId = currentParent.getBusId();
        Log.d(TAG, "Loading notifications for busId: " + busId);

        // Clear old listeners
        if (notificationsListener != null) notificationsListener.remove();
        if (arrivalListener != null) arrivalListener.remove();

        // Use a Map to collect ALL unique notifications from both collections
        Map<String, NotificationHistory> allNotificationsMap = new HashMap<>();

        // TEMPORARY: Remove orderBy while indexes are building
        // Listen to regular notifications collection
        notificationsListener = db.collection("notifications")
                .whereEqualTo("busId", busId)
                // .orderBy("timestamp", Query.Direction.DESCENDING) // REMOVED temporarily
                .limit(100)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Notifications listener error", e);
                        showError("Error loading notifications");
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        Log.d(TAG, "Received " + snapshot.size() + " notifications from 'notifications' collection");

                        for (QueryDocumentSnapshot doc : snapshot) {
                            debugNotificationData(doc, "notifications");
                            NotificationHistory notif = createNotificationFromDoc(doc);
                            if (notif.getNotificationId() != null) {
                                allNotificationsMap.put(notif.getNotificationId(), notif);
                            }
                        }

                        updateNotificationsList(allNotificationsMap);
                    } else {
                        Log.d(TAG, "No notifications found in 'notifications' collection");
                    }
                });

        // Listen to arrival_notifications collection
        arrivalListener = db.collection("arrival_notifications")
                .whereEqualTo("busId", busId)
                // .orderBy("timestamp", Query.Direction.DESCENDING) // REMOVED temporarily
                .limit(100)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Arrival notifications listener error", e);
                        showError("Error loading arrival notifications");
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        Log.d(TAG, "Received " + snapshot.size() + " notifications from 'arrival_notifications' collection");

                        for (QueryDocumentSnapshot doc : snapshot) {
                            debugNotificationData(doc, "arrival_notifications");
                            NotificationHistory notif = createNotificationFromDoc(doc);
                            if (notif.getNotificationId() != null) {
                                allNotificationsMap.put(notif.getNotificationId(), notif);
                            }
                        }

                        updateNotificationsList(allNotificationsMap);
                    } else {
                        Log.d(TAG, "No notifications found in 'arrival_notifications' collection");
                    }
                });

        // Also try a one-time query to check if we're getting all data
        loadNotificationsWithSingleQuery();
    }

    private void loadNotificationsWithSingleQuery() {
        String busId = currentParent.getBusId();

        // One-time query to debug and ensure we get all data
        Task<QuerySnapshot> notificationsTask = db.collection("notifications")
                .whereEqualTo("busId", busId)
                // .orderBy("timestamp", Query.Direction.DESCENDING) // REMOVED temporarily
                .limit(100)
                .get();

        Task<QuerySnapshot> arrivalTask = db.collection("arrival_notifications")
                .whereEqualTo("busId", busId)
                // .orderBy("timestamp", Query.Direction.DESCENDING) // REMOVED temporarily
                .limit(100)
                .get();

        Tasks.whenAllComplete(notificationsTask, arrivalTask)
                .addOnCompleteListener(task -> {
                    int totalCount = 0;
                    if (notificationsTask.isSuccessful()) {
                        totalCount += notificationsTask.getResult().size();
                        Log.d(TAG, "One-time query - Notifications: " + notificationsTask.getResult().size());
                    }
                    if (arrivalTask.isSuccessful()) {
                        totalCount += arrivalTask.getResult().size();
                        Log.d(TAG, "One-time query - Arrival Notifications: " + arrivalTask.getResult().size());
                    }
                    Log.d(TAG, "One-time query TOTAL: " + totalCount + " notifications");

                    if (totalCount == 0) {
                        Log.d(TAG, "No notifications found - this might be normal if no notifications exist yet");
                    }
                });
    }

    private void debugNotificationData(QueryDocumentSnapshot doc, String collection) {
        Log.d(TAG, "=== DEBUG NOTIFICATION FROM " + collection + " ===");
        Log.d(TAG, "Document ID: " + doc.getId());
        Log.d(TAG, "Data: " + doc.getData());
        Log.d(TAG, "Timestamp: " + doc.get("timestamp"));
        Log.d(TAG, "BusId: " + doc.get("busId"));
        Log.d(TAG, "=== END DEBUG ===");
    }

    private void updateNotificationsList(Map<String, NotificationHistory> notificationsMap) {
        // Convert map to list
        notificationsList.clear();
        notificationsList.addAll(notificationsMap.values());

        // Manual sorting since we removed orderBy from query
        Collections.sort(notificationsList, (a, b) -> {
            Date d1 = a.getTimestampAsDate();
            Date d2 = b.getTimestampAsDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1); // Descending order (newest first)
        });

        Log.d(TAG, "Total unique notifications: " + notificationsList.size());

        runOnUiThread(() -> {
            if (notificationsList.isEmpty()) {
                updateUIWithNoNotifications();
            } else {
                recyclerNotifications.setVisibility(View.VISIBLE);
                tvNoNotifications.setVisibility(View.GONE);
                View cardEmpty = findViewById(R.id.card_empty_state);
                if (cardEmpty != null) cardEmpty.setVisibility(View.GONE);
                adapter.updateNotifications(notificationsList);
                Log.d(TAG, "Displaying " + notificationsList.size() + " notifications");
            }
        });
    }

    private NotificationHistory createNotificationFromDoc(QueryDocumentSnapshot doc) {
        NotificationHistory notif = new NotificationHistory();
        notif.setNotificationId(doc.getId());

        // Handle both 'type' and 'alertType'
        String type = doc.getString("type");
        if (type == null) type = doc.getString("alertType");
        notif.setType(type != null ? type : "GENERAL");

        notif.setMessage(doc.getString("message") != null ? doc.getString("message") : "No message");
        notif.setBusNumber(doc.getString("busNumber"));
        notif.setNextStop(doc.getString("nextStop"));

        Object etaObj = doc.get("etaMinutes");
        if (etaObj instanceof Long) notif.setEtaMinutes((Long) etaObj);
        else if (etaObj instanceof Integer) notif.setEtaMinutes(((Integer) etaObj).longValue());

        // IMPROVED TIMESTAMP HANDLING
        Object ts = doc.get("timestamp");
        if (ts instanceof Timestamp) {
            notif.setTimestamp((Timestamp) ts);
        } else if (ts instanceof Long) {
            // If it's a Long, it's likely milliseconds since epoch
            long millis = (Long) ts;
            notif.setTimestamp(new Timestamp(millis / 1000, (int) ((millis % 1000) * 1000000)));
        } else if (ts instanceof Integer) {
            int millis = (Integer) ts;
            notif.setTimestamp(new Timestamp(millis / 1000, (int) ((millis % 1000) * 1000000)));
        } else {
            // Fallback to current time
            notif.setTimestamp(Timestamp.now());
        }

        Object readObj = doc.get("read");
        if (readObj instanceof Boolean) notif.setRead((Boolean) readObj);
        else notif.setRead(false); // Default to unread

        return notif;
    }

    private void updateUIWithNoNotifications() {
        recyclerNotifications.setVisibility(View.GONE);
        tvNoNotifications.setVisibility(View.VISIBLE);
        tvNoNotifications.setText("No notifications yet");
        View cardEmpty = findViewById(R.id.card_empty_state);
        if (cardEmpty != null) cardEmpty.setVisibility(View.VISIBLE);
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            updateUIWithNoNotifications();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsListener != null) notificationsListener.remove();
        if (arrivalListener != null) arrivalListener.remove();
    }

    @SuppressLint({"MissingSuperCall", "GestureBackNavigation"})
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}