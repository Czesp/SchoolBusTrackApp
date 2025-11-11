package com.example.schoolbus.driver;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.schoolbus.driver.models.Bus;
import com.example.schoolbus.driver.models.Route;
import com.example.schoolbus.driver.models.SafetyAlert;
import com.example.schoolbus.driver.models.User;
import com.example.schoolbus.driver.services.DirectionsService;
import com.example.schoolbus.driver.services.EnhancedETAService;
import com.example.schoolbus.driver.services.NotificationService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.Timestamp;
import java.util.*;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "DriverApp";

    // Safety Detection Constants
    private static final float MAX_SPEED_KMPH = 60.0f;
    private static final long PROLONGED_STOP_THRESHOLD = 300000; // 5 minutes
    private static final float ROUTE_DEVIATION_THRESHOLD = 500; // meters
    private static final long MIN_ETA_UPDATE_INTERVAL = 30000; // 30 seconds

    private TextView tvBusInfo, tvRouteInfo, tvDriverName, tvNextStop, tvETA, tvLocationStatus;
    private MaterialButton btnToggleLocation;
    private MaterialToolbar toolbar;
    private ImageButton btnLogout, btnProfile;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isSharingLocation = false;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentDriverId;
    private Bus currentBus;
    private Route currentRoute;
    private User currentUser;

    private Map<String, Marker> stopMarkers = new HashMap<>();
    private Marker currentLocationMarker;
    private Marker nextStopMarker;
    private Polyline routePolyline;
    private Polyline directionPolyline;
    private String currentNextStop = "";
    private long currentETAMinutes = 0;

    private boolean isMapReady = false;
    private boolean isRouteLoaded = false;

    private NotificationService notificationService;
    private EnhancedETAService enhancedETAService;
    private DirectionsService directionsService;

    // Enhanced routing variables
    private List<LatLng> fullRoutePath = new ArrayList<>();
    private List<LatLng> routePath = new ArrayList<>();
    private int currentStopIndex = 0;
    private Location lastKnownLocation;
    private long lastLocationUpdateTime = 0;
    private long lastETACalculationTime = 0;

    // Safety Detection Variables
    private Location lastMovingLocation;
    private long stoppedSince = 0;
    private boolean isStopped = false;

    private Map<String, Long> lastAlertTimeMap = new HashMap<>();
    private static final long ALERT_COOLDOWN_MS = 3000000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentDriverId = mAuth.getCurrentUser().getUid();

        Log.d(TAG, "=== DRIVER APP STARTED ===");
        Log.d(TAG, "Driver ID: " + currentDriverId);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        directionsService = DirectionsService.getInstance(this);
        notificationService = new NotificationService(this);
        enhancedETAService = EnhancedETAService.getInstance();

        initViews();
        setupToolbar();
        setupClickListeners();
        setupLocationCallback();
        setupMapFragment();
        loadDriverData();
        clearCurrentNotificationSpam();
        checkPermissionsStatus();
    }

    private boolean hasLocationPermissions() {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Location Permissions - Fine: " + hasFineLocation + ", Coarse: " + hasCoarseLocation);
        return hasFineLocation && hasCoarseLocation;
    }

    private void checkPermissionsStatus() {
        if (hasLocationPermissions()) {
            Log.d(TAG, "✓ Location permissions GRANTED");
        } else {
            Log.w(TAG, "✗ Location permissions NOT GRANTED");
        }
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvBusInfo = findViewById(R.id.tvBusInfo);
        tvRouteInfo = findViewById(R.id.tvRouteInfo);
        tvDriverName = findViewById(R.id.tvDriverName);
        tvNextStop = findViewById(R.id.tvNextStop);
        tvETA = findViewById(R.id.tvETA);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        btnToggleLocation = findViewById(R.id.btnToggleLocation);

        // Initialize the new buttons
        btnLogout = findViewById(R.id.btnLogout);
        btnProfile = findViewById(R.id.btnProfile);

        tvNextStop.setText("Next: Loading...");
        tvETA.setText("ETA: -- min");
        tvLocationStatus.setText("📍 Offline");
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        // Remove default title and enable custom layout
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupClickListeners() {
        // Logout button click listener (Top Left)
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutConfirmationDialog();
            }
        });

        // Profile button click listener (Top Right)
        btnProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDriverProfile();
            }
        });

        // Location toggle button listener
        btnToggleLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLocationSharing();
            }
        });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        performLogout();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        Log.d(TAG, "=== PERFORMING LOGOUT ===");

        // 1. Stop location services if active
        if (isSharingLocation) {
            stopLocationSharing();
        } else {
            setDriverOffline();
        }

        // 2. Clear user session/data
        clearUserData();

        // 3. Sign out from Firebase
        mAuth.signOut();

        // 4. Navigate to login screen
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    private void clearUserData() {
        // Clear any local data if needed
        // SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        // preferences.edit().clear().apply();
        Log.d(TAG, "User data cleared");
    }

    private void openDriverProfile() {
        Log.d(TAG, "Opening driver profile...");
        Intent intent = new Intent(MainActivity.this, DriverProfileActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void clearCurrentNotificationSpam() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
            Log.d(TAG, "Cleared all current notification spam");
        }
    }

    private void setupMapFragment() {
        Log.d(TAG, "Setting up map fragment...");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
            Log.d(TAG, "Map fragment found, waiting for onMapReady...");
        } else {
            Log.e(TAG, "Map fragment is NULL!");
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.d(TAG, "Location result is null");
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude() +
                            " Accuracy: " + location.getAccuracy() + "m");

                    lastKnownLocation = location;
                    lastLocationUpdateTime = System.currentTimeMillis();

                    updateDriverLocation(location);
                    updateMapWithCurrentLocation(location);

                    // Update location status
                    runOnUiThread(() -> {
                        tvLocationStatus.setText("📍 Tracking");
                    });

                    // PERFORM SAFETY CHECKS ON EVERY LOCATION UPDATE
                    performSafetyChecks(location);

                    // Throttle ETA updates to avoid excessive API calls
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastETACalculationTime > MIN_ETA_UPDATE_INTERVAL) {
                        if (currentRoute != null && currentRoute.getStops() != null) {
                            updateETAAndNextStop(location);
                        }
                        lastETACalculationTime = currentTime;
                    }
                }
            }
        };
    }

    // ==================== SAFETY DETECTION METHODS ====================

    /**
     * Perform all safety checks on location update
     */
    private void performSafetyChecks(Location location) {
        // 1. Speed check
        checkSpeedViolation(location);

        // 2. Route deviation check
        checkRouteDeviation(location);

        // 3. Prolonged stop check
        checkProlongedStop(location);
    }

    /**
     * Check if bus is exceeding speed limit
     */
    private void checkSpeedViolation(Location location) {
        if (location.hasSpeed()) {
            float speedKmph = location.getSpeed() * 3.6f; // m/s to km/h
            if (speedKmph > MAX_SPEED_KMPH) {
                String message = "Speed violation: " + String.format("%.1f", speedKmph) + " km/h";
                triggerSafetyAlert("SPEED_VIOLATION", message, "HIGH");
            }
        }
    }

    /**
     * Check if bus has deviated from assigned route
     */
    private void checkRouteDeviation(Location location) {
        if (fullRoutePath == null || fullRoutePath.isEmpty()) return;

        float minDistance = Float.MAX_VALUE;
        for (LatLng routePoint : fullRoutePath) {
            float[] results = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    routePoint.latitude, routePoint.longitude, results);
            minDistance = Math.min(minDistance, results[0]);
        }

        if (minDistance > ROUTE_DEVIATION_THRESHOLD) {
            String message = "Bus has deviated from assigned route by " +
                    String.format("%.0f", minDistance) + " meters";
            triggerSafetyAlert("ROUTE_DEVIATION", message, "HIGH");
        }
    }

    /**
     * Check if bus has been stopped for too long
     */
    private void checkProlongedStop(Location location) {
        if (location.hasSpeed() && location.getSpeed() < 1.0f) { // Stopped (less than 1 m/s)
            if (!isStopped) {
                isStopped = true;
                stoppedSince = System.currentTimeMillis();
            } else {
                long stoppedDuration = System.currentTimeMillis() - stoppedSince;
                if (stoppedDuration > PROLONGED_STOP_THRESHOLD) {
                    String message = "Bus stopped for " + (stoppedDuration / 60000) + " minutes";
                    triggerSafetyAlert("PROLONGED_STOP", message, "MEDIUM");
                    // Reset to avoid repeated alerts for same stop
                    stoppedSince = System.currentTimeMillis() + 300000; // Add 5 minutes to prevent immediate re-alert
                }
            }
        } else {
            isStopped = false;
            stoppedSince = 0;
        }
    }

    /**
     * Trigger safety alert and send notification
     */
    private void triggerSafetyAlert(String alertType, String message, String severity) {

        // Only trigger if we're sharing location
        if (!isSharingLocation) return;

        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        Long lastAlertTime = lastAlertTimeMap.get(alertType);

        if (lastAlertTime != null && (currentTime - lastAlertTime) < ALERT_COOLDOWN_MS) {
            Log.d(TAG, "Alert " + alertType + " is in cooldown, skipping");
            return;
        }

        // Update last alert time
        lastAlertTimeMap.put(alertType, currentTime);

        SafetyAlert alert = new SafetyAlert();
        alert.setAlertType(alertType);
        alert.setMessage(message);
        alert.setBusId(currentBus != null ? currentBus.getBusId() : "unknown");
        alert.setBusNumber(currentBus != null ? currentBus.getBusNumber() : "Unknown");
        alert.setRouteId(currentBus != null ? currentBus.getRouteId() : "unknown");
        alert.setTimestamp(com.google.firebase.Timestamp.now());
        alert.setSeverity(severity);

        // Send the alert via notification service
        notificationService.sendSafetyAlertNotification(alert);

        // Show immediate alert to driver
        Toast.makeText(this, "🚨 " + message, Toast.LENGTH_LONG).show();

        Log.d(TAG, "Safety alert triggered: " + alertType + " - " + message);
    }

    // ==================== ENHANCED ETA METHODS ====================

    private void updateETAAndNextStop(Location currentLocation) {
        if (currentRoute == null || currentRoute.getStops() == null || currentRoute.getStops().isEmpty()) {
            return;
        }

        List<Map<String, Object>> stops = currentRoute.getStops();
        int nextStopIndex = findNextStopIndex(currentLocation, stops);

        if (nextStopIndex != -1) {
            Map<String, Object> nextStop = stops.get(nextStopIndex);
            String nextStopName = getStopName(nextStop);

            Double nextStopLat = getCoordinateValue(nextStop.get("latitude"));
            Double nextStopLng = getCoordinateValue(nextStop.get("longitude"));

            if (nextStopLat != null && nextStopLng != null) {
                calculateRealTimeETA(currentLocation, nextStopLat, nextStopLng, nextStopName, nextStopIndex);
                currentStopIndex = nextStopIndex;
            }
        } else {
            updateUIWithFinalStop();
        }
    }

    private int findNextStopIndex(Location currentLocation, List<Map<String, Object>> stops) {
        for (int i = currentStopIndex; i < stops.size(); i++) {
            Map<String, Object> stop = stops.get(i);
            Double stopLat = getCoordinateValue(stop.get("latitude"));
            Double stopLng = getCoordinateValue(stop.get("longitude"));

            if (stopLat != null && stopLng != null) {
                Location stopLocation = new Location("");
                stopLocation.setLatitude(stopLat);
                stopLocation.setLongitude(stopLng);

                float distance = currentLocation.distanceTo(stopLocation);
                Log.d(TAG, "Distance to stop " + i + ": " + distance + "m");

                if (distance > 100) {
                    return i;
                } else {
                    Log.d(TAG, "✓ Reached stop " + i);
                }
            }
        }
        return -1;
    }

    private void calculateRealTimeETA(Location currentLocation, double nextStopLat, double nextStopLng, String nextStopName, int stopIndex) {
        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        LatLng nextStopLatLng = new LatLng(nextStopLat, nextStopLng);

        directionsService.getRouteDirections(currentLatLng, nextStopLatLng, new DirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReady(List<LatLng> routePoints, long durationMinutes) {
                Log.d(TAG, "Google Maps ETA: " + durationMinutes + " minutes");

                // USE ENHANCED ETA SERVICE
                if (enhancedETAService != null && currentBus != null) {
                    enhancedETAService.calculateEnhancedETA(
                            currentLatLng,
                            nextStopLatLng,
                            durationMinutes,
                            currentBus.getBusId(),
                            currentBus.getBusNumber(),
                            currentBus.getRouteId(),
                            nextStopName,
                            new EnhancedETAService.EnhancedETACallback() {
                                @Override
                                public void onEnhancedETAReady(long enhancedETA, String confidence, List<String> factors) {
                                    Log.d(TAG, "Enhanced ETA: " + enhancedETA + "min (Confidence: " + confidence + ")");

                                    runOnUiThread(() -> {
                                        currentNextStop = nextStopName;
                                        currentETAMinutes = enhancedETA;

                                        tvNextStop.setText("Next: " + nextStopName);
                                        tvETA.setText("ETA: " + enhancedETA + " min");

                                        // Show factors indicator
                                        if (!factors.isEmpty()) {
                                            tvETA.append(" *");
                                        }

                                        // Draw direction to next stop
                                        drawDirectionToNextStop(currentLocation, nextStopLatLng, nextStopName);
                                        updateLiveLocationWithETA(nextStopName, enhancedETA, stopIndex);
                                    });
                                }

                                @Override
                                public void onETAFailed(String error) {
                                    Log.e(TAG, "Enhanced ETA failed: " + error);
                                    // Fallback to regular ETA
                                    runOnUiThread(() -> {
                                        currentNextStop = nextStopName;
                                        currentETAMinutes = durationMinutes;

                                        tvNextStop.setText("Next: " + nextStopName);
                                        tvETA.setText("ETA: " + durationMinutes + " min");

                                        drawDirectionToNextStop(currentLocation, nextStopLatLng, nextStopName);
                                        updateLiveLocationWithETA(nextStopName, durationMinutes, stopIndex);
                                    });
                                }
                            }
                    );
                } else {
                    // Fallback if enhanced service not available
                    runOnUiThread(() -> {
                        currentNextStop = nextStopName;
                        currentETAMinutes = durationMinutes;

                        tvNextStop.setText("Next: " + nextStopName);
                        tvETA.setText("ETA: " + durationMinutes + " min");

                        drawDirectionToNextStop(currentLocation, nextStopLatLng, nextStopName);
                        updateLiveLocationWithETA(nextStopName, durationMinutes, stopIndex);
                    });
                }
            }

            @Override
            public void onDirectionsFailed(String error) {
                Log.e(TAG, "Failed to get ETA: " + error);
                calculateFallbackETA(currentLocation, nextStopLatLng, nextStopName, stopIndex);
            }
        });
    }

    private void calculateFallbackETA(Location currentLocation, LatLng nextStop, String nextStopName, int stopIndex) {
        Location nextStopLocation = new Location("");
        nextStopLocation.setLatitude(nextStop.latitude);
        nextStopLocation.setLongitude(nextStop.longitude);

        float distance = currentLocation.distanceTo(nextStopLocation);
        float distanceInKm = distance / 1000;
        float averageBusSpeed = 20.0f;
        long basicETA = Math.max(1, (long) ((distanceInKm / averageBusSpeed) * 60));

        // Use enhanced ETA service even for fallback
        if (enhancedETAService != null && currentBus != null) {
            enhancedETAService.calculateEnhancedETA(
                    new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()),
                    nextStop,
                    basicETA,
                    currentBus.getBusId(),
                    currentBus.getBusNumber(),
                    currentBus.getRouteId(),
                    nextStopName,
                    new EnhancedETAService.EnhancedETACallback() {
                        @Override
                        public void onEnhancedETAReady(long enhancedETA, String confidence, List<String> factors) {
                            runOnUiThread(() -> {
                                currentNextStop = nextStopName;
                                currentETAMinutes = enhancedETA;

                                tvNextStop.setText("Next: " + nextStopName);
                                tvETA.setText("ETA: " + enhancedETA + " min");

                                drawStraightLineDirection(currentLocation, nextStop, nextStopName);
                                updateLiveLocationWithETA(nextStopName, enhancedETA, stopIndex);
                            });
                        }

                        @Override
                        public void onETAFailed(String error) {
                            // Use basic ETA as fallback
                            runOnUiThread(() -> {
                                currentNextStop = nextStopName;
                                currentETAMinutes = basicETA;

                                tvNextStop.setText("Next: " + nextStopName);
                                tvETA.setText("ETA: " + basicETA + " min");

                                drawStraightLineDirection(currentLocation, nextStop, nextStopName);
                                updateLiveLocationWithETA(nextStopName, basicETA, stopIndex);
                            });
                        }
                    }
            );
        } else {
            // Direct fallback
            runOnUiThread(() -> {
                currentNextStop = nextStopName;
                currentETAMinutes = basicETA;

                tvNextStop.setText("Next: " + nextStopName);
                tvETA.setText("ETA: " + basicETA + " min");

                drawStraightLineDirection(currentLocation, nextStop, nextStopName);
                updateLiveLocationWithETA(nextStopName, basicETA, stopIndex);
            });
        }
    }

    private void updateUIWithFinalStop() {
        if (currentRoute != null && currentRoute.getStops() != null && !currentRoute.getStops().isEmpty()) {
            String finalStopName = getStopName(currentRoute.getStops().get(currentRoute.getStops().size() - 1));
            runOnUiThread(() -> {
                currentNextStop = finalStopName;
                currentETAMinutes = 0L;

                tvNextStop.setText("Final: " + finalStopName);
                tvETA.setText("Arrived");

                // Clear direction when arrived
                if (directionPolyline != null) {
                    directionPolyline.remove();
                    directionPolyline = null;
                }
                if (nextStopMarker != null) {
                    nextStopMarker.remove();
                    nextStopMarker = null;
                }

                updateLiveLocationWithETA(finalStopName, 0, currentRoute.getStops().size() - 1);
            });
        }
    }

    // ==================== EXISTING METHODS (Keep these as they are) ====================

    private void loadDriverData() {
        Log.d(TAG, "=== STEP 1: Loading driver data ===");
        db.collection("users").document(currentDriverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        currentUser = task.getResult().toObject(User.class);
                        if (currentUser != null) {
                            Log.d(TAG, "✓ Driver: " + currentUser.getName());
                            tvDriverName.setText("Driver: " + currentUser.getName());

                            if (currentUser.getBusId() != null && !currentUser.getBusId().isEmpty()) {
                                Log.d(TAG, "✓ Bus assigned: " + currentUser.getBusId());
                                loadBusData(currentUser.getBusId());
                            } else {
                                Log.d(TAG, "✗ No bus assigned");
                                tvBusInfo.setText("Bus: Not Assigned");
                                tvRouteInfo.setText("Route: Not Assigned");
                                btnToggleLocation.setEnabled(true);
                                Toast.makeText(this, "No bus assigned. You can still share location.", Toast.LENGTH_LONG).show();
                                showDefaultMapView();
                            }
                        }
                    } else {
                        Log.e(TAG, "✗ Error loading driver data");
                        showDefaultMapView();
                    }
                });
    }

    private void loadBusData(String busId) {
        Log.d(TAG, "=== STEP 2: Loading bus data ===");
        db.collection("buses").document(busId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        currentBus = task.getResult().toObject(Bus.class);
                        if (currentBus != null) {
                            Log.d(TAG, "✓ Bus: " + currentBus.getBusNumber());
                            tvBusInfo.setText("Bus: " + currentBus.getBusNumber());

                            if (currentBus.getRouteId() != null && !currentBus.getRouteId().isEmpty()) {
                                Log.d(TAG, "✓ Route assigned: " + currentBus.getRouteId());
                                loadRouteData(currentBus.getRouteId());
                            } else {
                                Log.d(TAG, "✗ No route assigned");
                                tvRouteInfo.setText("Route: Not Assigned");
                                showDefaultMapView();
                            }
                        }
                    } else {
                        Log.e(TAG, "✗ Error loading bus data");
                        showDefaultMapView();
                    }
                });
    }

    private void loadRouteData(String routeId) {
        Log.d(TAG, "=== STEP 3: Loading route data ===");
        db.collection("routes").document(routeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        currentRoute = task.getResult().toObject(Route.class);
                        if (currentRoute != null) {
                            Log.d(TAG, "✓ Route: " + currentRoute.getRouteName());
                            tvRouteInfo.setText("Route: " + currentRoute.getRouteName());

                            isRouteLoaded = true;

                            if (isMapReady) {
                                drawRouteOnMap();
                                focusMapOnRoute();
                            }
                        } else {
                            showDefaultMapView();
                        }
                    } else {
                        Log.e(TAG, "✗ Error loading route data");
                        showDefaultMapView();
                    }
                });
    }

    private void drawRouteOnMap() {
        Log.d(TAG, "=== DRAWING ENHANCED ROUTE ON MAP ===");

        if (currentRoute == null || currentRoute.getStops() == null || mMap == null) {
            Log.e(TAG, "✗ Cannot draw route: missing data");
            return;
        }

        // Clear existing map elements
        mMap.clear();
        stopMarkers.clear();
        fullRoutePath.clear();

        if (routePolyline != null) {
            routePolyline.remove();
        }
        if (directionPolyline != null) {
            directionPolyline.remove();
        }

        List<Map<String, Object>> stops = currentRoute.getStops();
        List<LatLng> stopLatLngs = new ArrayList<>();

        // Extract and validate stop coordinates
        for (int i = 0; i < stops.size(); i++) {
            Map<String, Object> stop = stops.get(i);
            String stopName = getStopName(stop);

            Double latitude = getCoordinateValue(stop.get("latitude"));
            Double longitude = getCoordinateValue(stop.get("longitude"));

            if (latitude == null || longitude == null) {
                Log.e(TAG, "⚠ Missing coordinates for stop: " + stopName);
                continue;
            }

            // Validate coordinates are within Nepal roughly
            if (latitude < 26 || latitude > 30 || longitude < 80 || longitude > 88) {
                Log.e(TAG, "⚠ Suspicious coordinates for stop: " + stopName);
                continue;
            }

            LatLng stopLatLng = new LatLng(latitude, longitude);
            stopLatLngs.add(stopLatLng);
            addStopMarker(stopLatLng, stopName, i, stops.size());
        }

        if (stopLatLngs.isEmpty()) {
            Log.e(TAG, "✗ No valid stops found!");
            showDefaultMapView();
            return;
        }

        Log.d(TAG, "Valid stops found: " + stopLatLngs.size());

        // Get full route with waypoints using Directions API
        if (stopLatLngs.size() >= 2) {
            directionsService.getRouteWithWaypoints(stopLatLngs, new DirectionsService.DirectionsCallback() {
                @Override
                public void onDirectionsReady(List<LatLng> routePoints, long totalDuration) {
                    Log.d(TAG, "✓ Route drawn with " + routePoints.size() + " points");
                    fullRoutePath.clear();
                    fullRoutePath.addAll(routePoints);

                    runOnUiThread(() -> {
                        drawRoadRoute(routePoints);
                        focusMapOnRoute();
                        Toast.makeText(MainActivity.this,
                                "Route '" + currentRoute.getRouteName() + "' loaded",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onDirectionsFailed(String error) {
                    Log.e(TAG, "✗ Directions failed: " + error);
                    runOnUiThread(() -> {
                        drawStraightLineRoute(stopLatLngs);
                        focusMapOnRoute();
                    });
                }
            });
        } else {
            drawStraightLineRoute(stopLatLngs);
            focusMapOnRoute();
        }
    }

    private Double getCoordinateValue(Object coordinate) {
        if (coordinate == null) return null;

        if (coordinate instanceof Double) return (Double) coordinate;
        if (coordinate instanceof Long) return ((Long) coordinate).doubleValue();
        if (coordinate instanceof Integer) return ((Integer) coordinate).doubleValue();
        if (coordinate instanceof String) {
            try {
                return Double.parseDouble((String) coordinate);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String getStopName(Map<String, Object> stop) {
        if (stop.containsKey("stopName") && stop.get("stopName") != null) {
            return stop.get("stopName").toString();
        }
        if (stop.containsKey("name") && stop.get("name") != null) {
            return stop.get("name").toString();
        }
        return "Unknown Stop";
    }

    private void addStopMarker(LatLng position, String stopName, int index, int totalStops) {
        String stopLabel = "Stop " + (index + 1) + ": " + stopName;

        float hue = BitmapDescriptorFactory.HUE_BLUE;
        if (index == 0) hue = BitmapDescriptorFactory.HUE_GREEN;
        else if (index == totalStops - 1) hue = BitmapDescriptorFactory.HUE_RED;

        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(stopLabel)
                .snippet("Route: " + currentRoute.getRouteName())
                .icon(BitmapDescriptorFactory.defaultMarker(hue));

        Marker marker = mMap.addMarker(markerOptions);
        if (marker != null) {
            stopMarkers.put(stopName, marker);
        }
    }

    private void drawRoadRoute(List<LatLng> routePoints) {
        if (routePolyline != null) {
            routePolyline.remove();
        }

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(12f)
                .color(0xFF4285F4) // Blue for main route
                .geodesic(false);

        routePolyline = mMap.addPolyline(polylineOptions);
    }

    private void drawStraightLineRoute(List<LatLng> stopLatLngs) {
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(stopLatLngs)
                .width(8f)
                .color(0xFF888888)
                .geodesic(true);

        routePolyline = mMap.addPolyline(polylineOptions);
    }

    private void focusMapOnRoute() {
        if (mMap == null) return;

        List<LatLng> pointsToFocus = !fullRoutePath.isEmpty() ? fullRoutePath : routePath;
        if (pointsToFocus.isEmpty()) return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : pointsToFocus) {
            builder.include(point);
        }

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        } catch (Exception e) {
            if (!pointsToFocus.isEmpty()) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pointsToFocus.get(0), 14));
            }
        }
    }

    private void showDefaultMapView() {
        if (mMap != null) {
            LatLng kathmandu = new LatLng(27.7172, 85.3240);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(kathmandu, 12));
        }
    }

    private void drawDirectionToNextStop(Location currentLocation, LatLng nextStopLatLng, String nextStopName) {
        if (mMap == null) return;

        // Clear previous direction
        if (directionPolyline != null) {
            directionPolyline.remove();
        }

        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

        directionsService.getRouteDirections(currentLatLng, nextStopLatLng, new DirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReady(List<LatLng> routePoints, long durationMinutes) {
                runOnUiThread(() -> {
                    // Draw direction polyline with distinct color
                    PolylineOptions directionOptions = new PolylineOptions()
                            .addAll(routePoints)
                            .width(10f)
                            .color(0xFF00C853) // Green for direction
                            .geodesic(false);

                    directionPolyline = mMap.addPolyline(directionOptions);

                    updateNextStopMarker(nextStopLatLng, nextStopName, durationMinutes);

                    Log.d(TAG, "✓ Direction drawn to next stop");
                });
            }

            @Override
            public void onDirectionsFailed(String error) {
                Log.e(TAG, "Failed to get direction: " + error);
                runOnUiThread(() -> {
                    drawStraightLineDirection(currentLocation, nextStopLatLng, nextStopName);
                });
            }
        });
    }

    private void drawStraightLineDirection(Location currentLocation, LatLng nextStop, String nextStopName) {
        if (mMap == null) return;

        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

        PolylineOptions directionOptions = new PolylineOptions()
                .add(currentLatLng)
                .add(nextStop)
                .width(6f)
                .color(0xFFFF9800) // Orange for fallback direction
                .geodesic(true);

        directionPolyline = mMap.addPolyline(directionOptions);

        updateNextStopMarker(nextStop, nextStopName, currentETAMinutes);
    }

    private void updateNextStopMarker(LatLng stopLatLng, String stopName, long etaMinutes) {
        if (nextStopMarker != null) {
            nextStopMarker.remove();
        }

        MarkerOptions markerOptions = new MarkerOptions()
                .position(stopLatLng)
                .title("Next Stop: " + stopName)
                .snippet("ETA: " + etaMinutes + " min")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));

        nextStopMarker = mMap.addMarker(markerOptions);
    }

    private void updateDriverLocation(Location location) {
        if (currentDriverId == null || currentUser == null) return;

        if (currentBus != null) {
            updateBusCollection(location);
        }
        updateLiveLocationsCollection(location);
    }

    private void updateBusCollection(Location location) {
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("accuracy", location.getAccuracy());

        Map<String, Object> busUpdates = new HashMap<>();
        busUpdates.put("currentLocation", locationData);
        busUpdates.put("lastUpdated", System.currentTimeMillis());
        busUpdates.put("isActive", true);
        busUpdates.put("currentStatus", "on_route");

        if (!currentNextStop.isEmpty()) {
            busUpdates.put("nextStop", currentNextStop);
            busUpdates.put("etaToNextStop", currentETAMinutes);
        }

        db.collection("buses").document(currentBus.getBusId())
                .set(busUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Bus location updated");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Bus update failed: " + e.getMessage());
                });
    }

    private void updateLiveLocationsCollection(Location location) {
        Map<String, Object> liveLocation = new HashMap<>();
        liveLocation.put("driverId", currentDriverId);
        liveLocation.put("driverName", currentUser.getName());
        liveLocation.put("latitude", location.getLatitude());
        liveLocation.put("longitude", location.getLongitude());
        liveLocation.put("timestamp", Timestamp.now());
        liveLocation.put("currentStatus", "on_route");
        liveLocation.put("accuracy", location.getAccuracy());

        if (currentBus != null) {
            liveLocation.put("busId", currentBus.getBusId());
            liveLocation.put("busNumber", currentBus.getBusNumber());
            liveLocation.put("routeId", currentBus.getRouteId());
        }

        if (!currentNextStop.isEmpty()) {
            liveLocation.put("nextStop", currentNextStop);
            liveLocation.put("etaToNextStop", currentETAMinutes);
            liveLocation.put("currentStopIndex", currentStopIndex);
        }

        db.collection("live_locations").document(currentDriverId)
                .set(liveLocation)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Live location updated");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Live location update failed: " + e.getMessage());
                });
    }

    private void updateLiveLocationWithETA(String nextStop, long etaMinutes, int stopIndex) {
        if (currentDriverId == null) return;

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("nextStop", nextStop);
        updateData.put("etaToNextStop", etaMinutes);
        updateData.put("currentStopIndex", stopIndex);
        updateData.put("timestamp", Timestamp.now());

        db.collection("live_locations").document(currentDriverId)
                .update(updateData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ETA update failed: " + e.getMessage());
                });
    }

    private void updateMapWithCurrentLocation(Location location) {
        if (mMap == null) return;

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (currentLocationMarker == null) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(currentLatLng)
                    .title("Current Location")
                    .snippet("Bus: " + (currentBus != null ? currentBus.getBusNumber() : "Not Assigned"))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
            currentLocationMarker = mMap.addMarker(markerOptions);
        } else {
            currentLocationMarker.setPosition(currentLatLng);
            if (!currentNextStop.isEmpty()) {
                currentLocationMarker.setSnippet("Bus: " + (currentBus != null ? currentBus.getBusNumber() : "Not Assigned") +
                        " | Next: " + currentNextStop + " in " + currentETAMinutes + " min");
            }
        }
    }

    private void toggleLocationSharing() {
        if (!isSharingLocation) {
            startLocationSharing();
        } else {
            stopLocationSharing();
        }
    }

    private void startLocationSharing() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
            return;
        }

        Log.d(TAG, "✓ Starting location sharing...");

        // Reset tracking state
        currentStopIndex = 0;
        currentNextStop = "";
        currentETAMinutes = 0;

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(15000) // 15 seconds
                .setFastestInterval(10000) // 10 seconds
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

            if (currentBus != null) {
                updateBusStatus(true);
            }

            setDriverOnline();
            isSharingLocation = true;
            updateLocationButtonState();
            Toast.makeText(this, "Location sharing started", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLocationSharing() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (Exception e) {
            Log.d(TAG, "Error removing location updates: " + e.getMessage());
        }

        if (currentBus != null) {
            updateBusStatus(false);
        }

        setDriverOffline();
        // Clear location data
        db.collection("live_locations").document(currentDriverId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Live location cleared");
                });

        runOnUiThread(() -> {
            tvNextStop.setText("Next: -");
            tvETA.setText("ETA: -");
            tvLocationStatus.setText("📍 Offline");

            // Clear direction visuals
            if (directionPolyline != null) {
                directionPolyline.remove();
                directionPolyline = null;
            }
            if (nextStopMarker != null) {
                nextStopMarker.remove();
                nextStopMarker = null;
            }
        });

        isSharingLocation = false;
        updateLocationButtonState();
        Toast.makeText(this, "Location sharing stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateBusStatus(boolean isActive) {
        if (currentBus == null) return;

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("isActive", isActive);

        db.collection("buses").document(currentBus.getBusId())
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Bus status updated");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Bus status update failed: " + e.getMessage());
                });
    }

    private void setDriverOnline() {
        if (currentDriverId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", true);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(currentDriverId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Driver set to ONLINE");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Failed to set driver online", e);
                });
    }

    private void setDriverOffline() {
        if (currentDriverId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", false);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(currentDriverId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Driver set to OFFLINE");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Failed to set driver offline", e);
                });
    }

    private void updateLocationButtonState() {
        runOnUiThread(() -> {
            if (isSharingLocation) {
                btnToggleLocation.setText("🛑 Stop Sharing Location");
                btnToggleLocation.setIconResource(R.drawable.ic_location_off);
            } else {
                btnToggleLocation.setText("🚀 Start Sharing Location");
                btnToggleLocation.setIconResource(R.drawable.ic_location);
            }
            btnToggleLocation.setEnabled(true);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✓ Location permissions granted");
                // Enable my location layer if map is ready
                if (mMap != null && hasLocationPermissions()) {
                    try {
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException enabling location layer: " + e.getMessage());
                    }
                }
                startLocationSharing();
            } else {
                Log.w(TAG, "✗ Location permissions denied");
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "=== MAP IS READY ===");
        mMap = googleMap;
        isMapReady = true;

        // Configure map controls
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Enable my location layer if permissions are granted
        if (hasLocationPermissions()) {
            try {
                mMap.setMyLocationEnabled(true);
                Log.d(TAG, "✓ My location layer enabled");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "Location permissions not granted - my location button may not work");
        }

        // Draw route if data is available
        if (isRouteLoaded || currentRoute != null) {
            Log.d(TAG, "Drawing route on map ready...");
            drawRouteOnMap();
            focusMapOnRoute();
        } else {
            showDefaultMapView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isSharingLocation) {
            stopLocationSharing();
        } else {
            // Ensure driver is offline when app closes
            setDriverOffline();
        }
    }
}