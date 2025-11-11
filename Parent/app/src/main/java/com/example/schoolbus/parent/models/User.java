package com.example.schoolbus.parent.models;

import com.google.firebase.Timestamp;

import java.util.Map;

public class User {
    private String userId;
    private String email;
    private String name;
    private String role;
    private String phone;
    private String busId;
    private String studentId;
    private Timestamp createdAt;

    private Boolean isOnline; // ← ADD THIS FIELD
    private String fcmToken;


    public User() {}

    public User(String userId, String email, String name, String role) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = Timestamp.now();
        this.isOnline = false; // Default to offline
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken;}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // In your User model class, add this method:
    public static User fromMap(Map<String, Object> data) {
        User user = new User();
        if (data.containsKey("userId")) {
            user.setUserId((String) data.get("userId"));
        } else if (data.containsKey("uid")) {
            user.setUserId((String) data.get("uid"));
        }

        if (data.containsKey("name")) user.setName((String) data.get("name"));
        if (data.containsKey("email")) user.setEmail((String) data.get("email"));
        if (data.containsKey("phone")) user.setPhone((String) data.get("phone"));
        if (data.containsKey("role")) user.setRole((String) data.get("role"));
        if (data.containsKey("busId")) user.setBusId((String) data.get("busId"));

        return user;
    }
}