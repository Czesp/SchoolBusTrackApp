package com.example.schoolbus.parent;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.schoolbus.parent.adapters.BusAdapter;
import com.example.schoolbus.parent.models.Bus;
import com.example.schoolbus.parent.models.Route;
import com.example.schoolbus.parent.models.User;
import com.example.schoolbus.parent.services.ParentDirectionsService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class ParentMainActivity extends AppCompatActivity implements
        OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "ParentApp";

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private MaterialToolbar toolbar;
    private RecyclerView recyclerBuses;
    private MaterialCardView cardBusDetails;
    private TextView tvRouteName, tvRouteInfo, tvNextStop, tvETA, tvDriverName, tvDriverContact;
    private MaterialButton btnCallDriver;
    private ExtendedFloatingActionButton fabCallDriver;
    private GoogleMap mMap;

    private ImageView ivBusArrow;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentParentId;
    private User currentParent;

    private List<Bus> busesList = new ArrayList<>();
    private BusAdapter busAdapter;
    private Route currentRoute;
    private Bus selectedBus;

    private Map<String, Marker> busMarkers = new HashMap<>();
    private Map<String, String> busDriverNames = new HashMap<>();
    private Map<String, String> busDriverPhones = new HashMap<>();
    private List<ListenerRegistration> activeListeners = new ArrayList<>();
    private Polyline routePolyline;
    private Polyline directionPolyline;
    private ParentDirectionsService directionsService;

    private Marker nextStopMarker;
    private String currentNextStop = "";
    private long currentETAMinutes = 0;

    private MaterialCardView cardBusesSection;
    private TextView tvNoBuses;
    private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;

    private boolean shouldAutoFocusOnBus = true;
    private boolean isMapReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        directionsService = ParentDirectionsService.getInstance(this);

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, ParentLoginActivity.class));
            finish();
            return;
        }

        currentParentId = mAuth.getCurrentUser().getUid();

        initializeViews();
        setupToolbarAndNavigation();
        setupRecyclerView();
        setupMapFragment();
        setupBottomSheetBehavior();
        loadParentData();
        subscribeToBusTopics();
    }

    private void setupBottomSheetBehavior() {
        cardBusesSection = findViewById(R.id.card_buses_section);
        bottomSheetBehavior = BottomSheetBehavior.from(cardBusesSection);

        bottomSheetBehavior.setPeekHeight(120);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setHalfExpandedRatio(0.5f);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_EXPANDED:
                        ivBusArrow.setImageResource(R.drawable.ic_arrow_up);
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        ivBusArrow.setImageResource(R.drawable.ic_arrow_down);
                        break;
                    case BottomSheetBehavior.STATE_HALF_EXPANDED:
                        ivBusArrow.setImageResource(R.drawable.ic_arrow_up);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        View layoutBusHeader = findViewById(R.id.layout_bus_header);
        layoutBusHeader.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
    }

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        recyclerBuses = findViewById(R.id.recycler_buses);
        cardBusDetails = findViewById(R.id.card_bus_details);
        tvRouteName = findViewById(R.id.tv_route_name);
        tvRouteInfo = findViewById(R.id.tv_route_info);
        tvNextStop = findViewById(R.id.tv_next_stop);
        tvETA = findViewById(R.id.tv_eta);
        tvDriverName = findViewById(R.id.tv_driver_name);
        tvDriverContact = findViewById(R.id.tv_driver_contact);
        btnCallDriver = findViewById(R.id.btn_call_driver);
        fabCallDriver = findViewById(R.id.fab_call_driver);

        ivBusArrow = findViewById(R.id.iv_bus_arrow);
        tvNoBuses = findViewById(R.id.tv_no_buses);
        cardBusesSection = findViewById(R.id.card_buses_section);

        btnCallDriver.setOnClickListener(v -> callDriver());
        fabCallDriver.setOnClickListener(v -> callDriver());
        findViewById(R.id.btn_profile).setOnClickListener(v -> openProfileActivity());

        // Hide both cards initially
        cardBusDetails.setVisibility(View.GONE);
        fabCallDriver.setVisibility(View.GONE);
    }

    private void setupToolbarAndNavigation() {
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navView.setNavigationItemSelectedListener(this);
    }

    private void setupRecyclerView() {
        busAdapter = new BusAdapter(busesList, busDriverNames, this::onBusSelected);
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

    private void loadParentData() {
        Log.d(TAG, "=== STARTING PARENT DATA LOAD ===");
        Log.d(TAG, "Current Parent ID: " + currentParentId);

        db.collection("users").document(currentParentId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        currentParent = task.getResult().toObject(User.class);
                        if (currentParent != null) {
                            Log.d(TAG, "✓ Parent data loaded successfully");
                            Log.d(TAG, "Parent Name: " + currentParent.getName());
                            Log.d(TAG, "Parent BusId: " + currentParent.getBusId());

                            updateNavigationHeader();
                            loadParentAssignedBus();
                        }
                    } else {
                        Toast.makeText(this, "Error loading parent data", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error loading parent data: ", task.getException());
                    }
                });
    }

    private void updateNavigationHeader() {
        View headerView = navView.getHeaderView(0);
        TextView tvParentName = headerView.findViewById(R.id.tv_parent_name);
        TextView tvParentEmail = headerView.findViewById(R.id.tv_parent_email);
        TextView tvStudentInfo = headerView.findViewById(R.id.tv_student_info);

        if (currentParent != null) {
            tvParentName.setText(currentParent.getName());
            tvParentEmail.setText(currentParent.getEmail());

            if (currentParent.getBusId() != null && !currentParent.getBusId().isEmpty()) {
                tvStudentInfo.setText("Assigned Bus: " + currentParent.getBusId());
            } else {
                tvStudentInfo.setText("No bus assigned");
            }
        }
    }

    private void loadParentAssignedBus() {
        Log.d(TAG, "=== STARTING BUS LOAD ===");

        if (currentParent == null) {
            Log.e(TAG, "Current parent is null - cannot load bus");
            tvRouteInfo.setText("Error loading parent data");
            loadFirstRouteAsFallback();
            return;
        }

        String assignedBusId = currentParent.getBusId();
        Log.d(TAG, "Assigned Bus ID from parent: " + assignedBusId);

        if (assignedBusId == null || assignedBusId.isEmpty()) {
            Log.w(TAG, "Parent has no bus assigned - busId is null or empty");
            tvRouteInfo.setText("No bus assigned to you");
            loadFirstRouteAsFallback();
            return;
        }

        Log.d(TAG, "Loading bus document with ID: " + assignedBusId);

        db.collection("buses").document(assignedBusId)
                .get()
                .addOnCompleteListener(busTask -> {
                    if (busTask.isSuccessful() && busTask.getResult().exists()) {
                        Log.d(TAG, "✓ Bus document EXISTS in Firestore");
                        Log.d(TAG, "Bus document data: " + busTask.getResult().getData());
                        setupBusListener(assignedBusId);
                    } else {
                        Log.e(TAG, "✗ Bus document does NOT exist in Firestore for ID: " + assignedBusId);
                        tvRouteInfo.setText("Bus " + assignedBusId + " not found in system");
                        loadFirstRouteAsFallback();
                    }
                });
    }

    private void setupBusListener(String busId) {
        Log.d(TAG, "Setting up real-time listener for bus: " + busId);

        busesList.clear();
        busDriverNames.clear();
        busDriverPhones.clear();
        busAdapter.notifyDataSetChanged();

        ListenerRegistration listener = db.collection("buses")
                .document(busId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore listen error for bus: " + error);
                        tvRouteInfo.setText("Error loading bus data");
                        return;
                    }

                    if (document != null && document.exists()) {
                        Log.d(TAG, "✓ Bus document exists in listener");
                        Map<String, Object> busData = document.getData();
                        Bus bus = convertMapToBus(busData, busId);

                        if (bus != null) {
                            Log.d(TAG, "✓ Bus object created successfully");
                            Log.d(TAG, "Bus Number: " + bus.getBusNumber());
                            Log.d(TAG, "Bus Active: " + bus.isActive());
                            Log.d(TAG, "Bus Location: " + bus.getLatitude() + ", " + bus.getLongitude());
                            Log.d(TAG, "Bus RouteId: " + bus.getRouteId());
                            Log.d(TAG, "Bus NextStop: " + bus.getNextStop());
                            Log.d(TAG, "Bus ETA: " + bus.getEtaToNextStop());
                            Log.d(TAG, "Bus DriverId: " + bus.getDriverId());

                            // Update the bus in list
                            busesList.clear();
                            busesList.add(bus);

                            // Load driver info FIRST
                            loadDriverInfo(bus);

                            // Then load route
                            if (bus.getRouteId() != null && !bus.getRouteId().isEmpty()) {
                                Log.d(TAG, "Loading route for bus: " + bus.getRouteId());
                                loadRouteForBus(bus.getRouteId());
                            } else {
                                Log.w(TAG, "Bus has no route ID");
                                loadFirstRouteAsFallback();
                            }

                            // Update UI
                            updateUIWithBuses();

                            // CRITICAL FIX: Update bus on map IMMEDIATELY when location changes
                            if (isMapReady && bus.getLatitude() != null && bus.getLongitude() != null) {
                                Log.d(TAG, "✓✓✓ UPDATING BUS MARKER ON MAP ✓✓✓");
                                updateBusOnMap(bus);

                                // Auto-focus on first location update
                                if (shouldAutoFocusOnBus) {
                                    new android.os.Handler().postDelayed(() -> {
                                        Log.d(TAG, "🎯 Setting up auto directions for assigned bus");
                                        setupAutoDirectionsForAssignedBus(bus);
                                        focusOnBus(bus);
                                        shouldAutoFocusOnBus = false;
                                    }, 1000);
                                }
                            } else {
                                Log.w(TAG, "Cannot update map - MapReady: " + isMapReady + ", Lat: " + bus.getLatitude() + ", Lng: " + bus.getLongitude());
                            }
                        } else {
                            Log.e(TAG, "✗ Failed to convert bus data");
                            tvRouteInfo.setText("Error loading bus information");
                        }
                    } else {
                        Log.w(TAG, "✗ Bus document does not exist in listener - removing bus");
                        // Remove bus from list and map
                        busesList.removeIf(b -> b.getBusId().equals(busId));
                        if (busMarkers.containsKey(busId)) {
                            busMarkers.get(busId).remove();
                            busMarkers.remove(busId);
                        }
                        busDriverNames.remove(busId);
                        busDriverPhones.remove(busId);
                        updateUIWithBuses();
                    }
                    busAdapter.notifyDataSetChanged();
                });

        activeListeners.add(listener);
        Log.d(TAG, "Bus listener registered");
    }

    private Bus convertMapToBus(Map<String, Object> busData, String busId) {
        try {
            Bus bus = new Bus();
            bus.setBusId(busId);

            if (busData.containsKey("busNumber")) {
                bus.setBusNumber(busData.get("busNumber").toString());
            }

            if (busData.containsKey("routeId")) {
                bus.setRouteId(busData.get("routeId").toString());
            }

            if (busData.containsKey("driverId")) {
                bus.setDriverId(busData.get("driverId").toString());
            }

            if (busData.containsKey("nextStop")) {
                bus.setNextStop(busData.get("nextStop").toString());
            }

            if (busData.containsKey("currentStatus")) {
                bus.setCurrentStatus(busData.get("currentStatus").toString());
            }

            if (busData.containsKey("isActive")) {
                Object active = busData.get("isActive");
                if (active instanceof Boolean) {
                    bus.setActive((Boolean) active);
                }
            }

            // FIXED: Just set the currentLocation map, let the model's getters handle coordinate extraction
            if (busData.containsKey("currentLocation")) {
                Object location = busData.get("currentLocation");
                if (location instanceof Map) {
                    bus.setCurrentLocation((Map<String, Object>) location);
                }
            }

            if (busData.containsKey("etaToNextStop")) {
                Object eta = busData.get("etaToNextStop");
                if (eta instanceof Long) {
                    bus.setEtaToNextStop((Long) eta);
                } else if (eta instanceof Integer) {
                    bus.setEtaToNextStop(((Integer) eta).longValue());
                } else if (eta instanceof Double) {
                    bus.setEtaToNextStop(((Double) eta).longValue());
                }
            }

            Log.d(TAG, "Manual conversion successful: " + bus.getBusNumber());
            return bus;

        } catch (Exception e) {
            Log.e(TAG, "Error in manual bus conversion: " + e.getMessage());
            return null;
        }
    }

    private void setupAutoDirectionsForAssignedBus(Bus bus) {
        Log.d(TAG, "🚀🚀🚀 SETUP AUTO DIRECTIONS FOR ASSIGNED BUS 🚀🚀🚀");
        Log.d(TAG, "Bus: " + bus.getBusNumber() + ", Next Stop: " + bus.getNextStop());

        // REMOVED: Auto-showing the card
        // selectedBus = bus;
        // cardBusDetails.setVisibility(View.VISIBLE);
        // updateBusDetails(bus);

        // Keep only the marker highlighting and directions
        if (busMarkers.containsKey(bus.getBusId())) {
            Marker marker = busMarkers.get(bus.getBusId());
            marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_selected_large, 56, 56));
            Log.d(TAG, "✅ Bus marker highlighted");
        }

        // Show directions to next stop (without showing the card)
        Log.d(TAG, "🎯 Calling updateDirectionToNextStop for assigned bus");
        updateDirectionToNextStop(bus);
    }

    private void loadFirstRouteAsFallback() {
        Log.d(TAG, "Loading first route as fallback");
        db.collection("routes")
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        QueryDocumentSnapshot document = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                        Route route = document.toObject(Route.class);
                        route.setRouteId(document.getId());
                        setCurrentRoute(route);
                        tvRouteInfo.setText("Showing default route (no bus assigned)");

                        if (isMapReady) {
                            new android.os.Handler().postDelayed(() -> {
                                focusOnRoute();
                            }, 1000);
                        }
                    } else {
                        Log.e(TAG, "No routes available");
                        tvRouteInfo.setText("No routes available");
                    }
                });
    }

    private void loadRouteForBus(String routeId) {
        Log.d(TAG, "Loading route for bus: " + routeId);
        db.collection("routes").document(routeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Route route = task.getResult().toObject(Route.class);
                        if (route != null) {
                            route.setRouteId(task.getResult().getId());
                            setCurrentRoute(route);
                            Log.d(TAG, "Route loaded successfully: " + route.getRouteName());
                        } else {
                            Log.e(TAG, "Route object is null");
                            loadFirstRouteAsFallback();
                        }
                    } else {
                        Log.e(TAG, "Error loading route for bus: " + routeId);
                        loadFirstRouteAsFallback();
                    }
                });
    }

    private void setCurrentRoute(Route route) {
        currentRoute = route;
        tvRouteName.setText(route.getRouteName());
        if (isMapReady) {
            drawRouteOnMap(route);
        }
    }

    private void updateUIWithBuses() {
        runOnUiThread(() -> {
            if (busesList.isEmpty()) {
                updateUIWithNoBuses();
                return;
            }

            if (busesList.size() > 0) {
                Bus bus = busesList.get(0);
                tvRouteInfo.setText("Tracking your assigned bus: " + bus.getBusNumber());

                tvNoBuses.setVisibility(View.GONE);
                recyclerBuses.setVisibility(View.VISIBLE);

                busAdapter.notifyDataSetChanged();

                Log.d(TAG, "✓ UI updated with bus: " + bus.getBusNumber());
            }
        });
    }

    private void updateUIWithNoBuses() {
        runOnUiThread(() -> {
            tvRouteInfo.setText("No bus assigned to you");
            tvNoBuses.setVisibility(View.VISIBLE);
            recyclerBuses.setVisibility(View.GONE);
        });
    }

    private void loadDriverInfo(Bus bus) {
        if (bus.getDriverId() != null && !bus.getDriverId().isEmpty()) {
            Log.d(TAG, "Loading driver info for ID: " + bus.getDriverId());
            db.collection("users").document(bus.getDriverId())
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            User driver = document.toObject(User.class);
                            if (driver != null) {
                                busDriverNames.put(bus.getBusId(), driver.getName());
                                busDriverPhones.put(bus.getBusId(), driver.getPhone());
                                Log.d(TAG, "✓ Driver info loaded: " + driver.getName() + " - " + driver.getPhone());

                                // Update adapter
                                runOnUiThread(() -> {
                                    busAdapter.notifyDataSetChanged();

                                    // Update details card if this bus is selected
                                    if (selectedBus != null && selectedBus.getBusId().equals(bus.getBusId())) {
                                        updateBusDetails(bus);
                                    }

                                    // Also update the bus marker with driver info
                                    updateBusMarkerWithDriverInfo(bus, driver.getName());
                                });
                            } else {
                                Log.e(TAG, "Driver object is null after conversion");
                                busDriverNames.put(bus.getBusId(), "Driver data error");
                            }
                        } else {
                            Log.w(TAG, "Driver document not found for ID: " + bus.getDriverId());
                            busDriverNames.put(bus.getBusId(), "Driver not assigned");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading driver info: ", e);
                        busDriverNames.put(bus.getBusId(), "Error loading driver");
                    });
        } else {
            Log.w(TAG, "Bus has no driver ID assigned");
            busDriverNames.put(bus.getBusId(), "Driver not assigned");
        }
    }

    private void updateBusMarkerWithDriverInfo(Bus bus, String driverName) {
        if (busMarkers.containsKey(bus.getBusId())) {
            Marker marker = busMarkers.get(bus.getBusId());
            String snippet = "Driver: " + driverName;
            if (bus.getNextStop() != null && !bus.getNextStop().equals("Unknown")) {
                snippet += "\nNext: " + bus.getNextStop();
                if (bus.getEtaToNextStop() > 0) {
                    snippet += " (" + bus.getEtaToNextStop() + " min)";
                }
            }
            marker.setSnippet(snippet);
        }
    }

    private BitmapDescriptor getResizedBusIcon(int drawableResId, int width, int height) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, drawableResId);
            if (drawable == null) {
                Log.e(TAG, "Drawable is null for resource: " + drawableResId);
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
        if (mMap == null) {
            Log.e(TAG, "Map is null, cannot update bus marker");
            return;
        }

        Double latitude = bus.getLatitude();
        Double longitude = bus.getLongitude();

        if (latitude == null || longitude == null) {
            Log.w(TAG, "Bus location is null, cannot update marker");
            return;
        }

        LatLng busLocation = new LatLng(latitude, longitude);
        String busId = bus.getBusId();

        Log.d(TAG, "Updating bus marker for: " + bus.getBusNumber() + " at " + latitude + ", " + longitude);

        String driverName = busDriverNames.get(busId);
        String snippet = "Driver: " + (driverName != null ? driverName : "Loading...");
        if (bus.getNextStop() != null && !bus.getNextStop().equals("Unknown")) {
            snippet += "\nNext: " + bus.getNextStop();
            if (bus.getEtaToNextStop() > 0) {
                snippet += " (" + bus.getEtaToNextStop() + " min)";
            }
        }

        if (busMarkers.containsKey(busId)) {
            // Update existing marker
            Marker marker = busMarkers.get(busId);
            marker.setPosition(busLocation);
            marker.setSnippet(snippet);
            Log.d(TAG, "✓ Updated existing bus marker");
        } else {
            // Create new marker
            BitmapDescriptor icon = getResizedBusIcon(R.drawable.ic_bus_large, 48, 48);

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(busLocation)
                    .title("Bus " + bus.getBusNumber())
                    .snippet(snippet)
                    .icon(icon)
                    .anchor(0.5f, 0.5f);

            Marker marker = mMap.addMarker(markerOptions);
            busMarkers.put(busId, marker);
            Log.d(TAG, "✓ Created new bus marker for: " + bus.getBusNumber());
        }

        // Update direction if selected
        if (selectedBus != null && selectedBus.getBusId().equals(busId)) {
            updateDirectionToNextStop(bus);
        }
    }

    private void updateDirectionToNextStop(Bus bus) {
        if (mMap == null) {
            return;
        }

        // Clear previous direction
        if (directionPolyline != null) {
            directionPolyline.remove();
            directionPolyline = null;
        }
        if (nextStopMarker != null) {
            nextStopMarker.remove();
            nextStopMarker = null;
        }

        // Get next stop coordinates from route
        LatLng nextStopLatLng = findNextStopCoordinates(bus.getNextStop());
        if (nextStopLatLng == null) {
            Log.e(TAG, "Could not find coordinates for stop: " + bus.getNextStop());
            return;
        }

        Double busLat = bus.getLatitude();
        Double busLng = bus.getLongitude();
        if (busLat == null || busLng == null) {
            Log.w(TAG, "Bus location is null, cannot draw direction");
            return;
        }

        LatLng busLocation = new LatLng(busLat, busLng);

        Log.d(TAG, "Drawing direction from bus to next stop: " + bus.getNextStop());

        directionsService.getRouteDirections(busLocation, nextStopLatLng, new ParentDirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReady(List<LatLng> routePoints, long durationMinutes) {
                Log.d(TAG, "Directions received with " + routePoints.size() + " points, ETA: " + durationMinutes + " min");

                runOnUiThread(() -> {
                    if (mMap == null) {
                        Log.e(TAG, "Map became null during direction drawing");
                        return;
                    }

                    try {
                        // Draw direction polyline
                        PolylineOptions directionOptions = new PolylineOptions()
                                .addAll(routePoints)
                                .width(15f)
                                .color(0xFFFF0000) // Bright red for visibility
                                .geodesic(false);

                        directionPolyline = mMap.addPolyline(directionOptions);
                        Log.d(TAG, "✅ Direction polyline drawn with " + routePoints.size() + " points");

                        // Add next stop marker
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(nextStopLatLng)
                                .title("Next Stop: " + bus.getNextStop())
                                .snippet("ETA: " + durationMinutes + " min")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

                        nextStopMarker = mMap.addMarker(markerOptions);
                        Log.d(TAG, "✅ Next stop marker added");

                        // Update ETA in details card
                        if (selectedBus != null && selectedBus.getBusId().equals(bus.getBusId())) {
                            currentETAMinutes = durationMinutes;
                            tvETA.setText(durationMinutes + " min");
                            Log.d(TAG, "✅ ETA updated in UI: " + durationMinutes + " min");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error drawing direction: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onDirectionsFailed(String error) {
                Log.e(TAG, "❌ Directions API failed: " + error);
                runOnUiThread(() -> {
                    Log.d(TAG, "🔄 Drawing straight line as fallback");
                    drawStraightLineDirection(busLocation, nextStopLatLng, bus.getNextStop());
                });
            }
        });
    }

    private void drawStraightLineDirection(LatLng busLocation, LatLng nextStop, String nextStopName) {
        if (mMap == null) return;

        PolylineOptions directionOptions = new PolylineOptions()
                .add(busLocation)
                .add(nextStop)
                .width(10f)
                .color(0xFFFFFF00)
                .geodesic(true);

        directionPolyline = mMap.addPolyline(directionOptions);

        MarkerOptions markerOptions = new MarkerOptions()
                .position(nextStop)
                .title("Next Stop: " + nextStopName)
                .snippet("ETA: " + currentETAMinutes + " min - Straight Path")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));

        nextStopMarker = mMap.addMarker(markerOptions);
    }

    private LatLng findNextStopCoordinates(String stopName) {
        if (currentRoute == null || currentRoute.getStops() == null) {
            Log.e(TAG, "Current route or stops is null");
            return null;
        }

        Log.d(TAG, "Looking for stop: '" + stopName + "' in route stops");
        Log.d(TAG, "Route has " + currentRoute.getStops().size() + " stops");

        for (int i = 0; i < currentRoute.getStops().size(); i++) {
            Map<String, Object> stop = currentRoute.getStops().get(i);
            String currentStopName = getStopName(stop);
            Log.d(TAG, "Stop " + i + ": '" + currentStopName + "'");

            if (currentStopName != null && currentStopName.equals(stopName)) {
                Double latitude = getCoordinateValue(stop.get("latitude"));
                Double longitude = getCoordinateValue(stop.get("longitude"));

                Log.d(TAG, "✅ Found matching stop: " + currentStopName);
                Log.d(TAG, "Coordinates: " + latitude + ", " + longitude);

                if (latitude != null && longitude != null) {
                    return new LatLng(latitude, longitude);
                } else {
                    Log.e(TAG, "❌ Coordinates are null for stop: " + currentStopName);
                }
            }
        }

        Log.e(TAG, "❌ Could not find coordinates for stop: '" + stopName + "'");
        return null;
    }

    private void drawRouteOnMap(Route route) {
        if (mMap == null || route.getStops() == null) return;

        mMap.clear();
        busMarkers.clear();
        if (routePolyline != null) {
            routePolyline.remove();
        }
        if (directionPolyline != null) {
            directionPolyline.remove();
        }
        if (nextStopMarker != null) {
            nextStopMarker.remove();
        }

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
                if (i == 0) {
                    hue = BitmapDescriptorFactory.HUE_GREEN;
                } else if (i == stops.size() - 1) {
                    hue = BitmapDescriptorFactory.HUE_RED;
                }

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(stopLatLng)
                        .title("Stop " + (i + 1) + ": " + stopName)
                        .snippet("Route: " + route.getRouteName())
                        .icon(BitmapDescriptorFactory.defaultMarker(hue));

                mMap.addMarker(markerOptions);
            }
        }

        if (stopPoints.size() >= 2) {
            drawRoadRouteWithDirections(stopPoints);
        }

        // CRITICAL: Re-add bus markers after drawing route
        for (Bus bus : busesList) {
            if (bus.getLatitude() != null && bus.getLongitude() != null) {
                updateBusOnMap(bus);
            }
        }
    }

    private void drawRoadRouteWithDirections(List<LatLng> stopPoints) {
        directionsService.getRouteWithWaypoints(stopPoints, new ParentDirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReady(List<LatLng> routePoints, long totalDuration) {
                runOnUiThread(() -> {
                    if (routePolyline != null) {
                        routePolyline.remove();
                    }

                    PolylineOptions polylineOptions = new PolylineOptions()
                            .addAll(routePoints)
                            .width(12f)
                            .color(0x802196F3)
                            .geodesic(false);

                    routePolyline = mMap.addPolyline(polylineOptions);

                    Log.d(TAG, "Route drawn with " + routePoints.size() + " points, duration: " + totalDuration + " min");
                });
            }

            @Override
            public void onDirectionsFailed(String error) {
                Log.e(TAG, "Failed to get directions: " + error);
                runOnUiThread(() -> {
                    PolylineOptions polylineOptions = new PolylineOptions()
                            .addAll(stopPoints)
                            .width(8f)
                            .color(0x802196F3)
                            .geodesic(true);
                    routePolyline = mMap.addPolyline(polylineOptions);
                    Log.d(TAG, "Using straight line route due to directions failure");
                });
            }
        });
    }

    private void focusOnBus(Bus bus) {
        if (bus.getLatitude() != null && bus.getLongitude() != null) {
            LatLng busLocation = new LatLng(bus.getLatitude(), bus.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(busLocation, 15));
            Log.d(TAG, "Auto-focused on bus: " + bus.getBusNumber());
        } else {
            Log.w(TAG, "Bus has no location, focusing on route instead");
            focusOnRoute();
        }
    }

    private void focusOnRoute() {
        if (currentRoute == null || currentRoute.getStops() == null) {
            Log.w(TAG, "Cannot focus on route: current route or stops is null");
            return;
        }

        List<LatLng> stopPoints = new ArrayList<>();
        for (Map<String, Object> stop : currentRoute.getStops()) {
            Double latitude = getCoordinateValue(stop.get("latitude"));
            Double longitude = getCoordinateValue(stop.get("longitude"));
            if (latitude != null && longitude != null) {
                stopPoints.add(new LatLng(latitude, longitude));
            }
        }

        if (stopPoints.isEmpty()) {
            Log.w(TAG, "No valid stop coordinates to focus on");
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : stopPoints) {
            builder.include(point);
        }

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            Log.d(TAG, "Auto-focused on route: " + currentRoute.getRouteName());
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(stopPoints.get(0), 12));
            Log.d(TAG, "Focused on first stop due to bounds exception");
        }
    }

    private void onBusSelected(Bus bus) {
        selectedBus = bus;

        // Show only the call button
        fabCallDriver.setVisibility(View.VISIBLE);
        cardBusDetails.setVisibility(View.GONE); // Keep full card hidden

        focusOnBus(bus);

        for (Map.Entry<String, Marker> entry : busMarkers.entrySet()) {
            Marker marker = entry.getValue();
            if (entry.getKey().equals(bus.getBusId())) {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_selected_large, 56, 56));
            } else {
                marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_large, 48, 48));
            }
        }

        Log.d(TAG, "Bus manually selected, updating direction to next stop...");
        updateDirectionToNextStop(bus);
    }

    private void updateBusDetails(Bus bus) {
        tvNextStop.setText(bus.getNextStop() != null ? bus.getNextStop() : "Unknown");

        // Use the ETA from bus if available, otherwise use calculated ETA
        if (bus.getEtaToNextStop() > 0) {
            tvETA.setText(bus.getEtaToNextStop() + " min");
        } else if (currentETAMinutes > 0) {
            tvETA.setText(currentETAMinutes + " min");
        } else {
            tvETA.setText("Calculating...");
        }

        String driverName = busDriverNames.get(bus.getBusId());
        String driverPhone = busDriverPhones.get(bus.getBusId());

        tvDriverName.setText(driverName != null ? driverName : "Loading driver...");
        tvDriverContact.setText(driverPhone != null ? driverPhone : "Not available");

        btnCallDriver.setEnabled(driverPhone != null && !driverPhone.isEmpty());

        currentNextStop = bus.getNextStop() != null ? bus.getNextStop() : "";
        currentETAMinutes = bus.getEtaToNextStop();
    }

    private void callDriver() {
        if (selectedBus != null) {
            String driverPhone = busDriverPhones.get(selectedBus.getBusId());
            if (driverPhone != null && !driverPhone.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + driverPhone));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Driver contact not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openProfileActivity() {
        Intent intent = new Intent(this, ParentProfileActivity.class);
        startActivity(intent);
    }

    private void subscribeToBusTopics() {
        if (currentParent != null && currentParent.getBusId() != null) {
            String busTopic = "bus_" + currentParent.getBusId();
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(busTopic)
                    .addOnCompleteListener(task -> {
                        Log.d(TAG, "Subscribed to bus topic: " + busTopic);
                    });

            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("parent_alerts")
                    .addOnCompleteListener(task -> {
                        Log.d(TAG, "Subscribed to parent_alerts topic");
                    });
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.navHome) {
            // Already on home
        } else if (id == R.id.nav_notif) {
            Intent intent = new Intent(this, ParentNotificationHistoryActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            logoutUser();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logoutUser() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes, Logout", (dialog, which) -> {
                    clearActiveListeners();
                    mAuth.signOut();
                    startActivity(new Intent(this, ParentLoginActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        isMapReady = true;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        mMap.setOnMapClickListener(latLng -> {
            // ONLY hide the call button and clear selection
            // DON'T remove bus markers or directions
            fabCallDriver.setVisibility(View.GONE);
            cardBusDetails.setVisibility(View.GONE);
            selectedBus = null;

            // Reset bus markers to normal (not selected) but DON'T remove them
            resetBusMarkers();

            // Keep the direction polyline and next stop marker visible
            // DON'T call clearDirectionPolyline();

            Log.d(TAG, "Map clicked - hiding call button but keeping bus and directions");
        });

        // Also add marker click listener to handle bus selection
        mMap.setOnMarkerClickListener(marker -> {
            // Check if this marker is a bus marker
            for (Map.Entry<String, Marker> entry : busMarkers.entrySet()) {
                if (entry.getValue().equals(marker)) {
                    String busId = entry.getKey();
                    // Find the bus in our list
                    for (Bus bus : busesList) {
                        if (bus.getBusId().equals(busId)) {
                            onBusSelected(bus);
                            return true; // Return true to indicate we've handled the click
                        }
                    }
                }
            }
            return false; // Return false to allow default behavior for other markers
        });

        Log.d(TAG, "Map is ready");

        // Draw current route if available
        if (currentRoute != null) {
            drawRouteOnMap(currentRoute);
        }

        // Update any existing buses on map
        for (Bus bus : busesList) {
            if (bus.getLatitude() != null && bus.getLongitude() != null) {
                updateBusOnMap(bus);
            }
        }
    }

    private void clearDirectionPolyline() {
        if (directionPolyline != null) {
            directionPolyline.remove();
            directionPolyline = null;
        }
        if (nextStopMarker != null) {
            nextStopMarker.remove();
            nextStopMarker = null;
        }
    }

    private void resetBusMarkers() {
        for (Marker marker : busMarkers.values()) {
            marker.setIcon(getResizedBusIcon(R.drawable.ic_bus_large, 48, 48));
        }
        Log.d(TAG, "Bus markers reset to normal state");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearActiveListeners();
    }

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
    }
}