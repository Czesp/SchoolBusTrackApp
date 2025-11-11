package com.example.schoolbus.parent;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.schoolbus.parent.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ParentProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone, tvStudentId, tvRole, tvBusAssigned;
    private MaterialButton btnLogout, btnBack, btnChangePassword;
    private MaterialCardView cardPersonalInfo, cardHeader;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_profile);

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
        tvStudentId = findViewById(R.id.tv_student_id);
        tvRole = findViewById(R.id.tv_role);
        tvBusAssigned = findViewById(R.id.tv_bus_assigned);

        // Initialize Buttons
        btnLogout = findViewById(R.id.btn_logout);
        btnBack = findViewById(R.id.btn_back);
        btnChangePassword = findViewById(R.id.btn_change_password);

        // Initialize other views
        cardPersonalInfo = findViewById(R.id.card_personal_info);
        cardHeader = findViewById(R.id.card_header);
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes, Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openChangePasswordActivity() {
        Intent intent = new Intent(this, ChangePasswordActivity.class);
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
            tvName.setText("Loading...");
            tvEmail.setText("Loading...");
            tvPhone.setText("Loading...");
            tvStudentId.setText("Loading...");
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

    @SuppressLint("SetTextI18n")
    private void displayUserInfo(User user) {
        tvName.setText(user.getName() != null ? user.getName() : "Not set");
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : "Not set");
        tvPhone.setText(user.getPhone() != null ? user.getPhone() : "Not set");
        tvStudentId.setText(user.getStudentId() != null ? user.getStudentId() : "Not assigned");
        tvRole.setText(user.getRole() != null ? user.getRole() : "Parent");

        // Set role in header as well
        if (user.getRole() != null && !user.getRole().isEmpty()) {
            String roleDisplay = user.getRole();
            if (roleDisplay.equalsIgnoreCase("parent")) {
                roleDisplay = "Parent Account";
            }
            // The header role is already set in the layout, but you can update it here if needed
        }
    }

    private void loadBusInfo(String busId) {
        if (busId == null || busId.isEmpty()) {
            tvBusAssigned.setText("No bus assigned");
            tvBusAssigned.setTextColor(getResources().getColor(R.color.text_secondary));
            return;
        }

        db.collection("buses").document(busId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String busNumber = documentSnapshot.getString("busNumber");
                        String busModel = documentSnapshot.getString("busModel");
                        String busType = documentSnapshot.getString("busType");

                        StringBuilder busInfo = new StringBuilder();
                        if (busNumber != null && !busNumber.isEmpty()) {
                            busInfo.append("").append(busNumber);
                        }
                        if (busModel != null && !busModel.isEmpty()) {
                            if (busInfo.length() > 0) busInfo.append(" • ");
                            busInfo.append(busModel);
                        }
                        if (busType != null && !busType.isEmpty()) {
                            if (busInfo.length() > 0) busInfo.append(" • ");
                            busInfo.append(busType);
                        }

                        if (busInfo.length() > 0) {
                            tvBusAssigned.setText(busInfo.toString());
                            tvBusAssigned.setTextColor(getResources().getColor(R.color.dashboard_accent_blue));
                        } else {
                            tvBusAssigned.setText("Bus info unavailable");
                            tvBusAssigned.setTextColor(getResources().getColor(R.color.text_secondary));
                        }
                    } else {
                        tvBusAssigned.setText("Bus not found");
                        tvBusAssigned.setTextColor(getResources().getColor(R.color.text_secondary));
                    }
                })
                .addOnFailureListener(e -> {
                    tvBusAssigned.setText("Error loading bus");
                    tvBusAssigned.setTextColor(getResources().getColor(R.color.error_color));
                    Toast.makeText(this, "Failed to load bus information", Toast.LENGTH_SHORT).show();
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Set error state in UI with visual feedback
        tvName.setText("Error loading data");
        tvEmail.setText("—");
        tvPhone.setText("—");
        tvStudentId.setText("—");
        tvRole.setText("—");
        tvBusAssigned.setText("—");

        tvName.setTextColor(getResources().getColor(R.color.error_color));
    }

    private void navigateToLogin() {
        auth.signOut();
        Intent intent = new Intent(this, ParentLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void logoutUser() {
        // Show logging out state
        btnLogout.setText("Logging out...");
        btnLogout.setEnabled(false);

        auth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, ParentLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @SuppressLint({"MissingSuperCall", "GestureBackNavigation"})
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}