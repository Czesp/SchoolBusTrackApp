package com.example.schoolbus.parent.models;

import android.util.Log;

import com.google.firebase.Timestamp;
import java.util.Date;
import java.util.List;

public class NotificationHistory {
    private String notificationId;
    private String type; // "ARRIVAL_PREDICTION", "SAFETY_ALERT", "ROUTE_DEVIATION", etc.
    private String busNumber;
    private String routeId;
    private String nextStop;
    private Long etaMinutes;
    private String message;
    private Timestamp timestamp;
    private List<String> factors;
    private Boolean read;

    public NotificationHistory() {}

    public NotificationHistory(String type, String message, String busNumber, Timestamp timestamp) {
        this.type = type;
        this.message = message;
        this.busNumber = busNumber;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getNextStop() { return nextStop; }
    public void setNextStop(String nextStop) { this.nextStop = nextStop; }

    public Long getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Long etaMinutes) { this.etaMinutes = etaMinutes; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public List<String> getFactors() { return factors; }
    public void setFactors(List<String> factors) { this.factors = factors; }

    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }

    public Date getTimestampAsDate() {
        if (timestamp == null) {
            Log.w("NotificationHistory", "Timestamp is null, using current time");
            return new Date();
        }

        try {
            return timestamp.toDate();
        } catch (Exception e) {
            Log.e("NotificationHistory", "Error converting timestamp: " + e.getMessage());
            return new Date();
        }
    }
}