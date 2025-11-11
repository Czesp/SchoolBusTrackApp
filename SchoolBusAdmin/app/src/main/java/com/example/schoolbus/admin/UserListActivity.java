package com.example.schoolbus.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private LinearLayout layoutDriversList, layoutParentsList;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabAddUser;
    private FirebaseFirestore db;
    private Button btnLoadMoreDrivers, btnLoadMoreParents;
    private TextView tvDriversCount, tvParentsCount, tvDriversSubtitle, tvParentsSubtitle;

    private List<UserItem> driversList = new ArrayList<>();
    private List<UserItem> parentsList = new ArrayList<>();
    private List<UserItem> displayedDrivers = new ArrayList<>();
    private List<UserItem> displayedParents = new ArrayList<>();

    private ImageView btnProfile;
    private static final int INITIAL_DISPLAY_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupClickListeners();
        setupPullToRefresh();
        loadUsers();
    }

    private void initializeViews() {
        layoutDriversList = findViewById(R.id.layoutDriversList);
        layoutParentsList = findViewById(R.id.layoutParentsList);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        fabAddUser = findViewById(R.id.fabAddUser);
        btnLoadMoreDrivers = findViewById(R.id.btnLoadMoreDrivers);
        btnLoadMoreParents = findViewById(R.id.btnLoadMoreParents);

        // New views for counters
        tvDriversCount = findViewById(R.id.tvDriversCount);
        tvParentsCount = findViewById(R.id.tvParentsCount);
        tvDriversSubtitle = findViewById(R.id.tvDriversSubtitle);
        tvParentsSubtitle = findViewById(R.id.tvParentsSubtitle);
        btnProfile = findViewById(R.id.btnProfile);

        // Set up toolbar with back button
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Set back button click listener
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        fabAddUser.setOnClickListener(v -> {
            startActivityForResult(new Intent(this, CreateUserActivity.class), 1001);
        });

        btnLoadMoreDrivers.setOnClickListener(v -> {
            loadMoreDrivers();
        });

        btnLoadMoreParents.setOnClickListener(v -> {
            loadMoreParents();
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void setupPullToRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadUsers();
        });
    }

    private void loadUsers() {
        db.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (task.isSuccessful()) {
                        driversList.clear();
                        parentsList.clear();
                        displayedDrivers.clear();
                        displayedParents.clear();

                        if (task.getResult().isEmpty()) {
                            showNoUsersMessage();
                        } else {
                            // Separate users by role
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String role = document.getString("role");
                                if ("driver".equals(role)) {
                                    driversList.add(new UserItem(document));
                                } else if ("parent".equals(role)) {
                                    parentsList.add(new UserItem(document));
                                }
                            }

                            // Display initial users
                            displayInitialUsers();
                            updateUserCounts();
                        }
                    } else {
                        Toast.makeText(this, "Error loading users: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateUserCounts() {
        tvDriversCount.setText(String.valueOf(driversList.size()));
        tvParentsCount.setText(String.valueOf(parentsList.size()));
        tvDriversSubtitle.setText(driversList.size() + " total");
        tvParentsSubtitle.setText(parentsList.size() + " total");
    }

    private void displayInitialUsers() {
        // Clear current displays
        layoutDriversList.removeAllViews();
        layoutParentsList.removeAllViews();

        // Display initial drivers
        int driversToShow = Math.min(driversList.size(), INITIAL_DISPLAY_COUNT);
        displayedDrivers.clear();
        for (int i = 0; i < driversToShow; i++) {
            displayedDrivers.add(driversList.get(i));
        }
        displayDriversSection();

        // Show/Hide Load More button for drivers
        if (driversList.size() > INITIAL_DISPLAY_COUNT) {
            btnLoadMoreDrivers.setVisibility(View.VISIBLE);
        } else {
            btnLoadMoreDrivers.setVisibility(View.GONE);
        }

        // Display initial parents
        int parentsToShow = Math.min(parentsList.size(), INITIAL_DISPLAY_COUNT);
        displayedParents.clear();
        for (int i = 0; i < parentsToShow; i++) {
            displayedParents.add(parentsList.get(i));
        }
        displayParentsSection();

        // Show/Hide Load More button for parents
        if (parentsList.size() > INITIAL_DISPLAY_COUNT) {
            btnLoadMoreParents.setVisibility(View.VISIBLE);
        } else {
            btnLoadMoreParents.setVisibility(View.GONE);
        }
    }

    private void loadMoreDrivers() {
        int currentSize = displayedDrivers.size();
        int remaining = driversList.size() - currentSize;
        int nextBatchSize = Math.min(remaining, INITIAL_DISPLAY_COUNT);

        for (int i = currentSize; i < currentSize + nextBatchSize; i++) {
            displayedDrivers.add(driversList.get(i));
        }

        displayDriversSection();

        // Hide button if all drivers are displayed
        if (displayedDrivers.size() >= driversList.size()) {
            btnLoadMoreDrivers.setVisibility(View.GONE);
        }
    }

    private void loadMoreParents() {
        int currentSize = displayedParents.size();
        int remaining = parentsList.size() - currentSize;
        int nextBatchSize = Math.min(remaining, INITIAL_DISPLAY_COUNT);

        for (int i = currentSize; i < currentSize + nextBatchSize; i++) {
            displayedParents.add(parentsList.get(i));
        }

        displayParentsSection();

        // Hide button if all parents are displayed
        if (displayedParents.size() >= parentsList.size()) {
            btnLoadMoreParents.setVisibility(View.GONE);
        }
    }

    private void showNoUsersMessage() {
        TextView tvNoUsers = new TextView(this);
        tvNoUsers.setText("🚫 No users created yet.\n\nTap the + button to add drivers and parents!");
        tvNoUsers.setTextSize(16);
        tvNoUsers.setGravity(Gravity.CENTER);
        tvNoUsers.setTextColor(Color.parseColor("#666666"));
        tvNoUsers.setPadding(0, 100, 0, 100);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tvNoUsers.setLayoutParams(params);

        layoutDriversList.addView(tvNoUsers);
    }

    private void displayDriversSection() {
        layoutDriversList.removeAllViews();

        if (displayedDrivers.isEmpty()) {
            TextView tvNoDrivers = new TextView(this);
            tvNoDrivers.setText("No drivers added yet");
            tvNoDrivers.setTextSize(14);
            tvNoDrivers.setGravity(Gravity.CENTER);
            tvNoDrivers.setTextColor(Color.parseColor("#666666"));
            tvNoDrivers.setPadding(0, 30, 0, 30);
            layoutDriversList.addView(tvNoDrivers);
            return;
        }

        for (UserItem user : displayedDrivers) {
            addUserToLayout(user, layoutDriversList, true);
        }
    }

    private void displayParentsSection() {
        layoutParentsList.removeAllViews();

        if (displayedParents.isEmpty()) {
            TextView tvNoParents = new TextView(this);
            tvNoParents.setText("No parents added yet");
            tvNoParents.setTextSize(14);
            tvNoParents.setGravity(Gravity.CENTER);
            tvNoParents.setTextColor(Color.parseColor("#666666"));
            tvNoParents.setPadding(0, 30, 0, 30);
            layoutParentsList.addView(tvNoParents);
            return;
        }

        for (UserItem user : displayedParents) {
            addUserToLayout(user, layoutParentsList, false);
        }
    }

    private void addUserToLayout(UserItem user, LinearLayout parentLayout, boolean isDriver) {
        // Create main user item container
        LinearLayout userItem = new LinearLayout(this);
        userItem.setOrientation(LinearLayout.HORIZONTAL);
        userItem.setPadding(40, 25, 25, 25);
        userItem.setBackgroundResource(R.drawable.user_card_background);
        userItem.setClickable(true);
        userItem.setElevation(2f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        userItem.setLayoutParams(params);

        // User info container (left side)
        LinearLayout userInfoLayout = new LinearLayout(this);
        userInfoLayout.setOrientation(LinearLayout.VERTICAL);
        userInfoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        // User name and role
        TextView tvUserInfo = new TextView(this);
        String roleEmoji = isDriver ? "🚗" : "👨‍👩‍👧‍👦";
        tvUserInfo.setText(roleEmoji + " " + user.name);
        tvUserInfo.setTextSize(16);
        tvUserInfo.setTextColor(Color.parseColor("#2E3B4E"));
        tvUserInfo.setPadding(0, 0, 0, 6);
        tvUserInfo.setTypeface(tvUserInfo.getTypeface(), android.graphics.Typeface.BOLD);

        // Email
        TextView tvEmail = new TextView(this);
        tvEmail.setText("📧 " + user.email);
        tvEmail.setTextSize(14);
        tvEmail.setTextColor(Color.parseColor("#7A8A99"));
        tvEmail.setPadding(0, 0, 0, 4);

        // Phone
        TextView tvPhone = new TextView(this);
        tvPhone.setText("📞 " + (user.phone != null ? user.phone : "No phone"));
        tvPhone.setTextSize(14);
        tvPhone.setTextColor(Color.parseColor("#7A8A99"));
        tvPhone.setPadding(0, 0, 0, 4);

        // Additional info based on role
        TextView tvAdditional = new TextView(this);
        if (isDriver) {
            tvAdditional.setText("🚌 Loading bus assignment...");
            tvAdditional.setTextSize(12);
            tvAdditional.setTextColor(Color.parseColor("#1976D2"));
            tvAdditional.setTypeface(tvAdditional.getTypeface(), android.graphics.Typeface.BOLD);

            // Load bus name for drivers
            if (user.busId != null && !user.busId.isEmpty()) {
                loadBusNameForUser(user.busId, tvAdditional);
            } else {
                tvAdditional.setText("🚌 Bus: Not assigned");
            }
        } else {
            tvAdditional.setText("👦 Loading student...");
            tvAdditional.setTextSize(12);
            tvAdditional.setTextColor(Color.parseColor("#7B1FA2"));
            tvAdditional.setTypeface(tvAdditional.getTypeface(), android.graphics.Typeface.BOLD);

            // Load student name for parents
            if (user.studentId != null && !user.studentId.isEmpty()) {
                loadStudentName(user.studentId, tvAdditional);
            } else {
                tvAdditional.setText("👦 Student: Not assigned");
            }
        }

        // Add to user info layout
        userInfoLayout.addView(tvUserInfo);
        userInfoLayout.addView(tvEmail);
        userInfoLayout.addView(tvPhone);
        userInfoLayout.addView(tvAdditional);

        // Three dots menu (right side) - Using your custom icon
        ImageView ivMenu = new ImageView(this);
        ivMenu.setImageResource(R.drawable.ic_more_vert);
        ivMenu.setPadding(16, 16, 16, 16);
        ivMenu.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ivMenu.setClickable(true);
        ivMenu.setColorFilter(Color.parseColor("#7A8A99"));
        ivMenu.setBackground(null);

// Set menu click listener
        ivMenu.setOnClickListener(v -> {
            showUserMenu(v, user, isDriver);
        });

        // Add both layouts to main item
        userItem.addView(userInfoLayout);
        userItem.addView(ivMenu);

        // Set click listener for the whole item (for edit)
        userItem.setOnClickListener(v -> {
            openEditUser(user, isDriver);
        });

        parentLayout.addView(userItem);
    }

    private void loadBusNameForUser(String busId, TextView textView) {
        db.collection("buses").document(busId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String busNumber = document.getString("busNumber");
                        textView.setText("🚌 Bus: " + (busNumber != null ? busNumber : "Unknown"));
                    } else {
                        textView.setText("🚌 Bus: Not found");
                    }
                })
                .addOnFailureListener(e -> {
                    textView.setText("🚌 Bus: Error loading");
                });
    }

    private void loadStudentName(String studentId, TextView textView) {
        db.collection("students").document(studentId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        textView.setText("👦 Student ID: " + studentId);
                    } else {
                        textView.setText("👦 Student: Not found");
                    }
                })
                .addOnFailureListener(e -> {
                    textView.setText("👦 Student: Error loading");
                });
    }

    private void showUserMenu(View anchorView, UserItem user, boolean isDriver) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add("Edit");
        popupMenu.getMenu().add("Show Password");
        popupMenu.getMenu().add("Delete");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Edit")) {
                openEditUser(user, isDriver);
                return true;
            } else if (item.getTitle().equals("Show Password")) {
                showUserPassword(user);
                return true;
            } else if (item.getTitle().equals("Delete")) {
                showDeleteUserConfirmation(user.userId, user.name);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showUserPassword(UserItem user) {
        String password = user.generatedPassword != null ? user.generatedPassword : "No password set";
        new android.app.AlertDialog.Builder(this)
                .setTitle("User Password")
                .setMessage("Password for " + user.name + ":\n\n" + password)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openEditUser(UserItem user, boolean isDriver) {
        Intent intent = new Intent(this, EditUserActivity.class);
        intent.putExtra("USER_ID", user.userId);
        intent.putExtra("USER_EMAIL", user.email);
        intent.putExtra("USER_NAME", user.name);
        intent.putExtra("USER_ROLE", isDriver ? "driver" : "parent");
        intent.putExtra("USER_PHONE", user.phone != null ? user.phone : "");
        intent.putExtra("USER_BUS_ID", user.busId != null ? user.busId : "");
        intent.putExtra("USER_STUDENT_ID", user.studentId != null ? user.studentId : "");
        startActivity(intent);
    }

    private void showDeleteUserConfirmation(String userId, String userName) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete '" + userName + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteUser(userId);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUser(String userId) {
        // First get the user data to check if it's a parent and get studentId
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        String studentId = documentSnapshot.getString("studentId");

                        // Delete user from Firestore
                        db.collection("users").document(userId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // If it's a parent, delete associated student
                                    if ("parent".equals(role) && studentId != null && !studentId.isEmpty()) {
                                        deleteStudent(studentId, userId);
                                    } else {
                                        completeUserDeletion(userId);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Error deleting user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteStudent(String studentId, String userId) {
        db.collection("students").document(studentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    completeUserDeletion(userId);
                })
                .addOnFailureListener(e -> {
                    // Still complete user deletion even if student deletion fails
                    Toast.makeText(this, "User deleted but error deleting student: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    completeUserDeletion(userId);
                });
    }

    private void completeUserDeletion(String userId) {
        Toast.makeText(this, "User deleted successfully!", Toast.LENGTH_SHORT).show();
        loadUsers(); // Refresh the list

        // Also remove user from any bus assignments if they were a driver
        updateBusAfterDriverDeletion(userId);
    }

    private void updateBusAfterDriverDeletion(String driverId) {
        db.collection("buses")
                .whereEqualTo("driverId", driverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("driverId", "");
                            db.collection("buses").document(document.getId()).update(updates);
                        }
                    }
                });
    }

    // User item data class
    private static class UserItem {
        String userId;
        String email;
        String name;
        String phone;
        String busId;
        String studentId;
        String role;
        String generatedPassword;

        UserItem(QueryDocumentSnapshot document) {
            this.userId = document.getId();
            this.email = document.getString("email");
            this.name = document.getString("name");
            this.phone = document.getString("phone");
            this.busId = document.getString("busId");
            this.studentId = document.getString("studentId");
            this.role = document.getString("role");
            this.generatedPassword = document.getString("generatedPassword"); // Load from Firestore
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ✅ Check if we're returning from CreateUserActivity with success
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Immediately refresh the user list
            loadUsers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers();
    }
}