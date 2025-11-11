package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminLoginActivity extends AppCompatActivity {

    private static final String TAG = "ADMIN_LOGIN_DEBUG";

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ImageView ivLogo;
    private TextInputLayout tilEmail, tilPassword;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        Log.d(TAG, "=== ACTIVITY STARTED ===");

        // ==== VIEW BINDING ====
        etEmail   = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        ivLogo     = findViewById(R.id.ivAppLogo);
        tilEmail   = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);

        // ==== FIREBASE INIT ====
        try {
            FirebaseApp.initializeApp(this);
            auth = FirebaseAuth.getInstance();
            db   = FirebaseFirestore.getInstance();
            testFirebaseConnection();
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed", e);
            Toast.makeText(this, "Firebase init failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // ==== LOGIN CLICK ====
        btnLogin.setOnClickListener(v -> loginAdmin());

        // ==== OPTIONAL: animate logo ====
        ivLogo.animate().rotation(360).setDuration(1000).start();
    }

    // -------------------------------------------------
    // FIREBASE CONNECTION TEST
    // -------------------------------------------------
    private void testFirebaseConnection() {
        db.collection("test").limit(1).get()
                .addOnSuccessListener(snap -> Log.d(TAG, "Firestore connection OK"))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore test failed", e));
    }

    // -------------------------------------------------
    // ADMIN LOGIN LOGIC
    // -------------------------------------------------
    private void loginAdmin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            tilEmail.setError("Email required");
            return;
        } else tilEmail.setError(null);

        if (pass.isEmpty()) {
            tilPassword.setError("Password required");
            return;
        } else tilPassword.setError(null);

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        checkAdminRole(auth.getCurrentUser().getUid());
                    } else {
                        handleLoginError(task.getException());
                        resetLoginButton();
                    }
                })
                .addOnFailureListener(e -> {
                    handleLoginError(e);
                    resetLoginButton();
                });
    }

    private void checkAdminRole(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && "admin".equals(doc.getString("role"))) {
                        Toast.makeText(this, "Welcome Admin!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, AdminDashboardActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Access denied. Admin only.", Toast.LENGTH_LONG).show();
                        auth.signOut();
                        resetLoginButton();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error reading user data", Toast.LENGTH_LONG).show();
                    resetLoginButton();
                });
    }

    private void handleLoginError(Exception e) {
        String msg = "Login failed";
        if (e != null) {
            String err = e.getMessage();
            if (err.contains("invalid credential") || err.contains("INVALID_LOGIN_CREDENTIALS"))
                msg = "Invalid email or password";
            else if (err.contains("USER_NOT_FOUND"))
                msg = "No account found";
            else if (err.contains("network"))
                msg = "Check internet connection";
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void resetLoginButton() {
        runOnUiThread(() -> {
            btnLogin.setEnabled(true);
            btnLogin.setText("LOGIN");
        });
    }

    // -------------------------------------------------
    // AUTO-CREATE ADMIN (keep for dev only)
    // -------------------------------------------------
    private void createAdminUser() {
        String email = "admin@school.com";
        String pass  = "admin123";

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", email);
                    data.put("name", "System Administrator");
                    data.put("role", "admin");
                    data.put("createdAt", com.google.firebase.Timestamp.now());

                    db.collection("users").document(user.getUid()).set(data)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Admin created! Email: " + email, Toast.LENGTH_LONG).show();
                                etEmail.setText(email);
                                etPassword.setText(pass);
                                auth.signOut();
                                resetLoginButton();
                            });
                })
                .addOnFailureListener(e -> {
                    if (e.getMessage().contains("email already in use")) {
                        Toast.makeText(this, "Admin already exists", Toast.LENGTH_SHORT).show();
                    }
                    resetLoginButton();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
//        FirebaseUser user = auth.getCurrentUser();
//        if (user != null) checkAdminRole(user.getUid());
    }
}