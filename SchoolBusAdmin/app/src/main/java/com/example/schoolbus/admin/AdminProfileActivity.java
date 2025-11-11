package com.example.schoolbus.admin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminProfileActivity extends AppCompatActivity {

    private TextView tvAdminName, tvAdminEmail;
    private TextInputEditText etName, etPhone, etAddress;
    private MaterialButton btnUpdateProfile, btnChangePassword;
    private MaterialToolbar toolbar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupToolbar();
        setupClickListeners();
        loadAdminProfile();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        tvAdminName = findViewById(R.id.tv_admin_name);
        tvAdminEmail = findViewById(R.id.tv_admin_email);
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etAddress = findViewById(R.id.et_address);
        btnUpdateProfile = findViewById(R.id.btn_update_profile);
        btnChangePassword = findViewById(R.id.btn_change_password);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        btnUpdateProfile.setOnClickListener(v -> updateProfile());

        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(AdminProfileActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });
    }

    private void loadAdminProfile() {
        if (currentUser != null) {
            String userId = currentUser.getUid();

            // Show loading state
            btnUpdateProfile.setEnabled(false);
            btnUpdateProfile.setText("Loading...");

            db.collection("users").document(userId)
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            // Restore button state
                            btnUpdateProfile.setEnabled(true);
                            btnUpdateProfile.setText("Update Profile");

                            if (task.isSuccessful() && task.getResult() != null) {
                                DocumentSnapshot document = task.getResult();
                                if (document.exists()) {
                                    // Load profile data
                                    String name = document.getString("name");
                                    String email = document.getString("email");
                                    String phone = document.getString("phone");
                                    String address = document.getString("address");

                                    // Update UI
                                    tvAdminName.setText(name != null ? name : "Admin");
                                    tvAdminEmail.setText(email != null ? email : currentUser.getEmail());
                                    etName.setText(name != null ? name : "");
                                    etPhone.setText(phone != null ? phone : "");
                                    etAddress.setText(address != null ? address : "");
                                } else {
                                    // Create initial profile if doesn't exist
                                    createInitialProfile();
                                }
                            } else {
                                Toast.makeText(AdminProfileActivity.this,
                                        "Failed to load profile", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void createInitialProfile() {
        if (currentUser != null) {
            String userId = currentUser.getUid();

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("name", "Admin");
            profileData.put("email", currentUser.getEmail());
            profileData.put("phone", "");
            profileData.put("address", "");
            profileData.put("role", "admin");
            profileData.put("createdAt", System.currentTimeMillis());

            db.collection("users").document(userId)
                    .set(profileData)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                tvAdminName.setText("Admin");
                                tvAdminEmail.setText(currentUser.getEmail());
                                Toast.makeText(AdminProfileActivity.this,
                                        "Profile initialized", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AdminProfileActivity.this,
                                        "Failed to create profile", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void updateProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (currentUser != null) {
            String userId = currentUser.getUid();

            // Show loading state
            btnUpdateProfile.setEnabled(false);
            btnUpdateProfile.setText("Updating...");

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("name", name);
            profileData.put("phone", phone);
            profileData.put("address", address);
            profileData.put("updatedAt", System.currentTimeMillis());

            db.collection("users").document(userId)
                    .update(profileData)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            // Restore button state
                            btnUpdateProfile.setEnabled(true);
                            btnUpdateProfile.setText("Update Profile");

                            if (task.isSuccessful()) {
                                tvAdminName.setText(name);
                                Toast.makeText(AdminProfileActivity.this,
                                        "Profile updated successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AdminProfileActivity.this,
                                        "Failed to update profile", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}