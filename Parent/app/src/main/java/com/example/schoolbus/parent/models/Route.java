package com.example.schoolbus.parent.models;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

public class Route {
    private String routeId;
    private String routeName;
    private String busId;
    private List<Map<String, Object>> stops;
    private Timestamp createdAt;

    public Route() {}

    public Route(String routeId, String routeName, String busId) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.busId = busId;
        this.createdAt = Timestamp.now();
    }

    // Getters and Setters
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public List<Map<String, Object>> getStops() { return stops; }
    public void setStops(List<Map<String, Object>> stops) { this.stops = stops; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}