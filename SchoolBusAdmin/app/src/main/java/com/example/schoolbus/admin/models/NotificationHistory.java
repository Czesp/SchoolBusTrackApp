package com.example.schoolbus.admin.models;

import android.util.Log;

import com.google.firebase.Timestamp;

import java.util.Date;


public class NotificationHistory {
    private String notificationId;
    private String type; // "ARRIVAL_PREDICTION", "SAFETY_ALERT"
    private String busNumber;
    private String routeId;
    private String nextStop;
    private Long etaMinutes;
    private String message;
    private Object timestamp;
    private java.util.List<String> factors;
    private Boolean read; // Add this field for admin

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

    public Object getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public java.util.List<String> getFactors() { return factors; }
    public void setFactors(java.util.List<String> factors) { this.factors = factors; }

    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }

    public java.util.Date getTimestampAsDate() {
        if (timestamp == null) {
            Log.e("NotificationHistory", "Timestamp is null, using current time");
            return new Date();
        }

        // DEBUG: Log the timestamp type and value
        Log.d("TimestampDebug", "Timestamp type: " + timestamp.getClass().getSimpleName() + ", value: " + timestamp.toString());

        if (timestamp instanceof Long) {
            long millis = (Long) timestamp;
            Log.d("TimestampDebug", "Processing Long timestamp: " + millis + " = " + new Date(millis).toString());
            return new Date(millis);
        } else if (timestamp instanceof Integer) {
            long millis = ((Integer) timestamp).longValue();
            Log.d("TimestampDebug", "Processing Integer timestamp: " + millis + " = " + new Date(millis).toString());
            return new Date(millis);
        } else if (timestamp instanceof Double) {
            long millis = ((Double) timestamp).longValue();
            Log.d("TimestampDebug", "Processing Double timestamp: " + millis + " = " + new Date(millis).toString());
            return new Date(millis);
        } else if (timestamp instanceof Timestamp) {
            Date date = ((Timestamp) timestamp).toDate();
            Log.d("TimestampDebug", "Processing Timestamp: " + date.toString());
            return date;
        } else if (timestamp instanceof Date) {
            Log.d("TimestampDebug", "Processing Date: " + timestamp.toString());
            return (Date) timestamp;
        }

        Log.e("NotificationHistory", "Unknown timestamp type: " + timestamp.getClass().getSimpleName());
        return new Date();
    }
}