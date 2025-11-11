package com.example.schoolbus.driver.services;

import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;

public class EnhancedETAService {
    private static final String TAG = "EnhancedETAService";
    private static EnhancedETAService instance;
    private FirebaseFirestore db;

    // School bus specific factors
    private static final int AVG_STOP_TIME_MINUTES = 1;
    private static final double SCHOOL_ZONE_REDUCTION = 0.7;
    private static final double PEAK_HOUR_REDUCTION = 0.8;

    public EnhancedETAService() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized EnhancedETAService getInstance() {
        if (instance == null) {
            instance = new EnhancedETAService();
        }
        return instance;
    }

    public interface EnhancedETACallback {
        void onEnhancedETAReady(long enhancedETA, String confidence, List<String> factors);
        void onETAFailed(String error);
    }

    public void calculateEnhancedETA(LatLng currentLocation, LatLng nextStop,
                                     long googleMapsETA, String busId, String busNumber,
                                     String routeId, String nextStopName, EnhancedETACallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Calculating enhanced ETA for bus " + busNumber + " - Google Maps: " + googleMapsETA + "min");

                List<String> affectingFactors = new ArrayList<>();
                long enhancedETA = googleMapsETA;

                // Factor 1: Student boarding time
                enhancedETA += AVG_STOP_TIME_MINUTES;
                affectingFactors.add("Student boarding");

                // Factor 2: Peak hour traffic
                if (isPeakHour()) {
                    double peakAdjustment = Math.ceil(enhancedETA * (1.0 - PEAK_HOUR_REDUCTION));
                    enhancedETA += (long) peakAdjustment;
                    affectingFactors.add("Peak traffic");
                }

                // Factor 3: School zone detection
                if (isInSchoolZone(currentLocation, nextStop)) {
                    double schoolAdjustment = Math.ceil(enhancedETA * (1.0 - SCHOOL_ZONE_REDUCTION));
                    enhancedETA += (long) schoolAdjustment;
                    affectingFactors.add("School zone");
                }

                // Factor 4: Weather consideration
                if (isBadWeatherTime()) {
                    enhancedETA += 2;
                    affectingFactors.add("Weather delay");
                }

                enhancedETA = Math.max(1, enhancedETA);

                Log.d(TAG, "Final Enhanced ETA: " + enhancedETA + "min for bus " + busNumber);

                callback.onEnhancedETAReady(enhancedETA, "HIGH", affectingFactors);

            } catch (Exception e) {
                Log.e(TAG, "Enhanced ETA calculation failed: " + e.getMessage());
                callback.onETAFailed(e.getMessage());
            }
        }).start();
    }

    private boolean isPeakHour() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        boolean isWeekday = dayOfWeek >= Calendar.MONDAY && dayOfWeek <= Calendar.FRIDAY;

        if (isWeekday) {
            return (hour >= 7 && hour <= 9) || (hour >= 16 && hour <= 19);
        } else {
            return (hour >= 10 && hour <= 12);
        }
    }

    private boolean isInSchoolZone(LatLng current, LatLng nextStop) {
        double distance = calculateDistance(current, nextStop);

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        boolean isSchoolTime = (hour >= 7 && hour <= 9) || (hour >= 14 && hour <= 16);

        return distance < 1000 && isSchoolTime;
    }

    private boolean isBadWeatherTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour <= 7 || hour >= 18;
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        double lat1 = point1.latitude;
        double lon1 = point1.longitude;
        double lat2 = point2.latitude;
        double lon2 = point2.longitude;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return 6371000 * c;
    }
}