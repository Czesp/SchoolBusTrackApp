package com.example.schoolbus.parent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ParentLoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private Button btnLogin;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ImageView ivLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btn_login);
        ivLogo     = findViewById(R.id.ivAppLogo);
        btnLogin.setOnClickListener(v -> loginUser());

        // Auto-login if already signed in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndRedirect(currentUser.getUid());
        }

        ivLogo.animate().rotation(360).setDuration(1000).start();
    }

    private void loginUser() {
        String email = tilEmail.getEditText().getText().toString().trim();
        String password = tilPassword.getEditText().getText().toString().trim();

        // Reset errors
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

        auth.signInWithEmailAndPassword(email, password)
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
                    if (document.exists() && "parent".equals(document.getString("role"))) {
                        startActivity(new Intent(this, ParentMainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Access denied. Parent account required.", Toast.LENGTH_LONG).show();
                        auth.signOut();
                        resetLoginButton();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error verifying account", Toast.LENGTH_SHORT).show();
                    auth.signOut();
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