package com.example.schoolbus.driver.models;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Bus {
    private String busId;
    private String busNumber;
    private String routeId;
    private String driverId;
    private boolean isActive;
    private Timestamp createdAt;
    private Map<String, Object> currentLocation;
    private String nextStop;
    private long etaToNextStop; // in minutes
    private String currentStatus; // "on_route", "at_stop", "delayed"
    private double speed; // in km/h
    private double acceleration; // in m/s²
    private long stopDuration; // milliseconds
    private long lastMovementTime; // timestamp
    private String tripId; // current trip identifier

    // Empty constructor for Firebase
    public Bus() {}

    // Constructor
    public Bus(String busId, String busNumber) {
        this.busId = busId;
        this.busNumber = busNumber;
        this.isActive = false;
        this.routeId = "";
        this.driverId = "";
        this.createdAt = Timestamp.now();
        this.currentStatus = "on_route";

        this.speed = 0.0;
        this.acceleration = 0.0;
        this.stopDuration = 0;
        this.lastMovementTime = System.currentTimeMillis();
        this.tripId = "";
    }

    public Double getLatitude() {
        if (currentLocation != null && currentLocation.containsKey("latitude")) {
            Object lat = currentLocation.get("latitude");
            if (lat instanceof Double) return (Double) lat;
            if (lat instanceof Long) return ((Long) lat).doubleValue();
            if (lat instanceof Integer) return ((Integer) lat).doubleValue();
        }
        return null;
    }

    public Double getLongitude() {
        if (currentLocation != null && currentLocation.containsKey("longitude")) {
            Object lng = currentLocation.get("longitude");
            if (lng instanceof Double) return (Double) lng;
            if (lng instanceof Long) return ((Long) lng).doubleValue();
            if (lng instanceof Integer) return ((Integer) lng).doubleValue();
        }
        return null;
    }

    public String getNextStop() {
        return nextStop != null ? nextStop : "Unknown";
    }

    public long getEtaToNextStop() {
        return etaToNextStop;
    }

    // Getters and setters
    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getAcceleration() { return acceleration; }
    public void setAcceleration(double acceleration) { this.acceleration = acceleration; }

    public long getStopDuration() { return stopDuration; }
    public void setStopDuration(long stopDuration) { this.stopDuration = stopDuration; }

    public long getLastMovementTime() { return lastMovementTime; }
    public void setLastMovementTime(long lastMovementTime) { this.lastMovementTime = lastMovementTime; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Map<String, Object> currentLocation) { this.currentLocation = currentLocation; }

    public void setNextStop(String nextStop) { this.nextStop = nextStop; }

    public void setEtaToNextStop(long etaToNextStop) { this.etaToNextStop = etaToNextStop; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
}