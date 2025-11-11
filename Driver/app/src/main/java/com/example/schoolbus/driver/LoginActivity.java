package com.example.schoolbus.driver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.schoolbus.driver.models.User;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private Button btnLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ImageView ivLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        ivLogo     = findViewById(R.id.ivAppLogo);

        btnLogin.setOnClickListener(v -> loginUser());

        // Auto-login if already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndRedirect(currentUser.getUid());
        }

        ivLogo.animate().rotation(360).setDuration(1000).start();

    }

    private void loginUser() {
        String email = tilEmail.getEditText().getText().toString().trim();
        String password = tilPassword.getEditText().getText().toString().trim();

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String userId = authResult.getUser().getUid();
                    checkUserRoleAndRedirect(userId);
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage();
                    if (msg.contains("invalid")) {
                        tilEmail.setError("Invalid email or password");
                    } else if (msg.contains("network")) {
                        Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Login failed: " + msg, Toast.LENGTH_LONG).show();
                    }
                    resetLoginButton();
                });
    }

    private void checkUserRoleAndRedirect(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        User user = document.toObject(User.class);
                        if (user != null && "driver".equals(user.getRole())) {
                            if (user.getBusId() != null && !user.getBusId().isEmpty()) {
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(this, "No bus assigned. Contact admin.", Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                                resetLoginButton();
                            }
                        } else {
                            Toast.makeText(this, "Access denied. Driver account required.", Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                            resetLoginButton();
                        }
                    } else {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        mAuth.signOut();
                        resetLoginButton();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error verifying role", Toast.LENGTH_SHORT).show();
                    resetLoginButton();
                });
    }

    private void resetLoginButton() {
        runOnUiThread(() -> {
            btnLogin.setEnabled(true);
            btnLogin.setText("LOGIN");
        });
    }
}