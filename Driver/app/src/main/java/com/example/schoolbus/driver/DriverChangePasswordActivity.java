package com.example.schoolbus.driver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DriverChangePasswordActivity extends AppCompatActivity {

    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private TextInputLayout currentPasswordLayout, newPasswordLayout, confirmPasswordLayout;
    private MaterialButton btnChangePassword, btnCancel;
    private LinearLayout strengthIndicator;
    private TextView tvPasswordStrength;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    // Password strength colors
    private int colorWeak, colorMedium, colorStrong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupColors();
        setupClickListeners();
        setupPasswordStrengthListener();
    }

    private void initializeViews() {
        // Initialize EditTexts
        etCurrentPassword = findViewById(R.id.et_current_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);

        // Initialize TextInputLayouts
        currentPasswordLayout = findViewById(R.id.currentPasswordLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        // Initialize Buttons
        btnChangePassword = findViewById(R.id.btn_change_password);
        btnCancel = findViewById(R.id.btn_cancel);

        // Initialize strength indicator
        strengthIndicator = findViewById(R.id.strengthIndicator);
        tvPasswordStrength = findViewById(R.id.tv_password_strength);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
    }

    private void setupColors() {
        // Define password strength colors using existing colors
        colorWeak = ContextCompat.getColor(this, R.color.error_color);
        colorMedium = ContextCompat.getColor(this, R.color.warning_color);
        colorStrong = ContextCompat.getColor(this, R.color.success_color);
    }

    private void showSuccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Success")
                .setMessage("Your password has been changed successfully!")
                .setPositiveButton("OK", (dialog, which) -> {
                    finish(); // This goes back to DriverProfileActivity
                })
                .setCancelable(false)
                .show();
    }

    private void setupClickListeners() {
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupPasswordStrengthListener() {
        etNewPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                if (password.isEmpty()) {
                    strengthIndicator.setVisibility(View.GONE);
                    tvPasswordStrength.setVisibility(View.GONE);
                } else {
                    strengthIndicator.setVisibility(View.VISIBLE);
                    tvPasswordStrength.setVisibility(View.VISIBLE);
                    updatePasswordStrength(password);
                }
            }
        });
    }

    private void updatePasswordStrength(String password) {
        int strength = calculatePasswordStrength(password);

        // Reset all bars
        strengthBar1.setBackgroundColor(colorWeak);
        strengthBar2.setBackgroundColor(colorWeak);
        strengthBar3.setBackgroundColor(colorWeak);
        strengthBar4.setBackgroundColor(colorWeak);

        switch (strength) {
            case 1: // Weak
                strengthBar1.setBackgroundColor(colorWeak);
                tvPasswordStrength.setText("Password strength: Weak");
                tvPasswordStrength.setTextColor(colorWeak);
                break;
            case 2: // Fair
                strengthBar1.setBackgroundColor(colorMedium);
                strengthBar2.setBackgroundColor(colorMedium);
                tvPasswordStrength.setText("Password strength: Fair");
                tvPasswordStrength.setTextColor(colorMedium);
                break;
            case 3: // Good
                strengthBar1.setBackgroundColor(colorMedium);
                strengthBar2.setBackgroundColor(colorMedium);
                strengthBar3.setBackgroundColor(colorMedium);
                tvPasswordStrength.setText("Password strength: Good");
                tvPasswordStrength.setTextColor(colorMedium);
                break;
            case 4: // Strong
                strengthBar1.setBackgroundColor(colorStrong);
                strengthBar2.setBackgroundColor(colorStrong);
                strengthBar3.setBackgroundColor(colorStrong);
                strengthBar4.setBackgroundColor(colorStrong);
                tvPasswordStrength.setText("Password strength: Strong");
                tvPasswordStrength.setTextColor(colorStrong);
                break;
            default: // None
                tvPasswordStrength.setText("Password strength: None");
                tvPasswordStrength.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                break;
        }
    }

    private int calculatePasswordStrength(String password) {
        int strength = 0;

        // Length check (8+ characters)
        if (password.length() >= 8) {
            strength++;
        }

        // Contains uppercase letters
        if (password.matches(".*[A-Z].*")) {
            strength++;
        }

        // Contains lowercase letters
        if (password.matches(".*[a-z].*")) {
            strength++;
        }

        // Contains numbers
        if (password.matches(".*\\d.*")) {
            strength++;
        }

        // Contains special characters
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            strength++;
        }

        // Cap at 4 for our indicator
        return Math.min(strength, 4);
    }

    private void changePassword() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
            return;
        }

        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Reset errors
        resetErrors();

        // Validation
        if (!validateInputs(currentPassword, newPassword, confirmPassword)) {
            return;
        }

        // Check password strength
        int strength = calculatePasswordStrength(newPassword);
        if (strength < 2) {
            newPasswordLayout.setError("Please choose a stronger password");
            Toast.makeText(this, "Password is too weak. Please follow the requirements.", Toast.LENGTH_LONG).show();
            return;
        }

        // Show loading
        btnChangePassword.setEnabled(false);
        btnChangePassword.setText("Updating Password...");

        // Re-authenticate user first
        AuthCredential credential = EmailAuthProvider.getCredential(
                currentUser.getEmail(), currentPassword
        );

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(reauthTask -> {
                    if (reauthTask.isSuccessful()) {
                        // Re-authentication successful, now change password
                        updatePassword(newPassword);
                    } else {
                        // Re-authentication failed
                        btnChangePassword.setEnabled(true);
                        btnChangePassword.setText("Update Password");
                        currentPasswordLayout.setError("Current password is incorrect");
                        Toast.makeText(this, "Authentication failed. Please check your current password.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Update Password");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private boolean validateInputs(String currentPassword, String newPassword, String confirmPassword) {
        boolean isValid = true;

        if (TextUtils.isEmpty(currentPassword)) {
            currentPasswordLayout.setError("Current password is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(newPassword)) {
            newPasswordLayout.setError("New password is required");
            isValid = false;
        } else if (newPassword.length() < 8) {
            newPasswordLayout.setError("Password must be at least 8 characters");
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError("Please confirm your password");
            isValid = false;
        } else if (!newPassword.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            isValid = false;
        }

        return isValid;
    }

    private void resetErrors() {
        currentPasswordLayout.setError(null);
        newPasswordLayout.setError(null);
        confirmPasswordLayout.setError(null);
    }

    private void updatePassword(String newPassword) {
        currentUser.updatePassword(newPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Password updated successfully
                        showSuccessDialog();
                    } else {
                        // Password update failed
                        btnChangePassword.setEnabled(true);
                        btnChangePassword.setText("Update Password");
                        Toast.makeText(this, "Failed to change password: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Update Password");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}