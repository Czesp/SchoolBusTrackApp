package com.example.schoolbus.driver.services;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

import com.example.schoolbus.driver.models.Bus;
import com.example.schoolbus.driver.models.Route;
import com.example.schoolbus.driver.models.SafetyAlert;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentBusId;
    private String currentDriverId;
    private String currentDriverName;
    private String currentBusNumber;
    private String currentRouteId;
    private Route currentRoute; // ADDED: Route object
    private String currentNextStop = ""; // CHANGED: Empty instead of hardcoded
    private long currentEta = 5;

    private static final double MAX_SPEED_KMPH = 60.0;
    private static final double ROUTE_DEVIATION_THRESHOLD = 1000;
    private static final long PROLONGED_STOP_THRESHOLD = 10 * 60 * 1000;
    private static final long SAFETY_CHECK_INTERVAL = 30000;
    private Handler safetyHandler = new Handler();
    private Runnable safetyRunnable;

    private NotificationService notificationService;
    private EnhancedETAService enhancedETAService;

    private Map<String, Long> lastAlertTimeMap = new HashMap<>();
    private static final long ALERT_COOLDOWN_MS = 3000000;

    private Map<String, Long> lastArrivalNotificationMap = new HashMap<>();
    private static final long ARRIVAL_NOTIFICATION_COOLDOWN_MS = 300000;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        notificationService = new NotificationService(this);
        enhancedETAService = EnhancedETAService.getInstance();
        setupLocationCallback();
        loadDriverBusInfo();
        setupSafetyMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Location tracking service started");
        clearNotificationSpam();
        startLocationUpdates();
        return START_STICKY;
    }

    private void clearNotificationSpam() {
        // Delete recent arrival notifications to stop spam
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);

        db.collection("arrival_notifications")
                .whereGreaterThan("timestamp", new com.google.firebase.Timestamp(new Date(oneHourAgo)))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        doc.getReference().delete();
                    }
                    Log.d(TAG, "Cleared recent notification spam");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error clearing spam", e);
                });
    }
    private void loadDriverBusInfo() {
        currentDriverId = auth.getCurrentUser().getUid();

        db.collection("users").document(currentDriverId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentDriverName = documentSnapshot.getString("name");
                        currentBusId = documentSnapshot.getString("busId");

                        if (currentBusId != null && !currentBusId.isEmpty()) {
                            // Load bus details
                            db.collection("buses").document(currentBusId)
                                    .get()
                                    .addOnSuccessListener(busDocument -> {
                                        if (busDocument.exists()) {
                                            currentBusNumber = busDocument.getString("busNumber");
                                            currentRouteId = busDocument.getString("routeId");
                                            Log.d(TAG, "Driver assigned to bus: " + currentBusNumber);

                                            // ADDED: Load route data
                                            loadRouteData();
                                        }
                                    });
                        } else {
                            Log.w(TAG, "Driver not assigned to any bus");
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading driver bus info", e));
    }

    // ADDED: Load route data method
    private void loadRouteData() {
        if (currentRouteId == null || currentRouteId.isEmpty()) {
            Log.w(TAG, "No route ID available");
            return;
        }

        db.collection("routes").document(currentRouteId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentRoute = documentSnapshot.toObject(Route.class);
                        if (currentRoute != null) {
                            Log.d(TAG, "Route loaded: " + currentRoute.getRouteName() +
                                    " with " + (currentRoute.getStops() != null ? currentRoute.getStops().size() : 0) + " stops");
                        } else {
                            Log.e(TAG, "Failed to parse route data");
                        }
                    } else {
                        Log.w(TAG, "Route document does not exist: " + currentRouteId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading route data: " + e.getMessage());
                });
    }

    // Add this method to track notification sources
    private void debugNotificationSource(String stopName, double distance) {
        Log.d(TAG, "=== NOTIFICATION DEBUG ===");
        Log.d(TAG, "Stop: " + stopName);
        Log.d(TAG, "Distance: " + distance + "m");
        Log.d(TAG, "Should send notification: " + (distance < 500));
        Log.d(TAG, "Current Bus: " + currentBusNumber);
        Log.d(TAG, "Current Route: " + (currentRoute != null ? currentRoute.getRouteName() : "null"));
        Log.d(TAG, "========================");
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || currentBusId == null) return;

                for (Location location : locationResult.getLocations()) {
                    updateBusLocation(location);

                    // ADDED: Only calculate ETA if we have route data
                    if (currentRoute != null) {
                        calculateEnhancedETA(location);
                    }
                }
            }
        };
    }

    // Add this method to track notification sources
    private void updateBusLocation(Location location) {
        if (currentBusId == null) return;

        Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude() + " Accuracy: " + location.getAccuracy() + "m");

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("accuracy", location.getAccuracy());

        // Update buses collection (for parent app)
        Map<String, Object> busUpdates = new HashMap<>();
        busUpdates.put("currentLocation", locationData);
        busUpdates.put("lastUpdated", System.currentTimeMillis());
        busUpdates.put("nextStop", currentNextStop);
        busUpdates.put("etaToNextStop", currentEta);
        busUpdates.put("currentStatus", "on_route");
        busUpdates.put("isActive", true);

        db.collection("buses").document(currentBusId)
                .set(busUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Bus document updated in buses collection");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating bus document", e);
                });

        // Also update livelocation collection (for existing functionality)
        Map<String, Object> liveLocationData = new HashMap<>();
        liveLocationData.put("busId", currentBusId);
        liveLocationData.put("busNumber", currentBusNumber);
        liveLocationData.put("driverId", currentDriverId);
        liveLocationData.put("driverName", currentDriverName);
        liveLocationData.put("routeId", currentRouteId);
        liveLocationData.put("latitude", location.getLatitude());
        liveLocationData.put("longitude", location.getLongitude());
        liveLocationData.put("accuracy", location.getAccuracy());
        liveLocationData.put("nextStop", currentNextStop);
        liveLocationData.put("etaToNextStop", currentEta);
        liveLocationData.put("currentStatus", "on_route");
        liveLocationData.put("currentStopIndex", 0);
        liveLocationData.put("timestamp", System.currentTimeMillis());

        db.collection("live_locations")
                .document(currentBusId)
                .set(liveLocationData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Live location updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating live location", e);
                });
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(10000)
                .setFastestInterval(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d(TAG, "Location updates started");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
    }

    private void setupSafetyMonitoring() {
        safetyRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentBusId != null && currentRouteId != null) {
                    performSafetyCheck();
                }
                safetyHandler.postDelayed(this, SAFETY_CHECK_INTERVAL);
            }
        };
        safetyHandler.postDelayed(safetyRunnable, SAFETY_CHECK_INTERVAL);
    }

    private void performSafetyCheck() {
        if (currentBusId == null) return;

        // Get current bus data
        db.collection("buses").document(currentBusId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Bus currentBus = documentSnapshot.toObject(Bus.class);
                        if (currentBus != null) {
                            currentBus.setBusId(currentBusId);

                            // Get route for deviation calculation
                            db.collection("routes").document(currentRouteId)
                                    .get()
                                    .addOnSuccessListener(routeDoc -> {
                                        if (routeDoc.exists()) {
                                            Route currentRoute = routeDoc.toObject(Route.class);
                                            List<SafetyAlert> alerts = detectSafetyIssues(currentBus, currentRoute);

                                            // Save alerts to Firestore
                                            for (SafetyAlert alert : alerts) {
                                                saveSafetyAlert(alert);
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    private List<SafetyAlert> detectSafetyIssues(Bus currentBus, Route currentRoute) {
        List<SafetyAlert> alerts = new ArrayList<>();

        // 1. Speed Violation Detection
        if (currentBus.getSpeed() > MAX_SPEED_KMPH) {
            SafetyAlert alert = new SafetyAlert(
                    "SPEED_VIOLATION",
                    "Bus " + currentBusNumber + " exceeding speed limit: " +
                            Math.round(currentBus.getSpeed()) + " km/h",
                    currentBusId
            );
            // ADD THIS LINE:
            alert.setTimestamp(com.google.firebase.Timestamp.now());
            alerts.add(alert);
        }

        // 2. Route Deviation Detection
        if (currentBus.getSpeed() > 5 && currentRoute != null) {
            double deviation = calculateRouteDeviation(currentBus, currentRoute);
            if (deviation > ROUTE_DEVIATION_THRESHOLD) {
                SafetyAlert alert = new SafetyAlert(
                        "ROUTE_DEVIATION",
                        "Bus " + currentBusNumber + " deviated from route by " +
                                Math.round(deviation) + " meters",
                        currentBusId
                );
                // ADD THIS LINE:
                alert.setTimestamp(com.google.firebase.Timestamp.now());
                alerts.add(alert);
            }
        }

        // 3. Prolonged Stop Detection
        if ("stopped".equals(currentBus.getCurrentStatus()) &&
                System.currentTimeMillis() - currentBus.getLastMovementTime() > PROLONGED_STOP_THRESHOLD) {
            SafetyAlert alert = new SafetyAlert(
                    "PROLONGED_STOP",
                    "Bus " + currentBusNumber + " stopped for more than 10 minutes",
                    currentBusId
            );
            // ADD THIS LINE:
            alert.setTimestamp(com.google.firebase.Timestamp.now());
            alerts.add(alert);
        }

        return alerts;
    }

    private double calculateRouteDeviation(Bus bus, Route route) {
        if (route == null || route.getStops() == null || route.getStops().isEmpty()) {
            return 0;
        }

        Double busLat = bus.getLatitude();
        Double busLng = bus.getLongitude();
        if (busLat == null || busLng == null) {
            return 0;
        }

        // Find minimum distance to any stop in the route
        double minDistance = Double.MAX_VALUE;

        for (Map<String, Object> stop : route.getStops()) {
            Double stopLat = getCoordinateValue(stop.get("latitude"));
            Double stopLng = getCoordinateValue(stop.get("longitude"));

            if (stopLat != null && stopLng != null) {
                double distance = calculateHaversineDistance(busLat, busLng, stopLat, stopLng);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }

        return minDistance == Double.MAX_VALUE ? 0 : minDistance;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
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

    private void saveSafetyAlert(SafetyAlert alert) {
        // Check cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        Long lastAlertTime = lastAlertTimeMap.get(alert.getAlertType());

        if (lastAlertTime != null && (currentTime - lastAlertTime) < ALERT_COOLDOWN_MS) {
            Log.d(TAG, "Alert " + alert.getAlertType() + " is in cooldown, skipping");
            return;
        }

        // Update last alert time
        lastAlertTimeMap.put(alert.getAlertType(), currentTime);

        alert.setBusNumber(currentBusNumber);
        alert.setRouteId(currentRouteId);

        db.collection("safety_alerts")
                .add(alert)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Safety alert saved: " + alert.getMessage());
                    notificationService.sendSafetyAlertNotification(alert);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving safety alert", e);
                });
    }

    // UPDATED: Dynamic next stop detection
    private void calculateEnhancedETA(Location location) {
        if (currentRoute == null || currentRoute.getStops() == null || currentRoute.getStops().isEmpty()) {
            Log.w(TAG, "No route data available for ETA calculation");
            return;
        }

        // DYNAMIC NEXT STOP DETECTION
        String nextStopName = findNextStop(location);
        if (nextStopName == null) {
            Log.w(TAG, "Could not determine next stop");
            return;
        }

        LatLng nextStopLatLng = findStopCoordinates(nextStopName);
        if (nextStopLatLng == null) {
            Log.e(TAG, "Could not find coordinates for stop: " + nextStopName);
            return;
        }

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Calculate distance for simple ETA (fallback)
        double distance = calculateHaversineDistance(
                location.getLatitude(), location.getLongitude(),
                nextStopLatLng.latitude, nextStopLatLng.longitude
        );

        debugNotificationSource(nextStopName, distance);

        checkAndSendArrivalNotification(location, nextStopName, distance);

        // Simple ETA calculation: distance / average speed (20 km/h)
        long simpleETA = Math.max(1, (long)(distance / 333.33));

        // Use Enhanced ETA service
        enhancedETAService.calculateEnhancedETA(
                currentLatLng, nextStopLatLng, simpleETA,
                currentBusId, currentBusNumber, currentRouteId, nextStopName,
                new EnhancedETAService.EnhancedETACallback() {
                    @Override
                    public void onEnhancedETAReady(long enhancedETA, String confidence, List<String> factors) {
                        Log.d(TAG, "Enhanced ETA calculated: " + enhancedETA + "min to " + nextStopName);

                        currentEta = enhancedETA;
                        currentNextStop = nextStopName;
                        updateBusETAInFirestore(enhancedETA, nextStopName);
                    }

                    @Override
                    public void onETAFailed(String error) {
                        Log.e(TAG, "Enhanced ETA failed, using simple ETA: " + error);
                        currentEta = simpleETA;
                        currentNextStop = nextStopName;
                        updateBusETAInFirestore(simpleETA, nextStopName);
                    }
                }
        );
    }

    // ADDED: Dynamic next stop detection
    private String findNextStop(Location currentLocation) {
        if (currentRoute == null || currentRoute.getStops() == null) {
            return null;
        }

        List<Map<String, Object>> stops = currentRoute.getStops();
        String closestStop = null;
        double minDistance = Double.MAX_VALUE;

        // KNN search with 5km operational radius (K=1)
        for (Map<String, Object> stop : stops) {
            Double stopLat = getCoordinateValue(stop.get("latitude"));
            Double stopLng = getCoordinateValue(stop.get("longitude"));

            if (stopLat != null && stopLng != null) {
                double distance = calculateHaversineDistance(
                        currentLocation.getLatitude(), currentLocation.getLongitude(),
                        stopLat, stopLng
                );
                // Update nearest neighbor within valid range
                if (distance < minDistance && distance < 5000) {
                    minDistance = distance;
                    closestStop = getStopName(stop);
                }
            }
        }

        if (closestStop != null) {
            Log.d(TAG, "Next stop determined: " + closestStop + " (" + minDistance + "m away)");
            return closestStop;
        }

        // Fallback: return first stop
        if (!stops.isEmpty()) {
            String firstStop = getStopName(stops.get(0));
            Log.d(TAG, "Using first stop as fallback: " + firstStop);
            return firstStop;
        }

        return null;
    }

    // ADDED: Find coordinates for a stop by name
    private LatLng findStopCoordinates(String stopName) {
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

    private void updateBusETAInFirestore(long eta, String nextStop) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("etaToNextStop", eta);
        updates.put("nextStop", nextStop);

        db.collection("buses").document(currentBusId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Enhanced ETA updated in Firestore: " + eta + "min to " + nextStop);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update ETA in Firestore: " + e.getMessage());
                });
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

    private void checkAndSendArrivalNotification(Location location, String nextStopName, double distance) {
        // Only send notification when close to stop (within 300m) AND bus is moving slowly
        if (distance < 300 && location.hasSpeed() && location.getSpeed() < 5.0) {

            // Check cooldown
            String cooldownKey = currentBusId + "_" + nextStopName;
            long currentTime = System.currentTimeMillis();
            Long lastNotificationTime = lastArrivalNotificationMap.get(nextStopName);

            if (lastNotificationTime != null && (currentTime - lastNotificationTime) < ARRIVAL_NOTIFICATION_COOLDOWN_MS) {
                Log.d(TAG, "Arrival notification for " + nextStopName + " is in cooldown");
                return;
            }

            // Send notification
            sendArrivalNotification(nextStopName, distance);
            lastArrivalNotificationMap.put(cooldownKey, currentTime);
            Log.d(TAG, "✓ Arrival notification sent for " + nextStopName + ", cooldown set");
        }
    }

    private void sendArrivalNotification(String stopName, double distance) {
        long currentTime = System.currentTimeMillis();

        Log.d(TAG, "SENDING ARRIVAL NOTIFICATION: " + stopName + " (" + distance + "m away)");
        Log.d(TAG, "TIMESTAMP: " + currentTime + " = " + new Date(currentTime).toString());

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "ARRIVAL_PREDICTION");
        notificationData.put("message", "Bus " + currentBusNumber + " will arrive at " + stopName + " in " + currentEta + " minutes");
        notificationData.put("busNumber", currentBusNumber);
        notificationData.put("routeId", currentRouteId);
        notificationData.put("busId", currentBusId);
        notificationData.put("nextStop", stopName);
        notificationData.put("etaMinutes", currentEta);
        notificationData.put("timestamp", com.google.firebase.Timestamp.now()); // CORRECT: Current time in milliseconds
        notificationData.put("notificationSent", false);
        notificationData.put("read", false);

        // Add factors
        List<String> factors = new ArrayList<>();
        factors.add("Real-time location");
        factors.add("Distance: " + Math.round(distance) + "m");
        notificationData.put("factors", factors);

        // Use unique document ID to prevent duplicates
        String docId = "location_" + currentBusId + "_" + stopName + "_" + (currentTime / 60000);

        db.collection("arrival_notifications")
                .document(docId)
                .set(notificationData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Arrival notification saved: " + stopName + " at " + new Date(currentTime));

                    // Verify the timestamp was saved correctly
                    verifyTimestampInFirestore(docId, currentTime);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving arrival notification", e);
                });
    }

    // ADD THIS METHOD TO VERIFY TIMESTAMPS
    private void verifyTimestampInFirestore(String docId, long expectedTime) {
        db.collection("arrival_notifications").document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Object savedTimestamp = documentSnapshot.get("timestamp");
                        Log.d(TAG, "VERIFICATION - Saved timestamp: " + savedTimestamp);
                        Log.d(TAG, "VERIFICATION - Expected timestamp: " + expectedTime);

                        if (savedTimestamp instanceof Long) {
                            Date savedDate = new Date((Long) savedTimestamp);
                            Log.d(TAG, "VERIFICATION - Saved date: " + savedDate.toString());
                        } else {
                            Log.e(TAG, "VERIFICATION - Wrong timestamp type: " +
                                    (savedTimestamp != null ? savedTimestamp.getClass().getSimpleName() : "null"));
                        }
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");

            if (currentBusId != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("isActive", false);
                updates.put("currentStatus", "offline");

                db.collection("buses").document(currentBusId)
                        .set(updates, SetOptions.merge());
            }
        }
        if (safetyHandler != null && safetyRunnable != null) {
            safetyHandler.removeCallbacks(safetyRunnable);
        }
    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}