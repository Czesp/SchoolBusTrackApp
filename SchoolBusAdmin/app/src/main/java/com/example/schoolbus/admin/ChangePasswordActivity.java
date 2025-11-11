package com.example.schoolbus.admin;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private MaterialButton btnChangePassword;
    private MaterialTextView tvPasswordStrength;
    private MaterialToolbar toolbar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        auth = FirebaseAuth.getInstance();
        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupPasswordStrengthChecker();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        etCurrentPassword = findViewById(R.id.et_current_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnChangePassword = findViewById(R.id.btn_change_password);
        tvPasswordStrength = findViewById(R.id.tv_password_strength);
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
        btnChangePassword.setOnClickListener(v -> changePassword());
    }

    private void setupPasswordStrengthChecker() {
        etNewPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                checkPasswordStrength(s.toString());
            }
        });
    }

    private void checkPasswordStrength(String password) {
        if (password.isEmpty()) {
            tvPasswordStrength.setText("");
            return;
        }

        int strength = calculatePasswordStrength(password);
        String strengthText;
        int textColor;

        switch (strength) {
            case 0:
            case 1:
                strengthText = "Weak";
                textColor = getResources().getColor(android.R.color.holo_red_dark);
                break;
            case 2:
                strengthText = "Fair";
                textColor = getResources().getColor(android.R.color.holo_orange_dark);
                break;
            case 3:
                strengthText = "Good";
                textColor = getResources().getColor(android.R.color.holo_blue_dark);
                break;
            case 4:
                strengthText = "Strong";
                textColor = getResources().getColor(android.R.color.holo_green_dark);
                break;
            default:
                strengthText = "";
                textColor = getResources().getColor(android.R.color.darker_gray);
        }

        tvPasswordStrength.setText("Password Strength: " + strengthText);
        tvPasswordStrength.setTextColor(textColor);
    }

    private int calculatePasswordStrength(String password) {
        int strength = 0;

        // Check length
        if (password.length() >= 6) strength++;
        if (password.length() >= 8) strength++;

        // Check for digits
        if (password.matches(".*\\d.*")) strength++;

        // Check for letters
        if (password.matches(".*[a-zA-Z].*")) strength++;

        // Check for special characters
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) strength++;

        // Limit to max 4 for our scale
        return Math.min(strength, 4);
    }

    private void changePassword() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (currentPassword.isEmpty()) {
            etCurrentPassword.setError("Current password is required");
            etCurrentPassword.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            etNewPassword.setError("New password is required");
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (newPassword.equals(currentPassword)) {
            etNewPassword.setError("New password must be different from current password");
            etNewPassword.requestFocus();
            return;
        }

        // Check password strength
        int strength = calculatePasswordStrength(newPassword);
        if (strength < 2) {
            etNewPassword.setError("Please choose a stronger password");
            etNewPassword.requestFocus();
            return;
        }

        // Show loading
        btnChangePassword.setEnabled(false);
        btnChangePassword.setText("Changing Password...");

        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            // First re-authenticate the user
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

            user.reauthenticate(credential)
                    .addOnCompleteListener(reauthTask -> {
                        if (reauthTask.isSuccessful()) {
                            // Re-authentication successful, now change password
                            user.updatePassword(newPassword)
                                    .addOnCompleteListener(changeTask -> {
                                        btnChangePassword.setEnabled(true);
                                        btnChangePassword.setText("Change Password");

                                        if (changeTask.isSuccessful()) {
                                            Toast.makeText(ChangePasswordActivity.this,
                                                    "🎉 Password updated successfully!", Toast.LENGTH_SHORT).show();

                                            // Clear fields on success
                                            etCurrentPassword.setText("");
                                            etNewPassword.setText("");
                                            etConfirmPassword.setText("");
                                            tvPasswordStrength.setText("");

                                            // Delay before finishing to show success message
                                            new android.os.Handler().postDelayed(
                                                    () -> finish(), 1500
                                            );
                                        } else {
                                            String errorMessage = "Failed to update password";
                                            if (changeTask.getException() != null) {
                                                errorMessage += ": " + changeTask.getException().getMessage();
                                            }
                                            Toast.makeText(ChangePasswordActivity.this,
                                                    errorMessage, Toast.LENGTH_LONG).show();
                                        }
                                    });
                        } else {
                            btnChangePassword.setEnabled(true);
                            btnChangePassword.setText("Change Password");
                            etCurrentPassword.setError("Current password is incorrect");
                            etCurrentPassword.requestFocus();

                            String errorMessage = "Authentication failed";
                            if (reauthTask.getException() != null) {
                                errorMessage += ": " + reauthTask.getException().getMessage();
                            }
                            Toast.makeText(ChangePasswordActivity.this,
                                    errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            btnChangePassword.setEnabled(true);
            btnChangePassword.setText("Change Password");
            Toast.makeText(this, "❌ User not found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}