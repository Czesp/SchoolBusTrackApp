package com.example.schoolbus.driver.models;

import com.google.firebase.Timestamp;

public class User {
    private String userId;
    private String email;
    private String name;
    private String role; // "admin", "driver", "parent"
    private String phone;
    private String busId; // For drivers: which bus they drive
    private String studentId; // For parents: which student they're parent of
    private Timestamp createdAt;  // Changed from String to Timestamp

    private Boolean isOnline; // ← ADD THIS FIELD
    private String fcmToken;


    // Empty constructor for Firebase
    public User() {}

    // Constructor
    public User(String userId, String email, String name, String role) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = Timestamp.now();  // Set current timestamp
        this.isOnline = false; // Default to offline
    }

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }


    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public Timestamp getCreatedAt() { return createdAt; }  // Updated
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }  // Updated
}