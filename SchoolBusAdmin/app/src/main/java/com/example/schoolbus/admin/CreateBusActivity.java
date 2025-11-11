package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateBusActivity extends AppCompatActivity {

    private TextInputEditText etBusNumber;
    private MaterialButton btnSaveBus;
    private MaterialToolbar toolbar;
    private ImageButton btnProfile;
    private FrameLayout loadingOverlay;
    private TextView loadingText, loadingBusIcon;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_bus);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupToolbar();
        setupClickListeners();

        // Set focus on bus number field for better UX
        etBusNumber.requestFocus();
    }

    /**
     * Initialize all UI components from the new layout
     */
    private void initializeViews() {
        etBusNumber = findViewById(R.id.etBusNumber);
        btnSaveBus = findViewById(R.id.btnSaveBus);
        toolbar = findViewById(R.id.toolbar);
        btnProfile = findViewById(R.id.btnProfile);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
        loadingBusIcon = findViewById(R.id.loadingBusIcon);
    }

    /**
     * Setup toolbar with navigation
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    /**
     * Setup all click listeners
     */
    private void setupClickListeners() {
        btnSaveBus.setOnClickListener(v -> {
            // Add button animation for better UX
            animateButtonClick();
            saveBus();
        });

        btnProfile.setOnClickListener(v -> {
            openProfile();
        });
    }

    /**
     * Animate button click for better user experience
     */
    private void animateButtonClick() {
        btnSaveBus.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> btnSaveBus.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    /**
     * Open profile activity
     */
    private void openProfile() {
        Intent intent = new Intent(this, AdminProfileActivity.class);  // ← YOUR PROFILE ACTIVITY
        startActivity(intent);
    }

    /**
     * Save bus to Firebase - PRESERVED ORIGINAL LOGIC WITH ENHANCEMENTS
     */
    private void saveBus() {
        String busNumber = etBusNumber.getText().toString().trim().toUpperCase();

        // Validate bus number
        if (busNumber.isEmpty()) {
            showToast("Please enter bus number");
            etBusNumber.requestFocus();
            etBusNumber.setError("Bus number is required");
            return;
        }

        // Basic validation for bus number format - PRESERVED ORIGINAL LOGIC
        if (!busNumber.matches("BUS-[0-9]+")) {
            showToast("Bus number should be in format: BUS-001, BUS-002, etc.");
            etBusNumber.requestFocus();
            etBusNumber.setError("Invalid format");
            return;
        }

        // Clear any previous errors
        etBusNumber.setError(null);

        // Check if bus number already exists - PRESERVED ORIGINAL LOGIC
        checkAndCreateBus(busNumber);
    }

    /**
     * Check if bus exists and create if not - ENHANCED WITH LOADING STATES
     */
    private void checkAndCreateBus(String busNumber) {
        showLoading(true);

        db.collection("buses")
                .whereEqualTo("busNumber", busNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult() != null && !task.getResult().isEmpty()) {
                            showLoading(false);
                            showToast("Bus number already exists: " + busNumber);
                            etBusNumber.requestFocus();
                            etBusNumber.selectAll();
                        } else {
                            // Create new bus - PRESERVED ORIGINAL LOGIC
                            createNewBus(busNumber);
                        }
                    } else {
                        showLoading(false);
                        showToast("Error loading buses: " + task.getException().getMessage());
                    }
                });
    }

    /**
     * Create new bus in Firebase - PRESERVED ORIGINAL LOGIC WITH ENHANCEMENTS
     */
    private void createNewBus(String busNumber) {
        String busId = "bus_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> bus = new HashMap<>();
        bus.put("busId", busId);
        bus.put("busNumber", busNumber);
        bus.put("routeId", "");
        bus.put("driverId", "");
        bus.put("isActive", false);
        bus.put("createdAt", com.google.firebase.Timestamp.now());

        // Save to Firebase - PRESERVED ORIGINAL LOGIC
        db.collection("buses").document(busId)
                .set(bus)
                .addOnSuccessListener(aVoid -> {
                    showSuccessState(busNumber);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showToast("Error creating bus: " + e.getMessage());
                    btnSaveBus.setEnabled(true);
                    btnSaveBus.setText("CREATE BUS");
                });
    }

    /**
     * Show loading state with animation
     */
    private void showLoading(boolean show) {
        btnSaveBus.setEnabled(!show);
        btnSaveBus.setText(show ? "Creating..." : "CREATE BUS");
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            startLoadingAnimation();
        } else {
            stopLoadingAnimation();
        }
    }

    /**
     * Start bus icon rotation animation
     */
    private void startLoadingAnimation() {
        loadingBusIcon.animate()
                .rotationBy(360)
                .setDuration(1000)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    if (loadingOverlay.getVisibility() == View.VISIBLE) {
                        startLoadingAnimation(); // Loop animation
                    }
                })
                .start();
    }

    /**
     * Stop loading animation
     */
    private void stopLoadingAnimation() {
        if (loadingBusIcon != null) {
            loadingBusIcon.animate().cancel();
            loadingBusIcon.setRotation(0);
        }
    }

    /**
     * Show success state before finishing
     */
    private void showSuccessState(String busNumber) {
        if (loadingText != null) {
            loadingText.setText("Bus '" + busNumber + "' created!");
        }
        if (loadingBusIcon != null) {
            loadingBusIcon.setText("✅");
        }

        showToast("Bus '" + busNumber + "' created successfully!");

        // Delay before closing to show success message
        new android.os.Handler().postDelayed(() -> {
            setResult(RESULT_OK);
            finish(); // Go back to bus list
        }, 1500);
    }

    /**
     * Utility method for showing toast messages
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up animations
        stopLoadingAnimation();
    }
}