package com.example.schoolbus.admin;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateRouteActivity extends AppCompatActivity {

    // UI Components
    private EditText etRouteName;
    private MaterialButton btnAddStop, btnSaveRoute;
    private LinearLayout layoutStopsList;
    private TextView tvStopCount;
    private MaterialToolbar toolbar;
    private FrameLayout loadingOverlay;
    private ProgressBar progressBar;

    private ImageButton btnProfile;

    // Firebase
    private FirebaseFirestore db;

    // Data
    private List<Stop> stopsList;
    private int stopCounter = 1;
    private static final int MAX_STOPS = 10;

    /**
     * Stop data model class
     */
    private static class Stop {
        String name;
        double latitude;
        double longitude;
        int order;
        boolean coordinatesValid;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_route);

        initializeFirebase();
        initializeViews();
        setupToolbar();
        setupClickListeners();

        // Add first stop initially
        addStopField();
        updateStopCount();
    }

    /**
     * Initialize Firebase Firestore
     */
    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        stopsList = new ArrayList<>();
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        etRouteName = findViewById(R.id.etRouteName);
        btnAddStop = findViewById(R.id.btnAddStop);
        btnSaveRoute = findViewById(R.id.btnSaveRoute);
        layoutStopsList = findViewById(R.id.layoutStopsList);
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


    /**
     * Open profile activity
     */
    private void openProfile() {
        Intent intent = new Intent(this, AdminProfileActivity.class);  // ← YOUR PROFILE ACTIVITY
        startActivity(intent);
    }


    private void setupClickListeners() {
        btnAddStop.setOnClickListener(v -> {
            if (stopCounter < MAX_STOPS) {
                addStopField();
                updateStopCount();
            } else {
                showToast("Maximum " + MAX_STOPS + " stops allowed");
            }
        });

        btnSaveRoute.setOnClickListener(v -> {
            saveRoute();
        });

        btnProfile.setOnClickListener(v -> openProfile());
    }

    /**
     * Add a new stop field to the form
     */
    private void addStopField() {
        // Create stop card container
        MaterialCardView stopCard = new MaterialCardView(this);
        stopCard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        stopCard.setCardElevation(4f);
        stopCard.setRadius(12f);
        stopCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg));
        stopCard.setTag("stop_item_" + stopCounter);

        // Main stop layout
        LinearLayout stopItem = new LinearLayout(this);
        stopItem.setOrientation(LinearLayout.VERTICAL);
        stopItem.setPadding(32, 20, 32, 20);

        // Header with stop number and remove button
        LinearLayout headerLayout = createStopHeader(stopCard);

        // Stop name input
        EditText etStopName = createStopNameInput();

        // Coordinates layout
        LinearLayout coordLayout = createCoordinatesLayout();

        // Add views to stop layout
        stopItem.addView(headerLayout);
        stopItem.addView(etStopName);
        stopItem.addView(coordLayout);

        // Add to card and main layout
        stopCard.addView(stopItem);
        layoutStopsList.addView(stopCard);
        stopCounter++;
    }

    /**
     * Create stop header with number and remove button
     */
    private LinearLayout createStopHeader(MaterialCardView stopCard) {
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Stop number text
        TextView tvStopHeader = new TextView(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(0, 0, 0, 12);
        tvStopHeader.setLayoutParams(textParams);
        tvStopHeader.setText("🚏 Stop " + stopCounter);
        tvStopHeader.setTextSize(16);
        tvStopHeader.setTypeface(null, Typeface.BOLD);
        tvStopHeader.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));

        headerLayout.addView(tvStopHeader);

        // Remove button (only show for stops after first one)
        if (stopCounter > 1) {
            Button btnRemove = new Button(this);
            btnRemove.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            btnRemove.setText("Remove");
            btnRemove.setTextSize(12);
            btnRemove.setBackground(ContextCompat.getDrawable(this, R.drawable.outline_button_bg));
            btnRemove.setTextColor(ContextCompat.getColor(this, R.color.alert_urgent));
            btnRemove.setOnClickListener(v -> {
                layoutStopsList.removeView(stopCard);
                stopCounter--;
                updateStopNumbers();
                updateStopCount();
                showToast("Stop removed");
            });
            headerLayout.addView(btnRemove);
        }

        return headerLayout;
    }

    /**
     * Create stop name input field
     */
    private EditText createStopNameInput() {
        EditText etStopName = new EditText(this);
        etStopName.setHint("Stop Name");
        etStopName.setPadding(16, 16, 16, 16);
        etStopName.setBackgroundResource(android.R.drawable.edit_text);
        etStopName.setTag("name_" + stopCounter);
        etStopName.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));
        return etStopName;
    }

    /**
     * Create coordinates input layout
     */
    private LinearLayout createCoordinatesLayout() {
        LinearLayout coordLayout = new LinearLayout(this);
        coordLayout.setOrientation(LinearLayout.HORIZONTAL);
        coordLayout.setPadding(0, 12, 0, 0);

        // Latitude input
        EditText etLatitude = createCoordinateInput("Latitude", "lat_" + stopCounter, 0);

        // Add space between inputs
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(12, 1));

        // Longitude input
        EditText etLongitude = createCoordinateInput("Longitude", "lng_" + stopCounter, 1);

        coordLayout.addView(etLatitude);
        coordLayout.addView(space);
        coordLayout.addView(etLongitude);

        return coordLayout;
    }

    /**
     * Create individual coordinate input field
     */
    private EditText createCoordinateInput(String hint, String tag, int index) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setPadding(16, 16, 16, 16);
        editText.setBackgroundResource(android.R.drawable.edit_text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        if (index == 0) {
            params.setMargins(0, 0, 6, 0);
        } else {
            params.setMargins(6, 0, 0, 0);
        }
        editText.setLayoutParams(params);
        editText.setTag(tag);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        editText.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));
        return editText;
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
                TextView tvStopHeader = (TextView) headerLayout.getChildAt(0);
                tvStopHeader.setText("🚏 Stop " + (i + 1));

                // Update tags for data extraction
                updateStopTags(stopItem, i + 1);
            }
        }
    }

    /**
     * Update tags for data extraction
     */
    private void updateStopTags(LinearLayout stopItem, int stopNumber) {
        // Update stop name tag
        EditText etName = (EditText) stopItem.getChildAt(1);
        etName.setTag("name_" + stopNumber);

        // Update coordinate tags
        LinearLayout coordLayout = (LinearLayout) stopItem.getChildAt(2);
        EditText etLat = (EditText) coordLayout.getChildAt(0);
        EditText etLng = (EditText) coordLayout.getChildAt(2);

        etLat.setTag("lat_" + stopNumber);
        etLng.setTag("lng_" + stopNumber);
    }

    /**
     * Update the stop count display
     */
    private void updateStopCount() {
        tvStopCount.setText(stopCounter + "/" + MAX_STOPS);
    }

    /**
     * Save route to Firebase - ORIGINAL LOGIC PRESERVED
     */
    private void saveRoute() {
        String routeName = etRouteName.getText().toString().trim();

        // Validate route name
        if (routeName.isEmpty()) {
            Toast.makeText(this, "Please enter route name", Toast.LENGTH_SHORT).show();
            etRouteName.requestFocus();
            return;
        }

        // Collect stops data using the original method
        stopsList.clear();
        boolean hasValidStop = false;

        for (int i = 0; i < layoutStopsList.getChildCount(); i++) {
            View stopCard = layoutStopsList.getChildAt(i);
            if (stopCard instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) stopCard;
                LinearLayout stopItem = (LinearLayout) card.getChildAt(0);

                // Extract data directly from the layout using original method
                String stopName = extractTextFromLayout(stopItem, 1); // Second child is stop name
                String latStr = extractCoordFromLayout(stopItem, 2, 0); // Third child is coord layout, first child is latitude
                String lngStr = extractCoordFromLayout(stopItem, 2, 1); // Third child is coord layout, second child is longitude

                Log.d("RouteDebug", "Stop " + (i+1) + " - Name: " + stopName + ", Lat: " + latStr + ", Lng: " + lngStr);

                // Validate all fields are filled
                if (stopName.isEmpty()) {
                    Toast.makeText(this, "Please enter name for Stop " + (i + 1), Toast.LENGTH_LONG).show();
                    return;
                }

                if (latStr.isEmpty() || lngStr.isEmpty()) {
                    Toast.makeText(this, "Please enter both latitude and longitude for Stop: " + stopName, Toast.LENGTH_LONG).show();
                    return;
                }

                // Validate coordinates are numbers
                double latitude, longitude;
                try {
                    latitude = Double.parseDouble(latStr);
                    longitude = Double.parseDouble(lngStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid coordinates for stop: " + stopName + ". Please enter valid numbers.", Toast.LENGTH_LONG).show();
                    return;
                }

                // Validate coordinates are within reasonable range (Nepal coordinates)
                if (latitude < 26 || latitude > 30 || longitude < 80 || longitude > 88) {
                    Toast.makeText(this,
                            "Suspicious coordinates for stop: " + stopName +
                                    "\nExpected: Latitude 26-30, Longitude 80-88 (Nepal area)" +
                                    "\nYou entered: " + latitude + ", " + longitude,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Stop stop = new Stop();
                stop.name = stopName;
                stop.latitude = latitude;
                stop.longitude = longitude;
                stop.order = stopsList.size() + 1;
                stop.coordinatesValid = true;
                stopsList.add(stop);
                hasValidStop = true;
            }
        }

        // Validate we have at least two stops
        if (!hasValidStop) {
            Toast.makeText(this, "Please add at least one stop", Toast.LENGTH_LONG).show();
            return;
        }

        if (stopsList.size() < 2) {
            Toast.makeText(this, "Please add at least two stops to create a route", Toast.LENGTH_LONG).show();
            return;
        }

        // Create route data for Firebase
        String routeId = "route_" + UUID.randomUUID().toString().substring(0, 8);

        List<Map<String, Object>> firebaseStops = new ArrayList<>();
        for (Stop stop : stopsList) {
            Map<String, Object> firebaseStop = new HashMap<>();
            firebaseStop.put("stopId", "stop_" + stop.order);
            firebaseStop.put("stopName", stop.name);
            firebaseStop.put("stopOrder", stop.order);
            firebaseStop.put("latitude", stop.latitude);
            firebaseStop.put("longitude", stop.longitude);

            firebaseStops.add(firebaseStop);
        }

        Map<String, Object> route = new HashMap<>();
        route.put("routeId", routeId);
        route.put("routeName", routeName);
        route.put("stops", firebaseStops);
        route.put("busId", ""); // Will assign later
        route.put("createdAt", com.google.firebase.Timestamp.now());

        // Show loading
        btnSaveRoute.setEnabled(false);
        btnSaveRoute.setText("Saving...");
        showLoading(true);

        // Save to Firebase
        db.collection("routes").document(routeId)
                .set(route)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Route '" + routeName + "' created successfully!", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    finish(); // Go back to route list
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error creating route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSaveRoute.setEnabled(true);
                    btnSaveRoute.setText("Create Route");
                    showLoading(false);
                });
    }

    /**
     * ORIGINAL METHOD: Direct extraction from layout structure
     */
    private String extractTextFromLayout(LinearLayout stopLayout, int childIndex) {
        if (stopLayout.getChildCount() > childIndex) {
            View child = stopLayout.getChildAt(childIndex);
            if (child instanceof EditText) {
                return ((EditText) child).getText().toString().trim();
            }
        }
        return "";
    }

    /**
     * ORIGINAL METHOD: Extract coordinate from coordinate layout
     */
    private String extractCoordFromLayout(LinearLayout stopLayout, int coordLayoutIndex, int coordIndex) {
        if (stopLayout.getChildCount() > coordLayoutIndex) {
            View coordLayout = stopLayout.getChildAt(coordLayoutIndex);
            if (coordLayout instanceof LinearLayout) {
                LinearLayout coordLinearLayout = (LinearLayout) coordLayout;
                if (coordLinearLayout.getChildCount() > coordIndex) {
                    View coordView = coordLinearLayout.getChildAt(coordIndex);
                    if (coordView instanceof EditText) {
                        return ((EditText) coordView).getText().toString().trim();
                    }
                }
            }
        }
        return "";
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}