package com.example.schoolbus.admin;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditRouteActivity extends AppCompatActivity {

    // UI Components
    private TextView tvRouteName, tvStopCount;
    private EditText etRouteName;
    private Spinner spinnerBus;
    private LinearLayout layoutStopsList;
    private MaterialButton btnAddStop, btnSave, btnCancel;
    private MaterialToolbar toolbar;
    private FrameLayout loadingOverlay;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseFirestore db;

    private ImageButton btnProfile;

    // Data
    private String routeId, currentRouteName, currentBusId;
    private List<String> busIds = new ArrayList<>();
    private List<String> busNumbers = new ArrayList<>();
    private List<Map<String, Object>> stops = new ArrayList<>();
    private static final int MAX_STOPS = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_route);

        db = FirebaseFirestore.getInstance();

        initializeViews();
        getRouteDataFromIntent();
        loadBusesForSpinner();
        setupToolbar();
        setupClickListeners();
        loadRouteData();
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        tvRouteName = findViewById(R.id.tvRouteName);
        etRouteName = findViewById(R.id.etRouteName);
        spinnerBus = findViewById(R.id.spinnerBus);
        layoutStopsList = findViewById(R.id.layoutStopsList);
        btnAddStop = findViewById(R.id.btnAddStop);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        tvStopCount = findViewById(R.id.tvStopCount);
        toolbar = findViewById(R.id.toolbar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        progressBar = findViewById(R.id.progressBar);
        btnProfile = findViewById(R.id.btnProfile);
    }

    /**
     * Setup toolbar with navigation and menu
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }






    private void getRouteDataFromIntent() {
        routeId = getIntent().getStringExtra("ROUTE_ID");
        currentRouteName = getIntent().getStringExtra("ROUTE_NAME");
        currentBusId = getIntent().getStringExtra("ROUTE_BUS_ID");

        if (currentRouteName != null) {
            tvRouteName.setText("Editing: " + currentRouteName);
        }
    }

    private void loadBusesForSpinner() {
        db.collection("buses")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        busIds.clear();
                        busNumbers.clear();

                        // Add "No Bus" option
                        busIds.add("");
                        busNumbers.add("Select Bus");

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String busId = document.getId();
                            String busNumber = document.getString("busNumber");
                            if (busNumber != null) {
                                busIds.add(busId);
                                busNumbers.add(busNumber);
                            }
                        }

                        ArrayAdapter<String> busAdapter = new ArrayAdapter<>(this,
                                android.R.layout.simple_spinner_item, busNumbers);
                        busAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerBus.setAdapter(busAdapter);

                        // Set current bus selection
                        if (currentBusId != null && !currentBusId.isEmpty()) {
                            int busIndex = busIds.indexOf(currentBusId);
                            if (busIndex >= 0) {
                                spinnerBus.setSelection(busIndex);
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error loading buses", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupClickListeners() {
        btnAddStop.setOnClickListener(v -> {
            if (layoutStopsList.getChildCount() < MAX_STOPS) {
                addStopField("", "", "");
                updateStopCount();
            } else {
                showToast("Maximum " + MAX_STOPS + " stops allowed");
            }
        });

        btnSave.setOnClickListener(v -> {
            saveRouteChanges();
        });

        btnCancel.setOnClickListener(v -> {
            finish();
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void loadRouteData() {
        if (currentRouteName != null) {
            etRouteName.setText(currentRouteName);
        }

        // Load stops from Firestore
        db.collection("routes").document(routeId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Object stopsObj = documentSnapshot.get("stops");
                        stops.clear();

                        if (stopsObj instanceof List) {
                            List<?> stopsList = (List<?>) stopsObj;

                            for (Object stopObj : stopsList) {
                                Map<String, Object> stopMap = new HashMap<>();

                                if (stopObj instanceof String) {
                                    // Stop is stored as simple string - convert to map
                                    stopMap.put("name", stopObj);
                                    stopMap.put("latitude", 0.0);
                                    stopMap.put("longitude", 0.0);
                                } else if (stopObj instanceof Map) {
                                    // Stop is stored as HashMap
                                    Map<String, Object> existingStop = (Map<String, Object>) stopObj;
                                    stopMap.put("name", existingStop.get("name") != null ? existingStop.get("name") : "");
                                    stopMap.put("latitude", existingStop.get("latitude") != null ? existingStop.get("latitude") : 0.0);
                                    stopMap.put("longitude", existingStop.get("longitude") != null ? existingStop.get("longitude") : 0.0);
                                }
                                stops.add(stopMap);
                            }
                        }

                        if (!stops.isEmpty()) {
                            populateStopsFields();
                        } else {
                            // Add one empty stop field if no stops exist
                            addStopField("", "", "");
                        }
                    } else {
                        // Add one empty stop field if route doesn't exist
                        addStopField("", "", "");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading stops: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Add one empty stop field as fallback
                    addStopField("", "", "");
                });
    }

    private void populateStopsFields() {
        layoutStopsList.removeAllViews();
        for (Map<String, Object> stop : stops) {
            String name = stop.get("name") != null ? stop.get("name").toString() : "";
            String latitude = stop.get("latitude") != null ? stop.get("latitude").toString() : "0.0";
            String longitude = stop.get("longitude") != null ? stop.get("longitude").toString() : "0.0";
            addStopField(name, latitude, longitude);
        }
        updateStopCount();

        // If no stops were added, add one empty field
        if (layoutStopsList.getChildCount() == 0) {
            addStopField("", "27.7172", "85.3240");
        }
    }

    private void addStopField(String stopName, String latitude, String longitude) {
        // Create stop card container
        MaterialCardView stopCard = new MaterialCardView(this);
        stopCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        stopCard.setCardElevation(4f);
        stopCard.setRadius(12f);
        stopCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg));

        LinearLayout stopItem = new LinearLayout(this);
        stopItem.setOrientation(LinearLayout.VERTICAL);
        stopItem.setPadding(20, 20, 20, 20);

        // Stop header with remove button
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tvStopNumber = new TextView(this);
        tvStopNumber.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        tvStopNumber.setText("🚏 Stop " + (layoutStopsList.getChildCount() + 1));
        tvStopNumber.setTextSize(14);
        tvStopNumber.setTypeface(null, Typeface.BOLD);
        tvStopNumber.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));

        Button btnRemove = new Button(this);
        btnRemove.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        btnRemove.setText("Remove");
        btnRemove.setTextSize(12);
        btnRemove.setBackground(ContextCompat.getDrawable(this, R.drawable.outline_button_bg));
        btnRemove.setTextColor(ContextCompat.getColor(this, R.color.alert_urgent));
        btnRemove.setOnClickListener(v -> {
            layoutStopsList.removeView(stopCard);
            updateStopNumbers();
            updateStopCount();
        });

        headerLayout.addView(tvStopNumber);
        headerLayout.addView(btnRemove);

        // Stop Name
        EditText etStopName = new EditText(this);
        etStopName.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        etStopName.setHint("Stop Name");
        etStopName.setPadding(16, 16, 16, 16);
        etStopName.setBackgroundResource(android.R.drawable.edit_text);
        etStopName.setText(stopName);
        etStopName.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));

        // Latitude and Longitude in horizontal layout
        LinearLayout coordLayout = new LinearLayout(this);
        coordLayout.setOrientation(LinearLayout.HORIZONTAL);
        coordLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText etLatitude = new EditText(this);
        etLatitude.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        etLatitude.setHint("Latitude");
        etLatitude.setPadding(16, 16, 16, 16);
        etLatitude.setBackgroundResource(android.R.drawable.edit_text);
        etLatitude.setText(latitude);
        etLatitude.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etLatitude.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));

        // Add space between inputs
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(12, 1));

        EditText etLongitude = new EditText(this);
        etLongitude.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        etLongitude.setHint("Longitude");
        etLongitude.setPadding(16, 16, 16, 16);
        etLongitude.setBackgroundResource(android.R.drawable.edit_text);
        etLongitude.setText(longitude);
        etLongitude.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etLongitude.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));

        coordLayout.addView(etLatitude);
        coordLayout.addView(space);
        coordLayout.addView(etLongitude);

        // Add all views to stop item
        stopItem.addView(headerLayout);
        stopItem.addView(etStopName);
        stopItem.addView(coordLayout);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        stopItem.setLayoutParams(params);

        stopCard.addView(stopItem);
        layoutStopsList.addView(stopCard);
    }

    /**
     * Update stop numbers after removal
     */
    private void updateStopNumbers() {
        for (int i = 0; i < layoutStopsList.getChildCount(); i++) {
            View stopCard = layoutStopsList.getChildAt(i);
            if (stopCard instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) stopCard;
                LinearLayout stopItem = (LinearLayout) card.getChildAt(0);
                LinearLayout headerLayout = (LinearLayout) stopItem.getChildAt(0);
                TextView tvStopNumber = (TextView) headerLayout.getChildAt(0);
                tvStopNumber.setText("🚏 Stop " + (i + 1));
            }
        }
    }

    /**
     * Update the stop count display
     */
    private void updateStopCount() {
        tvStopCount.setText(layoutStopsList.getChildCount() + "/" + MAX_STOPS);
    }

    private void saveRouteChanges() {
        String routeName = etRouteName.getText().toString().trim();
        String selectedBusId = getSelectedBusId();
        List<Map<String, Object>> updatedStops = getStopsFromFields();

        // Validation
        if (routeName.isEmpty()) {
            Toast.makeText(this, "Please enter route name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (updatedStops.isEmpty()) {
            Toast.makeText(this, "Please add at least one stop", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if any stop name is empty
        for (Map<String, Object> stop : updatedStops) {
            String name = (String) stop.get("name");
            if (name == null || name.trim().isEmpty()) {
                Toast.makeText(this, "Please fill in all stop names", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        updateRouteInFirestore(routeName, selectedBusId, updatedStops);
    }

    private String getSelectedBusId() {
        int selectedPosition = spinnerBus.getSelectedItemPosition();
        return (selectedPosition >= 0 && selectedPosition < busIds.size()) ? busIds.get(selectedPosition) : "";
    }

    private List<Map<String, Object>> getStopsFromFields() {
        List<Map<String, Object>> stopsList = new ArrayList<>();
        for (int i = 0; i < layoutStopsList.getChildCount(); i++) {
            View stopCard = layoutStopsList.getChildAt(i);
            if (stopCard instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) stopCard;
                LinearLayout layout = (LinearLayout) card.getChildAt(0);

                // Get stop name (second child)
                EditText etStopName = (EditText) layout.getChildAt(1);
                String stopName = etStopName.getText().toString().trim();

                // Get coordinates (third child is the coord layout)
                LinearLayout coordLayout = (LinearLayout) layout.getChildAt(2);
                EditText etLatitude = (EditText) coordLayout.getChildAt(0);
                EditText etLongitude = (EditText) coordLayout.getChildAt(2);

                String latStr = etLatitude.getText().toString().trim();
                String lngStr = etLongitude.getText().toString().trim();

                // Validate all fields are filled
                if (!stopName.isEmpty()) {
                    if (latStr.isEmpty() || lngStr.isEmpty()) {
                        Toast.makeText(this, "Please enter both latitude and longitude for: " + stopName, Toast.LENGTH_LONG).show();
                        return new ArrayList<>();
                    }

                    double latitude, longitude;
                    try {
                        latitude = Double.parseDouble(latStr);
                        longitude = Double.parseDouble(lngStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid coordinates for: " + stopName, Toast.LENGTH_LONG).show();
                        return new ArrayList<>();
                    }

                    // Validate coordinates range
                    if (latitude < 26 || latitude > 30 || longitude < 80 || longitude > 88) {
                        Toast.makeText(this,
                                "Invalid coordinates for: " + stopName +
                                        "\nExpected: Lat 26-30, Lng 80-88",
                                Toast.LENGTH_LONG).show();
                        return new ArrayList<>();
                    }

                    Map<String, Object> stopMap = new HashMap<>();
                    stopMap.put("name", stopName);
                    stopMap.put("latitude", latitude);
                    stopMap.put("longitude", longitude);
                    stopMap.put("stopOrder", i + 1);
                    stopsList.add(stopMap);
                }
            }
        }
        return stopsList;
    }

    private void updateRouteInFirestore(String routeName, String busId, List<Map<String, Object>> stops) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("routeName", routeName);
        updates.put("busId", busId);
        updates.put("stops", stops);

        showLoading(true);
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        db.collection("routes").document(routeId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Update bus route assignment
                    updateBusRouteAssignment(busId, routeId);
                    Toast.makeText(this, "Route updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Changes");
                    showLoading(false);
                });
    }

    private void updateBusRouteAssignment(String busId, String routeId) {
        // First, remove this route from any other buses
        removeRouteFromAllBuses(routeId);

        // Then assign to selected bus
        if (busId != null && !busId.isEmpty()) {
            Map<String, Object> busUpdates = new HashMap<>();
            busUpdates.put("routeId", routeId);

            db.collection("buses").document(busId)
                    .update(busUpdates)
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Route updated but bus assignment failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void removeRouteFromAllBuses(String routeId) {
        db.collection("buses")
                .whereEqualTo("routeId", routeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> busUpdates = new HashMap<>();
                            busUpdates.put("routeId", "");
                            db.collection("buses").document(document.getId()).update(busUpdates);
                        }
                    }
                });
    }

    /**
     * Show/hide loading overlay
     */
    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Utility method for showing toast messages
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}