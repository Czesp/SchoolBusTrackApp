package com.example.schoolbus.driver;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.schoolbus.driver.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DriverProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone, tvRole, tvBusAssigned;
    private MaterialButton btnLogout, btnBack, btnChangePassword;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupClickListeners();
        loadUserData();
    }

    private void initializeViews() {
        // Initialize TextViews
        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvPhone = findViewById(R.id.tv_phone);
        tvRole = findViewById(R.id.tv_role);
        tvBusAssigned = findViewById(R.id.tv_bus_assigned);

        // Initialize Buttons
        btnLogout = findViewById(R.id.btn_logout);
        btnBack = findViewById(R.id.btn_back);
        btnChangePassword = findViewById(R.id.btn_change_password);
    }

    private void setupClickListeners() {
        // Logout with confirmation
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());

        // Back to dashboard
        btnBack.setOnClickListener(v -> finish());

        // Change password
        btnChangePassword.setOnClickListener(v -> openChangePasswordActivity());
    }

    private void showLogoutConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes, Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openChangePasswordActivity() {
        Intent intent = new Intent(this, DriverChangePasswordActivity.class);
        startActivity(intent);
    }

    private void loadUserData() {
        if (auth.getCurrentUser() == null) {
            showError("User not logged in");
            navigateToLogin();
            return;
        }

        String userId = auth.getCurrentUser().getUid();

        // Show loading state
        setLoadingState(true);

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    setLoadingState(false);

                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            displayUserInfo(user);
                            loadBusInfo(user.getBusId());
                        } else {
                            showError("User data is invalid");
                        }
                    } else {
                        showError("User data not found");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoadingState(false);
                    showError("Error loading user data: " + e.getMessage());
                });
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            // Show loading state
            tvName.setText("Loading...");
            tvEmail.setText("Loading...");
            tvPhone.setText("Loading...");
            tvRole.setText("Loading...");
            tvBusAssigned.setText("Loading...");

            // Disable buttons while loading
            btnChangePassword.setEnabled(false);
            btnLogout.setEnabled(false);
            btnBack.setEnabled(false);
        } else {
            // Enable buttons after loading
            btnChangePassword.setEnabled(true);
            btnLogout.setEnabled(true);
            btnBack.setEnabled(true);
        }
    }

    private void displayUserInfo(User user) {
        tvName.setText(user.getName() != null ? user.getName() : "Not set");
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : "Not set");
        tvPhone.setText(user.getPhone() != null ? user.getPhone() : "Not set");
        tvRole.setText(user.getRole() != null ? user.getRole() : "Driver");
    }

    private void loadBusInfo(String busId) {
        if (busId == null || busId.isEmpty()) {
            tvBusAssigned.setText("No bus assigned");
            return;
        }

        db.collection("buses").document(busId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String busNumber = documentSnapshot.getString("busNumber");
                        if (busNumber != null && !busNumber.isEmpty()) {
                            tvBusAssigned.setText(busNumber);
                        } else {
                            tvBusAssigned.setText("Bus info unavailable");
                        }
                    } else {
                        tvBusAssigned.setText("Bus not found");
                    }
                })
                .addOnFailureListener(e -> {
                    tvBusAssigned.setText("Error loading bus");
                    Toast.makeText(this, "Failed to load bus information", Toast.LENGTH_SHORT).show();
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Set error state in UI
        tvName.setText("Error");
        tvEmail.setText("Error");
        tvPhone.setText("Error");
        tvRole.setText("Error");
        tvBusAssigned.setText("Error");
    }

    private void navigateToLogin() {
        auth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logoutUser() {
        auth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}