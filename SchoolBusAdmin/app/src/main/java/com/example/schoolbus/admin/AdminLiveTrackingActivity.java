package com.example.schoolbus.admin;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.schoolbus.admin.adapters.AdminBusAdapter;
import com.example.schoolbus.admin.models.Bus;
import com.example.schoolbus.admin.models.Route;
import com.example.schoolbus.admin.services.AdminDirectionsService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.*;
import java.util.*;

public class AdminLiveTrackingActivity extends AppCompatActivity implements
        OnMapReadyCallback, AdminBusAdapter.OnBusClickListener {

    private static final String TAG = "AdminLiveTracking";

    // Views
    private MaterialToolbar toolbar;
    private Spinner routeSpinner;
    private TextView tvRouteInfo, tvActiveCount, tvMapHint, tvAlertsBadge;
    private View tvNoBuses;
    private RecyclerView recyclerBuses;
    private GoogleMap mMap;
    private FirebaseFirestore db;

    // Data
    private List<Route> routes = new ArrayList<>();
    private List<Bus> busesList = new ArrayList<>();
    private AdminBusAdapter busAdapter;
    private Route currentRoute;
    private Bus selectedBus;

    // Maps and Listeners
    private Map<String, Marker> busMarkers = new HashMap<>();
    private Map<String, String> busDriverNames = new HashMap<>();
    private Map<String, String> busDriverPhones = new HashMap<>();
    private List<ListenerRegistration> activeListeners = new ArrayList<>();
    private Polyline routePolyline;
    private Polyline directionPolyline;
    private Marker nextStopMarker;

    // Services
    private AdminDirectionsService directionsService;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Swipe Refresh
    private SwipeRefreshLayout swipeRefreshLayout;

    // ETA Retry Mechanism - PRESERVED FROM ORIGINAL
    private Map<String, Integer> etaRetryCounts = new HashMap<>();
    private static final int MAX_ETA_RETRIES = 3;
    private static final long ETA_RETRY_DELAY_MS = 5000;
    private Handler retryHandler = new Handler(Looper.getMainLooper());

    // Auto-focus - PRESERVED FROM ORIGINAL
    private boolean shouldAutoFocusOnFirstBus = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_live_tracking);

        db = FirebaseFirestore.getInstance();
        directionsService = AdminDirectionsService.getInstance(this);

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupMapFragment();
        setupSwipeRefresh();
        loadRoutes();
        loadAlertsBadgeCount();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        routeSpinner = findViewById(R.id.routeSpinner);
        tvRouteInfo = findViewById(R.id.tv_route_info);
        tvActiveCount = findViewById(R.id.tv_active_count);
        tvMapHint = findViewById(R.id.tv_map_hint);
        tvNoBuses = findViewById(R.id.tv_no_buses);
        recyclerBuses = findViewById(R.id.recycler_buses);
        tvAlertsBadge = findViewById(R.id.tv_alerts_badge);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        // Profile button
        ImageButton btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });

        // Alerts badge
        MaterialCardView cardAlertsBadge = findViewById(R.id.cardAlertsBadge);
        cardAlertsBadge.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminNotificationHistoryActivity.class);
            startActivity(intent);
        });
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(""); // REMOVE THE "Admin Portal" TEXT
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Reset auto-focus flag on refresh - PRESERVED FROM ORIGINAL
            shouldAutoFocusOnFirstBus = true;

            loadRoutes();
            loadAlertsBadgeCount();

            if (currentRoute != null) {
                loadBusesForRoute(currentRoute.getRouteId());
            } else {
                loadAllActiveBuses();
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(this, "Data refreshed", Toast.LENGTH_SHORT).show();
            }, 2000);
        });

        swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this, R.color.primary_color),
                ContextCompat.getColor(this, R.color.secondary_color),
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
        );

        // FIX: Prevent SwipeRefreshLayout from interfering with bottom sheet dragging
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            // Disable swipe-to-refresh when the bottom sheet is expanded or being dragged
            View bottomSheet = findViewById(R.id.card_buses_section);
            if (bottomSheet != null) {
                // Get the bottom sheet behavior
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);

                // Disable swipe refresh when bottom sheet is expanded or dragging
                return behavior.getState() == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED ||
                        behavior.getState() == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING ||
                        behavior.getState() == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING;
            }
            return false;
        });
    }

    // ALERTS BADGE - PRESERVED FROM ORIGINAL
    private void loadAlertsBadgeCount() {
        db.collection("safety_alerts")
                .whereEqualTo("resolved", false)
                .whereEqualTo("acknowledged", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed for alerts badge", error);
                        return;
                    }

                    int unreadCount = value != null ? value.size() : 0;
                    Log.d(TAG, "Badge update - Unread alerts: " + unreadCount);
                    updateAlertsBadge(unreadCount);

                    if (unreadCount > 0 && value != null) {
                        checkForNewAlerts(value);
                    }
                });
    }

    private void checkForNewAlerts(QuerySnapshot snapshot) {
        long currentTime = System.currentTimeMillis();
        long fiveMinutesAgo = currentTime - (5 * 60 * 1000);

        for (QueryDocumentSnapshot doc : snapshot) {
            com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
            if (timestamp != null) {
                long alertTime = timestamp.toDate().getTime();
                if (alertTime > fiveMinutesAgo) {
                    showNewAlertNotification(snapshot.size());
                    break;
                }
            }
        }
    }

    private void showNewAlertNotification(int alertCount) {
        try {
            String message = alertCount + " safety alert" + (alertCount > 1 ? "s" : "") + " require attention";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "safety_alerts")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🚨 SchoolBus Safety Alert")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                Log.d(TAG, "Notification shown for " + alertCount + " alerts");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage());
        }
    }

    private void updateAlertsBadge(int count) {
        MaterialCardView cardAlertsBadge = findViewById(R.id.cardAlertsBadge);

        if (count > 0) {
            tvAlertsBadge.setText(String.valueOf(count));
            cardAlertsBadge.setVisibility(View.VISIBLE);

            if (count > 5) {
                flashBadge();
            }
        } else {
            cardAlertsBadge.setVisibility(View.GONE);
        }
    }

    private void flashBadge() {
        final int[] flashCount = {0};
        final int maxFlashes = 6;

        Runnable flashRunnable = new Runnable() {
            @Override
            public void run() {
                if (flashCount[0] < maxFlashes) {
                    if (tvAlertsBadge.getVisibility() == View.VISIBLE) {
                        tvAlertsBadge.setVisibility(View.INVISIBLE);
                    } else {
                        tvAlertsBadge.setVisibility(View.VISIBLE);
                    }
                    flashCount[0]++;
                    retryHandler.postDelayed(this, 500);
                } else {
                    tvAlertsBadge.setVisibility(View.VISIBLE);
                }
            }
        };

        retryHandler.post(flashRunnable);
    }

    private boolean checkLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mMap != null) {
                    try {
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Location permission not granted", e);
                    }
                }
            } else {
                Toast.makeText(this, "Location permission required for maps", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupRecyclerView() {
        busAdapter = new AdminBusAdapter(busesList, busDriverNames, this);
        recyclerBuses.setLayoutManager(new LinearLayoutManager(this));
        recyclerBuses.setAdapter(busAdapter);
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void loadRoutes() {
        db.collection("routes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        routes.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Route route = document.toObject(Route.class);
                            route.setRouteId(document.getId());
                            routes.add(route);
                        }
                        updateRouteSpinner();
                    } else {
                        Log.e(TAG, "Error loading routes: " + task.getException());
                    }
                });
    }

    private void updateRouteSpinner() {
        List<String> routeNames = new ArrayList<>();
        routeNames.add("All Routes");

        for (Route route : routes) {
            routeNames.add(route.getRouteName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, routeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        routeSpinner.setAdapter(adapter);

        routeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Reset auto-focus when route changes - PRESERVED FROM ORIGINAL
                shouldAutoFocusOnFirstBus = true;

                if (position == 0) {
                    currentRoute = null;
                    tvRouteInfo.setText("Showing all active buses across all routes");
                    loadAllActiveBuses();
                    clearRouteFromMap();
                } else {
                    currentRoute = routes.get(position - 1);
                    tvRouteInfo.setText("Route: " + currentRoute.getRouteName());
                    loadBusesForRoute(currentRoute.getRouteId());
                    drawRouteOnMap(currentRoute);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadAllActiveBuses() {
        clearActiveListeners();
        busesList.clear();
        busDriverNames.clear();
        busDriverPhones.clear();
        busAdapter.notifyDataSetChanged();

        ListenerRegistration listener = db.collection("buses")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    busesList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot doc : value) {
                            Bus bus = doc.toObject(Bus.class);
                            bus.setBusId(doc.getId());
                            busesList.add(bus);

                            Log.d(TAG, "Loaded bus: " + bus.getBusNumber() +
                                    " - Location: " + bus.getLatitude() + "," + bus.getLongitude() +
                                    " - Active: " + bus.isActive());

                            loadDriverInfo(bus);
                            listenToBusUpdates(bus);

                            // Calculate and update ETA for each bus - PRESERVED FROM ORIGINAL
                            updateBusETA(bus);
                        }

                        updateUIWithBuses();

                        // AUTO-FOCUS ON FIRST BUS - PRESERVED FROM ORIGINAL
                        if (shouldAutoFocusOnFirstBus && busesList.size() > 0) {
                            Bus firstBus = busesList.get(0);
                            if (firstBus.getLatitude() != null && firstBus.getLongitude() != null) {
                                focusOnBus(firstBus);
                                shouldAutoFocusOnFirstBus = false;
                            }
                        }
                    } else {
                        updateUIWithNoBuses();
                    }
                    busAdapter.notifyDataSetChanged();
                });

        activeListeners.add(listener);
    }

    private void loadBusesForRoute(String routeId) {
        clearActiveListeners();
        busesList.clear();
        busDriverNames.clear();
        busDriverPhones.clear();
        busAdapter.notifyDataSetChanged();

        ListenerRegistration listener = db.collection("buses")
                .whereEqualTo("routeId", routeId)
                .whereEqualTo("isActive", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    busesList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot doc : value) {
                            Bus bus = doc.toObject(Bus.class);
                            bus.setBusId(doc.getId());
                            busesList.add(bus);

                            Log.d(TAG, "Loaded bus: " + bus.getBusNumber() +
                                    " - Location: " + bus.getLatitude() + "," + bus.getLongitude());

                            loadDriverInfo(bus);
                            listenToBusUpdates(bus);

                            // Calculate and update ETA for each bus - PRESERVED FROM ORIGINAL
                            updateBusETA(bus);
                        }

                        updateUIWithBuses();

                        // AUTO-FOCUS ON FIRST BUS - PRESERVED FROM ORIGINAL
                        if (shouldAutoFocusOnFirstBus && busesList.size() > 0 && mMap != null) {
                            Bus firstBus = busesList.get(0);
                            if (firstBus.getLatitude() != null && firstBus.getLongitude() != null) {
                                focusOnBus(firstBus);
                                shouldAutoFocusOnFirstBus = false;
                            }
                        }
                    } else {
                        updateUIWithNoBuses();

                        if (mMap != null && currentRoute != null) {
                            focusOnRoute();
                        }
                    }
                    busAdapter.notifyDataSetChanged();
                });

        activeListeners.add(listener);
    }

    // ETA CALCULATION - PRESERVED FROM ORIGINAL
    private void updateBusETA(Bus bus) {
        if (currentRoute == null || currentRoute.getStops() == null || bus.getNextStop() == null) {
            return;
        }

        LatLng nextStopLatLng = findNextStopCoordinates(bus.getNextStop());
        if (nextStopLatLng == null || bus.getLatitude() == null || bus.getLongitude() == null) {
            return;
        }

        LatLng busLocation = new LatLng(bus.getLatitude(), bus.getLongitude());

        if (!etaRetryCounts.containsKey(bus.getBusId())) {
            etaRetryCounts.put(bus.getBusId(), 0);
        }

        directionsService.getRouteDirections(busLocation, nextStopLatLng, new AdminDirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReady(List<LatLng> routePoints, long durationMinutes) {
                etaRetryCounts.put(bus.getBusId(), 0);

                Map<String, Object> updates = new HashMap<>();
                updates.put("etaToNextStop", durationMinutes);

                db.collection("buses").document(bus.getBusId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "ETA updated for bus " + bus.getBusNumber() + ": " + durationMinutes + " min");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating ETA for bus " + bus.getBusNumber(), e);
                        });
            }

            @Override
            public void onDirectionsFailed(String error) {
                Log.e(TAG, "Failed to calculate ETA for bus " + bus.getBusNumber() + ": " + error);

                int currentRetryCount = etaRetryCounts.getOrDefault(bus.getBusId(), 0);
                if (currentRetryCount < MAX_ETA_RETRIES) {
                    Log.d(TAG, "Retrying ETA calculation for bus " + bus.getBusNumber() + " (attempt " + (currentRetryCount + 1) + ")");
                    etaRetryCounts.put(bus.getBusId(), currentRetryCount + 1);

                    retryHandler.postDelayed(() -> updateBusETA(bus), ETA_RETRY_DELAY_MS);
                } else {
                    Log.d(TAG, "Using fallback ETA calculation for bus " + bus.getBusNumber());
                    calculateFallbackETA(bus, busLocation, nextStopLatLng);
                    etaRetryCounts.put(bus.getBusId(), 0);
                }
            }
        });
    }

    private void calculateFallbackETA(Bus bus, LatLng busLocation, LatLng nextStopLatLng) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                busLocation.latitude, busLocation.longitude,
                nextStopLatLng.latitude, nextStopLatLng.longitude,
                results
        );

        float distanceInMeters = results[0];
        double averageBusSpeedMps = 8.33;
        long estimatedSeconds = (long) (distanceInMeters / averageBusSpeedMps);
        long estimatedMinutes = Math.max(1, estimatedSeconds / 60);
        estimatedMinutes = (long) (estimatedMinutes * 1.5);

        Map<String, Object> updates = new HashMap<>();
        updates.put("etaToNextStop", estimatedMinutes);
        updates.put("etaCalculationMethod", "fallback");

        long finalEstimatedMinutes = estimatedMinutes;
        db.collection("buses").document(bus.getBusId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Fallback ETA updated for bus " + bus.getBusNumber() + ": " + finalEstimatedMinutes + " min");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating fallback ETA for bus " + bus.getBusNumber(), e);
                });
    }

    private void updateUIWithBuses() {
        runOnUiThread(() -> {
            if (busesList.isEmpty()) {
                updateUIWithNoBuses();
                return;
            }

            tvActiveCount.setText("Active: " + busesList.size());

            if (currentRoute != null) {
                tvRouteInfo.setText(busesList.size() + " active buses on " + currentRoute.getRouteName());
                tvMapHint.setText(currentRoute.getRouteName());
            } else {
                tvRouteInfo.setText(busesList.size() + " active buses across all routes");
                tvMapHint.setText("All Routes View");
            }

            // FIX: Show buses in bottom sheet when there are active buses
            tvNoBuses.setVisibility(View.GONE);
            recyclerBuses.setVisibility(View.VISIBLE);
        });
    }

    private void updateUIWithNoBuses() {
        runOnUiThread(() -> {
            tvActiveCount.setText("Active: 0");

            if (currentRoute != null) {
                tvRouteInfo.setText("No active buses on " + currentRoute.getRouteName());
                tvMapHint.setText(currentRoute.getRouteName() + " - No Buses");
            } else {
                tvRouteInfo.setText("No active buses currently");
                tvMapHint.setText("No Active Buses");
            }

            // FIX: Show no buses message in bottom sheet
            tvNoBuses.setVisibility(View.VISIBLE);
            recyclerBuses.setVisibility(View.GONE);
        });
    }

    private void loadDriverInfo(Bus bus) {
        if (bus.getDriverId() != null && !bus.getDriverId().isEmpty()) {
            db.collection("users").document(bus.getDriverId())
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            String driverName = document.getString("name");
                            String driverPhone = document.getString("phone");
                            if (driverName != null) {
                                busDriverNames.put(bus.getBusId(), driverName);
                            }
                            if (driverPhone != null) {
                                busDriverPhones.put(bus.getBusId(), driverPhone);
                            }
                            busAdapter.notifyDataSetChanged();
                            updateBusOnMap(bus);
                        }
                    });
        }
    }

    private void listenToBusUpdates(Bus bus) {
        ListenerRegistration listener = db.collection("buses")
                .document(bus.getBusId())
                .addSnapshotListener((document, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Bus updates listen failed.", error);
                        return;
                    }

                    if (document != null && document.exists()) {
                        Bus updatedBus = document.toObject(Bus.class);
                        if (updatedBus != null) {
                            updatedBus.setBusId(document.getId());
                            updateBusInList(updatedBus);

                            if (updatedBus.getLatitude() != null && updatedBus.getLongitude() != null) {
                                updateBusOnMap(updatedBus);

                                // AUTO-FOCUS WHEN BUS STARTS SHARING LOCATION - PRESERVED FROM ORIGINAL
                                if (isFirstLocationUpdate(bus, updatedBus)) {
                                    focusOnBus(updatedBus);
                                }
                            }

                            if (selectedBus != null && selectedBus.getBusId().equals(updatedBus.getBusId())) {
                                selectedBus = updatedBus;
                            }
                        }
                    }
                });

        activeListeners.add(listener);
    }

    // CHECK IF FIRST LOCATION UPDATE - PRESERVED FROM ORIGINAL
    private boolean isFirstLocationUpdate(Bus oldBus, Bus newBus) {
        return (oldBus.getLatitude() == null || oldBus.getLongitude() == null) &&
                (newBus.getLatitude() != null && newBus.getLongitude() != null);
    }

    private void updateBusInList(Bus updatedBus) {
        for (int i = 0; i < busesList.size(); i++) {
            if (busesList.get(i).getBusId().equals(updatedBus.getBusId())) {
                busesList.set(i, updatedBus);
                busAdapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private BitmapDescriptor getResizedBusIcon(int drawableResId, int width, int height) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, drawableResId);
            if (drawable == null) {
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error resizing bus icon: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        }
    }

    private void updateBusOnMap(Bus bus) {
        if (mMap == null) return;

        Double latitude = bus.getLatitude();
        Double longitude = bus.getLongitude();

        if (latitude == null || longitude == null) {
            Log.d(TAG, "Bus " + bus.getBusNumber() + " has no location data");
            return;
        }

        LatLng busLocation = new LatLng(latitude, longitude);
        String busId = bus.getBusId();

        Log.d(TAG, "Updating bus on map: " + bus.getBusNumber() + " at " + latitude + "," + longitude);

        if (busMarkers.containsKey(busId)) {
            Marker marker = busMarkers.get(busId);
            marker.setPosition(busLocation);

            String driverName = busDriverNames.get(busId);
            String snippet = "Driver: " + (driverName != null ? driverName : "Unknown");
            if (bus.getNextStop() != null && !bus.getNextStop().equals("Unknown")) {
                snippet += "\nNext: " + bus.getNextStop();
                if (bus.getEtaToNextStop() > 0) {
                    snippet += " (" + bus.getEtaToNextStop() + " min)";
                }
            }
            marker.setSnippet(snippet);

            if (selectedBus != null && selectedBus.getBusId().equals(busId)) {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_selected_large, 56, 56));
            } else {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_large, 48, 48));
            }
        } else {
            String driverName = busDriverNames.get(busId);
            String snippet = "Driver: " + (driverName != null ? driverName : "Unknown");
            if (bus.getNextStop() != null && !bus.getNextStop().equals("Unknown")) {
                snippet += "\nNext: " + bus.getNextStop();
                if (bus.getEtaToNextStop() > 0) {
                    snippet += " (" + bus.getEtaToNextStop() + " min)";
                }
            }

            BitmapDescriptor icon = getResizedBusIcon(R.drawable.ic_bus_large, 48, 48);
            if (selectedBus != null && selectedBus.getBusId().equals(busId)) {
                icon = getResizedBusIcon(R.drawable.ic_bus_selected_large, 56, 56);
            }

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(busLocation)
                    .title("Bus " + bus.getBusNumber())
                    .snippet(snippet)
                    .icon(icon)
                    .anchor(0.5f, 0.5f);

            Marker marker = mMap.addMarker(markerOptions);
            busMarkers.put(busId, marker);

            Log.d(TAG, "New marker created for bus: " + bus.getBusNumber());
        }
    }

    private void drawRouteOnMap(Route route) {
        if (mMap == null || route.getStops() == null) return;

        clearRouteFromMap();
        busMarkers.clear();

        List<Map<String, Object>> stops = route.getStops();
        List<LatLng> stopPoints = new ArrayList<>();

        for (int i = 0; i < stops.size(); i++) {
            Map<String, Object> stop = stops.get(i);
            Double latitude = getCoordinateValue(stop.get("latitude"));
            Double longitude = getCoordinateValue(stop.get("longitude"));
            String stopName = getStopName(stop);

            if (latitude != null && longitude != null) {
                LatLng stopLatLng = new LatLng(latitude, longitude);
                stopPoints.add(stopLatLng);

                float hue = BitmapDescriptorFactory.HUE_BLUE;
                if (i == 0) hue = BitmapDescriptorFactory.HUE_GREEN;
                else if (i == stops.size() - 1) hue = BitmapDescriptorFactory.HUE_RED;

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(stopLatLng)
                        .title("Stop " + (i + 1) + ": " + stopName)
                        .snippet("Route: " + route.getRouteName())
                        .icon(BitmapDescriptorFactory.defaultMarker(hue));

                mMap.addMarker(markerOptions);
            }
        }

        if (stopPoints.size() >= 2) {
            directionsService.getRouteWithWaypoints(stopPoints, new AdminDirectionsService.DirectionsCallback() {
                @Override
                public void onDirectionsReady(List<LatLng> routePoints, long totalDuration) {
                    runOnUiThread(() -> {
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(routePoints)
                                .width(12f)
                                .color(0x802196F3)
                                .geodesic(false);
                        routePolyline = mMap.addPolyline(polylineOptions);
                        focusOnRoute();
                        Log.d(TAG, "Route drawn with directions: " + routePoints.size() + " points");
                    });
                }

                @Override
                public void onDirectionsFailed(String error) {
                    Log.e(TAG, "Directions failed, using straight line: " + error);
                    runOnUiThread(() -> {
                        drawStraightLineRoute(stopPoints);
                        focusOnRoute();
                        Toast.makeText(AdminLiveTrackingActivity.this,
                                "Using approximate route (directions unavailable)", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private LatLng findNextStopCoordinates(String stopName) {
        if (currentRoute == null || currentRoute.getStops() == null) {
            return null;
        }

        for (Map<String, Object> stop : currentRoute.getStops()) {
            String currentStopName = getStopName(stop);
            if (currentStopName != null && currentStopName.equals(stopName)) {
                Double latitude = getCoordinateValue(stop.get("latitude"));
                Double longitude = getCoordinateValue(stop.get("longitude"));
                if (latitude != null && longitude != null) {
                    return new LatLng(latitude, longitude);
                }
            }
        }
        return null;
    }

    private void drawStraightLineRoute(List<LatLng> stopPoints) {
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(stopPoints)
                .width(8f)
                .color(0x802196F3)
                .geodesic(true);
        routePolyline = mMap.addPolyline(polylineOptions);
    }

    private void clearRouteFromMap() {
        if (mMap != null) {
            mMap.clear();
        }
        busMarkers.clear();
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
        if (directionPolyline != null) {
            directionPolyline.remove();
            directionPolyline = null;
        }
        if (nextStopMarker != null) {
            nextStopMarker.remove();
            nextStopMarker = null;
        }
    }

    private void focusOnBus(Bus bus) {
        if (bus.getLatitude() != null && bus.getLongitude() != null) {
            LatLng busLocation = new LatLng(bus.getLatitude(), bus.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(busLocation, 15));
            Log.d(TAG, "Focused on bus: " + bus.getBusNumber());

            Toast.makeText(this, "Focused on Bus " + bus.getBusNumber(), Toast.LENGTH_SHORT).show();
        }
    }

    private void focusOnRoute() {
        if (currentRoute == null || currentRoute.getStops() == null) return;

        List<LatLng> stopPoints = new ArrayList<>();
        for (Map<String, Object> stop : currentRoute.getStops()) {
            Double latitude = getCoordinateValue(stop.get("latitude"));
            Double longitude = getCoordinateValue(stop.get("longitude"));
            if (latitude != null && longitude != null) {
                stopPoints.add(new LatLng(latitude, longitude));
            }
        }

        if (stopPoints.isEmpty()) return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : stopPoints) {
            builder.include(point);
        }

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(stopPoints.get(0), 12));
        }
    }

    @Override
    public void onBusClick(Bus bus) {
        selectedBus = bus;
        focusOnBus(bus);

        for (Map.Entry<String, Marker> entry : busMarkers.entrySet()) {
            Marker marker = entry.getValue();
            if (entry.getKey().equals(bus.getBusId())) {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_selected_large, 56, 56));
            } else {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_large, 48, 48));
            }
        }
    }

    // Helper methods
    private Double getCoordinateValue(Object coordinate) {
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
        if (stop.containsKey("stopName")) {
            Object stopName = stop.get("stopName");
            return stopName != null ? stopName.toString() : "Unknown Stop";
        }
        if (stop.containsKey("name")) {
            Object name = stop.get("name");
            return name != null ? name.toString() : "Unknown Stop";
        }
        return "Unknown Stop";
    }

    private void clearActiveListeners() {
        for (ListenerRegistration listener : activeListeners) {
            listener.remove();
        }
        activeListeners.clear();

        retryHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        if (checkLocationPermissions()) {
            try {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "Location permission not granted", e);
            }
        } else {
            requestLocationPermissions();
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2));
        loadAllActiveBuses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearActiveListeners();

        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }

        busMarkers.clear();
        busDriverNames.clear();
        busDriverPhones.clear();
        etaRetryCounts.clear();
    }
}