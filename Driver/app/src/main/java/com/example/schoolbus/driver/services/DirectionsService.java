package com.example.schoolbus.driver.services;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;
import com.google.maps.model.EncodedPolyline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DirectionsService {
    private static final String TAG = "DirectionsService";
    private static final String API_KEY = "AIzaSyDGBzaHtkkJjTIEirI92R_xBLVv3yKkD6A";
    private GeoApiContext geoApiContext;
    private static DirectionsService instance;

    private DirectionsService(Context context) {
        geoApiContext = new GeoApiContext.Builder()
                .apiKey(API_KEY)
                .queryRateLimit(3)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized DirectionsService getInstance(Context context) {
        if (instance == null) {
            instance = new DirectionsService(context);
        }
        return instance;
    }

    public interface DirectionsCallback {
        void onDirectionsReady(List<LatLng> routePoints, long totalDuration);
        void onDirectionsFailed(String error);
    }

    public void getRouteDirections(LatLng origin, LatLng destination, DirectionsCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Getting directions from " + origin + " to " + destination);

                DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                        .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                        .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                        .mode(TravelMode.DRIVING)
                        .await();

                if (result.routes != null && result.routes.length > 0) {
                    DirectionsRoute route = result.routes[0];
                    List<LatLng> decodedPath = decodePolyline(route.overviewPolyline);
                    long totalDuration = route.legs[0].duration.inSeconds / 60; // Convert to minutes

                    Log.d(TAG, "Route found with " + decodedPath.size() + " points, duration: " + totalDuration + " min");

                    callback.onDirectionsReady(decodedPath, totalDuration);
                } else {
                    callback.onDirectionsFailed("No routes found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Directions API error: " + e.getMessage());
                callback.onDirectionsFailed(e.getMessage());
            }
        }).start();
    }

    public void getRouteWithWaypoints(List<LatLng> waypoints, DirectionsCallback callback) {
        new Thread(() -> {
            try {
                if (waypoints == null || waypoints.size() < 2) {
                    callback.onDirectionsFailed("Not enough waypoints");
                    return;
                }

                Log.d(TAG, "Getting route with " + waypoints.size() + " waypoints");

                com.google.maps.model.LatLng[] stops = new com.google.maps.model.LatLng[waypoints.size()];
                for (int i = 0; i < waypoints.size(); i++) {
                    stops[i] = new com.google.maps.model.LatLng(waypoints.get(i).latitude, waypoints.get(i).longitude);
                }

                DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                        .origin(stops[0])
                        .destination(stops[stops.length - 1])
                        .waypoints(Arrays.copyOfRange(stops, 1, stops.length - 1))
                        .mode(TravelMode.DRIVING)
                        .optimizeWaypoints(false)
                        .await();

                if (result.routes != null && result.routes.length > 0) {
                    DirectionsRoute route = result.routes[0];
                    List<LatLng> decodedPath = decodePolyline(route.overviewPolyline);

                    // Calculate total duration
                    long totalDuration = 0;
                    for (com.google.maps.model.DirectionsLeg leg : route.legs) {
                        totalDuration += leg.duration.inSeconds;
                    }
                    totalDuration = totalDuration / 60; // Convert to minutes

                    Log.d(TAG, "Full route found with " + decodedPath.size() + " points, total duration: " + totalDuration + " min");

                    callback.onDirectionsReady(decodedPath, totalDuration);
                } else {
                    callback.onDirectionsFailed("No routes found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Directions API with waypoints error: " + e.getMessage());
                callback.onDirectionsFailed(e.getMessage());
            }
        }).start();
    }

    private List<LatLng> decodePolyline(EncodedPolyline polyline) {
        List<LatLng> decodedPath = new ArrayList<>();

        try {
            List<com.google.maps.model.LatLng> decoded = polyline.decodePath();
            for (com.google.maps.model.LatLng latLng : decoded) {
                decodedPath.add(new LatLng(latLng.lat, latLng.lng));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error decoding polyline: " + e.getMessage());
        }

        return decodedPath;
    }
}