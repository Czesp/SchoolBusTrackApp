package com.example.schoolbus.admin;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.schoolbus.admin.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdminDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private TextView tvWelcome, tvDateTime, tvTotalBuses, tvActiveBuses, tvTotalDrivers, tvActiveDrivers, tvTotalParents, tvTotalStudents;
    private TextView tvAdminEmail, notificationBadge;
    private CardView cardManageRoutes, cardManageBuses, cardManageUsers, cardLiveTracking;
    private RelativeLayout notificationLayout;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private Map<String, Long> lastNotificationTime = new HashMap<>();
    private static final long DEBOUNCE_DELAY = 30000; // 30 seconds
    private View btnNotifications;

    private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupToolbar();
        setupNavigationDrawer();
        setupClickListeners();
        loadAdminInfo();
        loadStatistics();
        loadArrivalNotifications();
        updateDateTime();
        updateGreeting();
        setSupportActionBar(toolbar);
//        setTitle("Admin Portal");

    }

    private void initializeViews() {
        // Toolbar and Navigation
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        notificationLayout = findViewById(R.id.notificationLayout);
        notificationBadge = findViewById(R.id.notificationBadge);
        btnNotifications = findViewById(R.id.btnNotifications);

        // Welcome and DateTime
        tvWelcome = findViewById(R.id.tvWelcome);
        tvDateTime = findViewById(R.id.tvDateTime);

        // Statistics TextViews
        tvTotalBuses = findViewById(R.id.tvTotalBuses);
        tvActiveBuses = findViewById(R.id.tvActiveBuses);
        tvTotalDrivers = findViewById(R.id.tvTotalDrivers);
        tvActiveDrivers = findViewById(R.id.tvActiveDrivers);
        tvTotalParents = findViewById(R.id.tvTotalParents);
        tvTotalStudents = findViewById(R.id.tvTotalStudents);

        // Quick Action Cards (now CardView)
        cardManageRoutes = findViewById(R.id.cardManageRoutes);
        cardManageBuses = findViewById(R.id.cardManageBuses);
        cardManageUsers = findViewById(R.id.cardManageUsers);
        cardLiveTracking = findViewById(R.id.cardLiveTracking);

        // Navigation Header Views
        View headerView = navigationView.getHeaderView(0);
        tvAdminEmail = headerView.findViewById(R.id.tvAdminEmail);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Set navigation icon click listener
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_dashboard);
    }

    private void updateDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        tvDateTime.setText(currentDate);
    }

    private boolean shouldProcessNotification(String busNumber, String notificationId) {
        long currentTime = System.currentTimeMillis();
        String key = busNumber + "_" + notificationId;

        if (lastNotificationTime.containsKey(key)) {
            long lastTime = lastNotificationTime.get(key);
            if (currentTime - lastTime < DEBOUNCE_DELAY) {
                Log.d(TAG, "Debouncing notification: " + key + " - too soon");
                return false;
            }
        }

        lastNotificationTime.put(key, currentTime);
        return true;
    }

    private void setupClickListeners() {
        // Notification bell click
        notificationLayout.setOnClickListener(v -> openNotificationHistory());
        btnNotifications.setOnClickListener(v -> openNotificationHistory());

        // Quick action cards with ripple effects
        cardManageRoutes.setOnClickListener(v -> {
            animateCardClick(v);
            startActivity(new Intent(this, RouteListActivity.class));
        });

        cardManageBuses.setOnClickListener(v -> {
            animateCardClick(v);
            startActivity(new Intent(this, BusListActivity.class));
        });

        cardManageUsers.setOnClickListener(v -> {
            animateCardClick(v);
            startActivity(new Intent(this, UserListActivity.class));
        });

        cardLiveTracking.setOnClickListener(v -> {
            animateCardClick(v);
            startActivity(new Intent(this, AdminLiveTrackingActivity.class));
        });
    }

    private void animateCardClick(View view) {
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void loadAdminInfo() {
        String currentUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        String email = document.getString("email");

                        String welcomeText = "Welcome, " + (name != null ? name : "Admin");
                        tvWelcome.setText(welcomeText);

                        if (email != null) {
                            tvAdminEmail.setText(email);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading admin info: " + e.getMessage());
                    tvWelcome.setText("Welcome, Admin");
                });
    }

    private void loadStatistics() {
        loadBusesStatistics();
        loadDriversStatistics();
        loadParentsStatistics();
        loadStudentsStatistics();
    }

    private void loadBusesStatistics() {
        db.collection("buses")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int totalBuses = task.getResult().size();
                        int activeBuses = 0;

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Boolean isActive = document.getBoolean("isActive");
                            if (isActive != null && isActive) {
                                activeBuses++;
                            }
                        }

                        int finalActiveBuses = activeBuses;
                        runOnUiThread(() -> {
                            tvTotalBuses.setText(String.valueOf(totalBuses));
                            tvActiveBuses.setText(String.valueOf(finalActiveBuses));
                        });

                        Log.d(TAG, "Buses - Total: " + totalBuses + ", Active: " + activeBuses);
                    } else {
                        runOnUiThread(() -> {
                            tvTotalBuses.setText("0");
                            tvActiveBuses.setText("0");
                        });
                        Log.e(TAG, "Error loading buses: " + task.getException());
                    }
                });
    }

    private void loadDriversStatistics() {
        // Load total drivers
        db.collection("users")
                .whereEqualTo("role", "driver")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int totalDrivers = task.getResult().size();
                        runOnUiThread(() -> tvTotalDrivers.setText(String.valueOf(totalDrivers)));
                        Log.d(TAG, "Total drivers: " + totalDrivers);

                        // Load active drivers
                        loadActiveDrivers();
                    } else {
                        runOnUiThread(() -> tvTotalDrivers.setText("0"));
                        Log.e(TAG, "Error loading total drivers: " + task.getException());
                    }
                });
    }

    private void loadActiveDrivers() {
        // Count drivers with isOnline field
        db.collection("users")
                .whereEqualTo("role", "driver")
                .whereEqualTo("isOnline", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int activeDrivers = task.getResult().size();
                        runOnUiThread(() -> tvActiveDrivers.setText(String.valueOf(activeDrivers)));
                        Log.d(TAG, "Active drivers (online): " + activeDrivers);
                    } else {
                        // Fallback: Count unique drivers assigned to active buses
                        loadActiveDriversFallback();
                    }
                });
    }

    private void loadActiveDriversFallback() {
        db.collection("buses")
                .whereEqualTo("isActive", true)
                .whereNotEqualTo("driverId", "")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Set<String> activeDriverIds = new HashSet<>();

                        for (QueryDocumentSnapshot busDoc : task.getResult()) {
                            String driverId = busDoc.getString("driverId");
                            if (driverId != null && !driverId.isEmpty()) {
                                activeDriverIds.add(driverId);
                            }
                        }

                        int activeDriversCount = activeDriverIds.size();
                        runOnUiThread(() -> tvActiveDrivers.setText(String.valueOf(activeDriversCount)));
                        Log.d(TAG, "Active drivers (bus assignment): " + activeDriversCount);
                    } else {
                        runOnUiThread(() -> tvActiveDrivers.setText("0"));
                        Log.e(TAG, "Error loading active drivers: " + task.getException());
                    }
                });
    }

    private void loadParentsStatistics() {
        db.collection("users")
                .whereEqualTo("role", "parent")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int parentCount = task.getResult().size();
                        runOnUiThread(() -> tvTotalParents.setText(String.valueOf(parentCount)));
                    } else {
                        runOnUiThread(() -> tvTotalParents.setText("0"));
                    }
                });
    }

    private void updateGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;

        if (hour < 12) {
            greeting = "Good Morning!";
        } else if (hour < 17) {
            greeting = "Good Afternoon!";
        } else {
            greeting = "Good Evening!";
        }

        runOnUiThread(() -> {
            TextView tvGreeting = findViewById(R.id.tvGreeting); // ← We'll add this ID
            if (tvGreeting != null) {
                tvGreeting.setText(greeting);
            }
        });
    }
    private void loadStudentsStatistics() {
        db.collection("students")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int studentCount = task.getResult().size();
                        runOnUiThread(() -> tvTotalStudents.setText(String.valueOf(studentCount)));
                    } else {
                        runOnUiThread(() -> tvTotalStudents.setText("0"));
                    }
                });
    }

    private void openNotificationHistory() {
        Intent intent = new Intent(AdminDashboardActivity.this, AdminNotificationHistoryActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.nav_dashboard) {
            // Already on dashboard, just close drawer
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (itemId == R.id.nav_profile) {
            Intent intent = new Intent(this, AdminProfileActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_notifications) {
            Intent intent = new Intent(this, AdminNotificationHistoryActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_routes) {
            Intent intent = new Intent(this, RouteListActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_buses) {
            Intent intent = new Intent(this, BusListActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_users) {
            Intent intent = new Intent(this, UserListActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_tracking) {
            Intent intent = new Intent(this, AdminLiveTrackingActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_logout) {
            logout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout() {
        auth.signOut();
        startActivity(new Intent(this, AdminLoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh statistics when returning to dashboard
        loadStatistics();
        updateNotificationBadge();
        updateDateTime();
        updateGreeting();

        // Ensure dashboard is selected in navigation
        navigationView.setCheckedItem(R.id.nav_dashboard);
    }

    private void updateNotificationBadge() {
        db.collection("arrival_notifications")
                .whereEqualTo("read", false)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int unreadCount = task.getResult().size();
                        runOnUiThread(() -> {
                            if (unreadCount > 0) {
                                notificationBadge.setText(String.valueOf(unreadCount));
                                notificationBadge.setVisibility(View.VISIBLE);
                            } else {
                                notificationBadge.setVisibility(View.GONE);
                            }
                        });
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadArrivalNotifications() {
        db.collection("arrival_notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed for arrival notifications", error);
                        return;
                    }

                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot doc : value) {
                            String busNumber = doc.getString("busNumber");
                            Long etaMinutes = doc.getLong("etaMinutes");
                            String message = doc.getString("message");
                            Boolean read = doc.getBoolean("read");
                            Boolean notificationSent = doc.getBoolean("notificationSent");
                            String notificationId = doc.getId();

                            if (busNumber != null && etaMinutes != null) {
                                Log.d(TAG, "Arrival notification received: " + message);

                                if ((read == null || !read) &&
                                        (notificationSent == null || !notificationSent) &&
                                        shouldProcessNotification(busNumber, notificationId)) {

                                    markNotificationAsSent(notificationId);
                                    updateNotificationBadge();

                                    Log.d(TAG, "Notification processed for badge update - Bus: " + busNumber);
                                }
                            }
                        }
                    }
                });
    }

    private void markNotificationAsSent(String notificationId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationSent", true);
        updates.put("sentAt", System.currentTimeMillis());

        db.collection("arrival_notifications").document(notificationId)
                .update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Notification marked as sent: " + notificationId);
                    } else {
                        Log.e(TAG, "Failed to mark notification as sent: " + notificationId);
                    }
                });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any resources if needed
        lastNotificationTime.clear();
    }
}