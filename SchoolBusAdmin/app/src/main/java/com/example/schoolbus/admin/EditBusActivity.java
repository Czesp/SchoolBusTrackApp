package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditBusActivity extends AppCompatActivity {

    // ALL YOUR VIEWS — findViewById STYLE
    private TextView tvBusNumber, loadingText, loadingBusIcon;
    private Spinner spinnerRoute, spinnerDriver;
    private MaterialButton btnSave, btnCancel;
    private MaterialToolbar toolbar;
    private ImageButton btnProfile;
    private View loadingOverlay;
    private FirebaseFirestore db;

    private String busId, busNumber, currentRouteId, currentDriverId;
    private boolean currentActiveStatus;

    private final List<RouteItem> routeList = new ArrayList<>();
    private final List<DriverItem> driverList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_bus);

        db = FirebaseFirestore.getInstance();

        // Get data from intent
        busId = getIntent().getStringExtra("BUS_ID");
        busNumber = getIntent().getStringExtra("BUS_NUMBER");
        currentRouteId = getIntent().getStringExtra("ROUTE_ID");
        currentDriverId = getIntent().getStringExtra("DRIVER_ID");
        currentActiveStatus = getIntent().getBooleanExtra("IS_ACTIVE", false);

        initializeViews();
        setupToolbar();
        loadRealDataFromFirebase();
        setupClickListeners();
    }

    private void initializeViews() {
        tvBusNumber = findViewById(R.id.tvBusNumber);
        spinnerRoute = findViewById(R.id.spinnerRoute);
        spinnerDriver = findViewById(R.id.spinnerDriver);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        toolbar = findViewById(R.id.toolbar);
        btnProfile = findViewById(R.id.btnProfile);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
        loadingBusIcon = findViewById(R.id.loadingBusIcon);

        tvBusNumber.setText("Editing: " + busNumber);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> {
            animateButtonClick(btnSave);
            saveBusChanges();
        });

        btnCancel.setOnClickListener(v -> finish());

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void animateButtonClick(View button) {
        button.animate()
                .scaleX(0.95f).scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> button.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100).start())
                .start();
    }

    private void loadRealDataFromFirebase() {
        loadRoutes();
        loadDrivers();
    }

    private void loadRoutes() {
        db.collection("routes").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                routeList.clear();
                routeList.add(new RouteItem("", "Select Route"));
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String id = doc.getId();
                    String name = doc.getString("routeName");
                    routeList.add(new RouteItem(id, name != null ? name : "Unnamed Route"));
                }
                setupRouteSpinner();
            }
        });
    }

    private void loadDrivers() {
        db.collection("users")
                .whereEqualTo("role", "driver")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        driverList.clear();
                        driverList.add(new DriverItem("", "Select Driver"));
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String id = doc.getId();
                            String name = doc.getString("name");
                            String busAssignment = doc.getString("busId");

                            String display = name != null ? name : "Unnamed Driver";
                            if (busAssignment != null && !busAssignment.isEmpty() && !id.equals(currentDriverId)) {
                                display += " (Assigned)";
                            }
                            driverList.add(new DriverItem(id, display));
                        }
                        setupDriverSpinner();
                    }
                });
    }

    private void setupRouteSpinner() {
        ArrayAdapter<RouteItem> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, routeList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRoute.setAdapter(adapter);

        if (currentRouteId != null && !currentRouteId.isEmpty()) {
            for (int i = 0; i < routeList.size(); i++) {
                if (routeList.get(i).getId().equals(currentRouteId)) {
                    spinnerRoute.setSelection(i);
                    break;
                }
            }
        }
    }

    private void setupDriverSpinner() {
        ArrayAdapter<DriverItem> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, driverList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDriver.setAdapter(adapter);

        if (currentDriverId != null && !currentDriverId.isEmpty()) {
            for (int i = 0; i < driverList.size(); i++) {
                if (driverList.get(i).getId().equals(currentDriverId)) {
                    spinnerDriver.setSelection(i);
                    break;
                }
            }
        }
    }

    private void saveBusChanges() {
        RouteItem route = (RouteItem) spinnerRoute.getSelectedItem();
        DriverItem driver = (DriverItem) spinnerDriver.getSelectedItem();

        String routeId = route != null ? route.getId() : "";
        String driverId = driver != null ? driver.getId() : "";

        if (routeId.isEmpty()) {
            showToast("Please select a route");
            return;
        }

        if (!driverId.isEmpty() && !driverId.equals(currentDriverId)) {
            checkDriverAssignment(driverId, routeId);
        } else {
            updateBusInFirebase(routeId, driverId);
        }
    }

    private void checkDriverAssignment(String driverId, String routeId) {
        showLoading(true);

        db.collection("buses")
                .whereEqualTo("driverId", driverId)
                .whereNotEqualTo(FieldPath.documentId(), busId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        showLoading(false);
                        showToast("Driver already assigned to another bus!");
                    } else {
                        updateBusInFirebase(routeId, driverId);
                    }
                });
    }

    private void updateBusInFirebase(String routeId, String driverId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("routeId", routeId);
        updates.put("driverId", driverId);

        db.collection("buses").document(busId)
                .update(updates)
                .addOnSuccessListener(aVoid -> synchronizeDriverAssignments(driverId))
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showToast("Update failed: " + e.getMessage());
                });
    }

    private void synchronizeDriverAssignments(String newDriverId) {
        if (newDriverId != null && !newDriverId.isEmpty()) {
            updateUserWithBusAssignment(newDriverId, busId);
        }

        if (currentDriverId != null && !currentDriverId.isEmpty() && !currentDriverId.equals(newDriverId)) {
            clearUserBusAssignment(currentDriverId);
        } else if (newDriverId == null || newDriverId.isEmpty()) {
            showSuccessState();
        }
    }

    private void updateUserWithBusAssignment(String driverId, String busId) {
        db.collection("users").document(driverId)
                .update("busId", busId)
                .addOnSuccessListener(aVoid -> clearDriverFromOtherBuses(driverId, busId))
                .addOnFailureListener(e -> {
                    showToast("Driver sync failed: " + e.getMessage());
                    showSuccessState();
                });
    }

    private void clearUserBusAssignment(String driverId) {
        db.collection("users").document(driverId)
                .update("busId", "")
                .addOnSuccessListener(aVoid -> showSuccessState())
                .addOnFailureListener(e -> {
                    showToast("Failed to unassign driver");
                    showSuccessState();
                });
    }

    private void clearDriverFromOtherBuses(String driverId, String currentBusId) {
        db.collection("buses")
                .whereEqualTo("driverId", driverId)
                .whereNotEqualTo(FieldPath.documentId(), currentBusId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            doc.getReference().update("driverId", "");
                        }
                    }
                    showSuccessState();
                });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
        btnSave.setText(show ? "Saving..." : "SAVE CHANGES");

        if (show) startLoadingAnimation();
        else stopLoadingAnimation();
    }

    private void startLoadingAnimation() {
        loadingBusIcon.animate()
                .rotationBy(360)
                .setDuration(1000)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    if (loadingOverlay.getVisibility() == View.VISIBLE)
                        startLoadingAnimation();
                })
                .start();
    }

    private void stopLoadingAnimation() {
        if (loadingBusIcon != null) {
            loadingBusIcon.animate().cancel();
            loadingBusIcon.setRotation(0);
        }
    }

    private void showSuccessState() {
        showLoading(false);
        loadingText.setText("Bus updated!");
        loadingBusIcon.setText("Checkmark");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            showToast("Bus updated successfully!");
            setResult(RESULT_OK);
            finish();
        }, 1500);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLoadingAnimation();
    }

    // Data classes
    public static class RouteItem {
        private final String id, name;
        public RouteItem(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        @Override public String toString() { return name; }
    }

    public static class DriverItem {
        private final String id, name;
        public DriverItem(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        @Override public String toString() { return name; }
    }
}