package com.example.schoolbus.driver.models;

import com.google.firebase.Timestamp;

public class SafetyAlert {
    private String alertId;
    private String alertType; // "SPEED_VIOLATION", "ROUTE_DEVIATION", "PROLONGED_STOP"
    private String message;
    private String busId;
    private String busNumber;
    private String routeId;
    private Timestamp timestamp;
    private String severity; // "LOW", "MEDIUM", "HIGH"
    private boolean resolved;

    // Empty constructor for Firebase
    public SafetyAlert() {}

    // Constructor for quick creation
    public SafetyAlert(String alertType, String message, String busId) {
        this.alertType = alertType;
        this.message = message;
        this.busId = busId;
        this.timestamp = Timestamp.now();
        this.severity = "MEDIUM";
        this.resolved = false;
    }

    // Getters and setters
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}