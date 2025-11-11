package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateUserActivity extends AppCompatActivity {

    private EditText etName, etPhone, etStudentId;
    private Spinner spinnerRole, spinnerBus;
    private Button btnGenerateCredentials, btnSaveUser, btnCancel;
    private TextView tvGeneratedEmail, tvGeneratedPassword, tvBusLabel, tvStudentId;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ImageButton btnProfile;
    private String generatedEmail = "";
    private String generatedPassword = "";
    private List<String> busList = new ArrayList<>();
    private List<String> busIdList = new ArrayList<>();
    private ArrayAdapter<String> busAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_user);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupRoleSpinner();
        setupBusSpinner();
        setupClickListeners();
        loadAvailableBuses();
    }

    private void initializeViews() {
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etStudentId = findViewById(R.id.etStudentId);
        spinnerRole = findViewById(R.id.spinnerRole);
        spinnerBus = findViewById(R.id.spinnerBus);
        btnGenerateCredentials = findViewById(R.id.btnGenerateCredentials);
        btnSaveUser = findViewById(R.id.btnSaveUser);
        btnCancel = findViewById(R.id.btnCancel);
        tvGeneratedEmail = findViewById(R.id.tvGeneratedEmail);
        tvGeneratedPassword = findViewById(R.id.tvGeneratedPassword);
        tvBusLabel = findViewById(R.id.tvBusLabel);
        tvStudentId = findViewById(R.id.tvStudentId);
        btnProfile = findViewById(R.id.btnProfile);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Set back button click listener
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRoleSpinner() {
        String[] roles = {"Select Role", "Driver", "Parent"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

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
                    } else {
                        Toast.makeText(this, "Error loading buses: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateRoleSpecificFields(String role) {
        TextView tvStudentIdLabel = findViewById(R.id.tvStudentId);
        TextInputLayout layoutStudentId = findViewById(R.id.layoutStudentId);
        TextView tvBusLabel = findViewById(R.id.tvBusLabel);
        MaterialCardView cardBusSpinner = findViewById(R.id.cardBusSpinner);

        if ("Driver".equals(role)) {
            // No bus assignment during driver creation
            layoutStudentId.setVisibility(View.GONE);
            tvStudentIdLabel.setVisibility(View.GONE);
            cardBusSpinner.setVisibility(View.GONE);
            tvBusLabel.setVisibility(View.GONE);
        } else if ("Parent".equals(role)) {
            // Show bus selection for parents
            layoutStudentId.setVisibility(View.VISIBLE);
            tvStudentIdLabel.setVisibility(View.VISIBLE);
            cardBusSpinner.setVisibility(View.VISIBLE);
            tvBusLabel.setVisibility(View.VISIBLE);
        } else {
            layoutStudentId.setVisibility(View.GONE);
            tvStudentIdLabel.setVisibility(View.GONE);
            cardBusSpinner.setVisibility(View.GONE);
            tvBusLabel.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnGenerateCredentials.setOnClickListener(v -> {
            generateCredentials();
        });

        btnSaveUser.setOnClickListener(v -> {
            createUser();
        });

        btnCancel.setOnClickListener(v -> {
            finish();
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProfileActivity.class));
        });
    }

    private void generateCredentials() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String role = spinnerRole.getSelectedItem().toString();

        if (name.isEmpty() || "Select Role".equals(role)) {
            Toast.makeText(this, "Please enter name and select role first", Toast.LENGTH_LONG).show();
            return;
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "Please enter phone number to generate credentials", Toast.LENGTH_LONG).show();
            return;
        }

        // Generate email in format: fullname + last 3 digits of phone + @school.com
        String username = name.toLowerCase().replace(" ", "");
        String phoneSuffix = phone.length() >= 3 ? phone.substring(phone.length() - 3) : "000";

        generatedEmail = username + "" + phoneSuffix + "@school.com";
        generatedPassword = generateRandomPassword();

        tvGeneratedEmail.setText("Email: " + generatedEmail);
        tvGeneratedPassword.setText("Password: " + generatedPassword);

        btnSaveUser.setEnabled(true);
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return password.toString();
    }

    private void createUser() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String role = spinnerRole.getSelectedItem().toString();
        String studentId = etStudentId.getText().toString().trim();
        String selectedBusId = getSelectedBusId();

        // Validation
        if (name.isEmpty() || generatedEmail.isEmpty() || generatedPassword.isEmpty()) {
            Toast.makeText(this, "Please generate credentials first", Toast.LENGTH_LONG).show();
            return;
        }

        if ("Select Role".equals(role)) {
            Toast.makeText(this, "Please select a role", Toast.LENGTH_LONG).show();
            return;
        }

        // For parents - additional validation
        if ("Parent".equals(role)) {
            if (studentId.isEmpty()) {
                Toast.makeText(this, "Please enter student ID", Toast.LENGTH_LONG).show();
                return;
            }
            if (selectedBusId.isEmpty()) {
                Toast.makeText(this, "Please select a bus", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // For parents, check if student ID already exists and is not assigned
        if ("Parent".equals(role)) {
            checkStudentAssignment(studentId, name, phone, role, selectedBusId);
        } else {
            // For drivers, no bus assignment during creation
            createUserInFirebase(name, phone, role, "", studentId);
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
        // Check if this student is already assigned to any parent
        db.collection("users")
                .whereEqualTo("role", "parent")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Student not assigned to any parent, check if student exists
                            checkStudentExists(studentId, name, phone, role, busId);
                        } else {
                            // Student already assigned to another parent
                            Toast.makeText(this,
                                    "Student ID " + studentId + " is already assigned to another parent!",
                                    Toast.LENGTH_LONG).show();
                            btnSaveUser.setEnabled(true);
                        }
                    } else {
                        Toast.makeText(this, "Error checking student assignment: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        btnSaveUser.setEnabled(true);
                    }
                });
    }

    private void checkStudentExists(String studentId, String name, String phone, String role, String busId) {
        db.collection("students").document(studentId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        // Student exists, update bus assignment
                        updateStudentBusAssignment(studentId, busId, name, phone, role);
                    } else {
                        // Student doesn't exist, create one with bus assignment
                        createStudentAndUser(name, phone, role, studentId, busId);
                    }
                });
    }

    private void createStudentAndUser(String name, String phone, String role, String studentId, String busId) {
        Map<String, Object> student = new HashMap<>();
        student.put("studentId", studentId);
        student.put("name", name + "'s Child");
        student.put("parentName", name);
        student.put("busId", busId); // Assign the selected bus
        student.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("students").document(studentId)
                .set(student)
                .addOnSuccessListener(aVoid -> {
                    createUserInFirebase(name, phone, role, busId, studentId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error creating student: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSaveUser.setEnabled(true);
                });
    }

    private void updateStudentBusAssignment(String studentId, String busId, String name, String phone, String role) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("busId", busId);
        updates.put("parentName", name);

        db.collection("students").document(studentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    createUserInFirebase(name, phone, role, busId, studentId);
                })
                .addOnFailureListener(e -> {
                    // If update fails, try set as fallback
                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("studentId", studentId);
                    studentData.put("name", "Student " + studentId);
                    studentData.put("parentName", name);
                    studentData.put("busId", busId);
                    studentData.put("createdAt", com.google.firebase.Timestamp.now());

                    db.collection("students").document(studentId)
                            .set(studentData)
                            .addOnSuccessListener(aVoid2 -> {
                                createUserInFirebase(name, phone, role, busId, studentId);
                            })
                            .addOnFailureListener(e2 -> {
                                Toast.makeText(this, "Error updating student: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                                btnSaveUser.setEnabled(true);
                            });
                });
    }

    private void createUserInFirebase(String name, String phone, String role, String busId, String studentId) {
        // Show loading
        btnSaveUser.setEnabled(false);
        btnSaveUser.setText("Creating User...");

        // Create user in Firebase Authentication
        auth.createUserWithEmailAndPassword(generatedEmail, generatedPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();

                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build();

                        auth.getCurrentUser().updateProfile(profileUpdates)
                                .addOnCompleteListener(profileTask -> {
                                    saveUserToFirestore(userId, name, generatedEmail, role, phone, busId, studentId);
                                });
                    } else {
                        Toast.makeText(CreateUserActivity.this,
                                "Error creating user: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        btnSaveUser.setEnabled(true);
                        btnSaveUser.setText("SAVE");
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email, String role,
                                     String phone, String busId, String studentId) {
        // Convert role to lowercase for database consistency
        String dbRole = role.toLowerCase();

        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId);
        user.put("name", name);
        user.put("email", email);
        user.put("role", dbRole); // Store as lowercase in database
        user.put("phone", phone);
        user.put("busId", busId); // This will be empty for drivers, filled for parents
        user.put("studentId", studentId);
        user.put("generatedPassword", generatedPassword);
        user.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CreateUserActivity.this,
                            "User '" + name + "' created successfully!\nEmail: " + generatedEmail + "\nPassword: " + generatedPassword,
                            Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(CreateUserActivity.this,
                            "Error saving user data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    btnSaveUser.setEnabled(true);
                    btnSaveUser.setText("SAVE");
                });
    }
}