package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditUserActivity extends AppCompatActivity {

    private TextView tvUserEmail, tvBusLabel;
    private EditText etName, etPhone, etStudentId;
    private Spinner spinnerRole, spinnerBus;
    private Button btnSave, btnCancel;
    private FirebaseFirestore db;

    private ImageView btnProfile;

    private String userId, userEmail, userName, userRole, userPhone, userStudentId, userBusId;
    private List<String> busList = new ArrayList<>();
    private List<String> busIdList = new ArrayList<>();
    private ArrayAdapter<String> busAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        db = FirebaseFirestore.getInstance();

        initializeViews();
        getUserDataFromIntent();
        setupRoleSpinner();
        setupBusSpinner();
        loadAvailableBuses();
        setupClickListeners();
        loadUserData();
    }

    private void initializeViews() {
        tvUserEmail = findViewById(R.id.tvUserEmail);
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etStudentId = findViewById(R.id.etStudentId);
        spinnerRole = findViewById(R.id.spinnerRole);
        spinnerBus = findViewById(R.id.spinnerBus);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        tvBusLabel = findViewById(R.id.tvBusLabel);
        btnProfile = findViewById(R.id.btnProfile);

        // Set up toolbar with back button
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Set back button click listener
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void getUserDataFromIntent() {
        userId = getIntent().getStringExtra("USER_ID");
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        userName = getIntent().getStringExtra("USER_NAME");
        userRole = getIntent().getStringExtra("USER_ROLE");
        userPhone = getIntent().getStringExtra("USER_PHONE");
        userStudentId = getIntent().getStringExtra("USER_STUDENT_ID");
        userBusId = getIntent().getStringExtra("USER_BUS_ID");

        tvUserEmail.setText("Editing: " + userEmail);
    }

    private void setupRoleSpinner() {
        String[] roles = {"Driver", "Parent"}; // Changed to capitalized
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        // Set current role (convert to capitalized for display)
        String displayRole = capitalizeFirstLetter(userRole);
        if (displayRole != null) {
            for (int i = 0; i < spinnerRole.getCount(); i++) {
                if (spinnerRole.getItemAtPosition(i).toString().equals(displayRole)) {
                    spinnerRole.setSelection(i);
                    break;
                }
            }
        }

        // Show/hide fields based on role
        spinnerRole.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedRole = parent.getItemAtPosition(position).toString();
                updateRoleSpecificFields(selectedRole);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    private void setupBusSpinner() {
        busList.add("Select Bus");
        busIdList.add("");
        busAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, busList);
        busAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBus.setAdapter(busAdapter);
    }

    private void loadAvailableBuses() {
        db.collection("buses")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        busList.clear();
                        busIdList.clear();

                        busList.add("Select Bus");
                        busIdList.add("");

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String busId = document.getId();
                            String busNumber = document.getString("busNumber");
                            if (busNumber != null) {
                                busList.add(busNumber);
                                busIdList.add(busId);
                            }
                        }
                        busAdapter.notifyDataSetChanged();

                        // Set current bus selection if user has one
                        if (userBusId != null && !userBusId.isEmpty()) {
                            setCurrentBusSelection(userBusId);
                        }
                    } else {
                        Toast.makeText(this, "Error loading buses: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setCurrentBusSelection(String busId) {
        for (int i = 0; i < busIdList.size(); i++) {
            if (busIdList.get(i).equals(busId)) {
                spinnerBus.setSelection(i);
                break;
            }
        }
    }

    private void updateRoleSpecificFields(String role) {
        TextView tvStudentIdLabel = findViewById(R.id.tvStudentId);
        TextInputLayout layoutStudentId = findViewById(R.id.layoutStudentId);
        TextView tvBusLabel = findViewById(R.id.tvBusLabel);
        MaterialCardView cardBusSpinner = findViewById(R.id.cardBusSpinner);

        if ("Driver".equals(role)) {
            // Show bus assignment status (read-only)
            showBusAssignmentStatus();
            layoutStudentId.setVisibility(View.GONE);
            tvStudentIdLabel.setVisibility(View.GONE);
            cardBusSpinner.setVisibility(View.GONE);
            tvBusLabel.setVisibility(View.GONE);
        } else if ("Parent".equals(role)) {
            hideBusAssignmentStatus();
            layoutStudentId.setVisibility(View.VISIBLE);
            tvStudentIdLabel.setVisibility(View.VISIBLE);
            cardBusSpinner.setVisibility(View.VISIBLE);
            tvBusLabel.setVisibility(View.VISIBLE);
        }
    }

    private void showBusAssignmentStatus() {
        TextView tvBusStatus = findViewById(R.id.tvBusAssignment);
        if (tvBusStatus != null) {
            tvBusStatus.setVisibility(View.VISIBLE);

            if (userBusId != null && !userBusId.isEmpty()) {
                // Load bus name instead of showing ID
                loadBusNameForDisplay(userBusId, tvBusStatus);
            } else {
                tvBusStatus.setText("🚌 No bus assigned");
            }
        }
    }

    private void loadBusNameForDisplay(String busId, TextView textView) {
        db.collection("buses").document(busId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String busNumber = document.getString("busNumber");
                        textView.setText("🚌 Currently assigned to: " + (busNumber != null ? busNumber : "Unknown Bus"));
                    } else {
                        textView.setText("🚌 Currently assigned to: Bus not found");
                    }
                })
                .addOnFailureListener(e -> {
                    textView.setText("🚌 Currently assigned to: Error loading");
                });
    }

    private void hideBusAssignmentStatus() {
        TextView tvBusStatus = findViewById(R.id.tvBusAssignment);
        if (tvBusStatus != null) {
            tvBusStatus.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> {
            saveUserChanges();
        });

        btnCancel.setOnClickListener(v -> {
            finish();
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void loadUserData() {
        // Set current data
        etName.setText(userName);
        etPhone.setText(userPhone != null ? userPhone : "");

        // Show student ID only if user is a parent
        if ("parent".equals(userRole) && userStudentId != null) {
            etStudentId.setText(userStudentId);
        }

        // Update fields visibility based on current role
        updateRoleSpecificFields(capitalizeFirstLetter(userRole));
    }

    private void saveUserChanges() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String role = spinnerRole.getSelectedItem().toString();
        String studentId = etStudentId.getText().toString().trim();
        String selectedBusId = getSelectedBusId();

        // Validation
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter name", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("Parent".equals(role)) {
            if (studentId.isEmpty()) {
                Toast.makeText(this, "Please enter student ID", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedBusId.isEmpty()) {
                Toast.makeText(this, "Please select a bus", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // For parent role change, validate student ID
        if ("Parent".equals(role)) {
            checkStudentAssignment(studentId, name, phone, role, selectedBusId);
        } else {
            updateUserInFirestore(name, phone, role, "", selectedBusId);
        }
    }

    private String getSelectedBusId() {
        int position = spinnerBus.getSelectedItemPosition();
        if (position > 0 && position < busIdList.size()) {
            return busIdList.get(position);
        }
        return "";
    }

    private void checkStudentAssignment(String studentId, String name, String phone, String role, String busId) {
        // Check if student is already assigned to another parent
        db.collection("users")
                .whereEqualTo("role", "parent")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Student not assigned, proceed
                            updateStudentAndUser(name, phone, role, studentId, busId);
                        } else {
                            // Check if it's the same user (updating their own student ID)
                            boolean isSameUser = false;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                if (document.getId().equals(userId)) {
                                    isSameUser = true;
                                    break;
                                }
                            }

                            if (isSameUser) {
                                // Same user updating their student ID
                                updateStudentAndUser(name, phone, role, studentId, busId);
                            } else {
                                Toast.makeText(this,
                                        "Student ID " + studentId + " is already assigned to another parent!",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error checking student assignment", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateStudentAndUser(String name, String phone, String role, String studentId, String busId) {
        // Update student's bus assignment
        Map<String, Object> studentUpdates = new HashMap<>();
        studentUpdates.put("busId", busId);
        studentUpdates.put("parentName", name);

        db.collection("students").document(studentId)
                .update(studentUpdates)
                .addOnSuccessListener(aVoid -> {
                    updateUserInFirestore(name, phone, role, studentId, busId);
                })
                .addOnFailureListener(e -> {
                    // If student doesn't exist, create one
                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("studentId", studentId);
                    studentData.put("name", name + "'s Child");
                    studentData.put("parentName", name);
                    studentData.put("busId", busId);
                    studentData.put("createdAt", com.google.firebase.Timestamp.now());

                    db.collection("students").document(studentId)
                            .set(studentData)
                            .addOnSuccessListener(aVoid2 -> {
                                updateUserInFirestore(name, phone, role, studentId, busId);
                            })
                            .addOnFailureListener(e2 -> {
                                Toast.makeText(this, "Error updating student: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void updateUserInFirestore(String name, String phone, String role, String studentId, String busId) {
        // Convert role to lowercase for database consistency
        String dbRole = role.toLowerCase();

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("role", dbRole);

        // Update role-specific fields
        if ("Parent".equals(role)) {
            updates.put("studentId", studentId);
            updates.put("busId", busId); // Set the selected bus for parents
        } else {
            updates.put("studentId", ""); // Clear student ID for drivers
            updates.put("busId", ""); // Clear bus assignment for drivers
        }

        // If changing from driver to parent, remove from all buses
        if ("Parent".equals(role) && "driver".equals(userRole)) {
            removeDriverFromAllBuses(userId);
        }

        btnSave.setEnabled(false);
        btnSave.setText("SAVING...");

        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("SAVE CHANGES");
                });
    }

    private void removeDriverFromAllBuses(String driverId) {
        db.collection("buses")
                .whereEqualTo("driverId", driverId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> busUpdates = new HashMap<>();
                            busUpdates.put("driverId", "");
                            db.collection("buses").document(document.getId()).update(busUpdates);
                        }
                    }
                });
    }
}