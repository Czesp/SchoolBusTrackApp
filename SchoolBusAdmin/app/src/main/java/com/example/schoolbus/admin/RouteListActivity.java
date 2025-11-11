package com.example.schoolbus.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteListActivity extends AppCompatActivity {

    private FloatingActionButton fabAddRoute;
    private LinearLayout layoutRoutesList, layoutEmptyState;
    private SwipeRefreshLayout swipeRefreshLayout;
    private MaterialToolbar toolbar;
    private ImageButton btnProfile;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_list);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupPullToRefresh();
        loadRoutes();
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        fabAddRoute = findViewById(R.id.fabAddRoute);
        layoutRoutesList = findViewById(R.id.layoutRoutesList);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        toolbar = findViewById(R.id.toolbar);
        btnProfile = findViewById(R.id.btnProfile);
    }

    /**
     * Setup toolbar with navigation
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupClickListeners() {
        fabAddRoute.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateRouteActivity.class));
        });

        btnProfile.setOnClickListener(v -> {
            openProfile();
        });
    }

    /**
     * Open profile activity
     */
    private void openProfile() {
        startActivity(new Intent(this, AdminProfileActivity.class));
    }

    private void setupPullToRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadRoutes();
        });
    }

    private void loadRoutes() {
        db.collection("routes")
                .get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (task.isSuccessful()) {
                        layoutRoutesList.removeAllViews();

                        if (task.getResult().isEmpty()) {
                            showEmptyState();
                        } else {
                            hideEmptyState();
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                addRouteToLayout(document);
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error loading routes: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        showEmptyState();
                    }
                });
    }

    /**
     * Show empty state when no routes exist
     */
    private void showEmptyState() {
        layoutEmptyState.setVisibility(View.VISIBLE);
        layoutRoutesList.setVisibility(View.GONE);
    }

    /**
     * Hide empty state when routes exist
     */
    private void hideEmptyState() {
        layoutEmptyState.setVisibility(View.GONE);
        layoutRoutesList.setVisibility(View.VISIBLE);
    }

    private void addRouteToLayout(QueryDocumentSnapshot document) {
        String routeId = document.getId();
        String routeName = document.getString("routeName");
        String busId = document.getString("busId");
        Object stopsObj = document.get("stops");

        int stopCount = 0;
        if (stopsObj != null && stopsObj instanceof java.util.List) {
            stopCount = ((java.util.List) stopsObj).size();
        }

        // Create route card container
        LinearLayout routeCard = new LinearLayout(this);
        routeCard.setOrientation(LinearLayout.VERTICAL);
        routeCard.setBackground(ContextCompat.getDrawable(this, R.drawable.route_card_background));
        routeCard.setElevation(4f);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        routeCard.setLayoutParams(cardParams);

        // Main content layout
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setPadding(20, 20, 16, 20);

        // Route info container (left side)
        LinearLayout routeInfoLayout = new LinearLayout(this);
        routeInfoLayout.setOrientation(LinearLayout.VERTICAL);
        routeInfoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        // Route name with icon
        TextView tvRouteName = new TextView(this);
        tvRouteName.setText("🗺️ " + (routeName != null ? routeName : "Unnamed Route"));
        tvRouteName.setTextSize(16);
        tvRouteName.setTextColor(ContextCompat.getColor(this, R.color.card_text_primary));
        tvRouteName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRouteName.setPadding(0, 0, 0, 8);

        // Route details
        TextView tvRouteDetails = new TextView(this);
        tvRouteDetails.setText("🚌 Loading bus... • 🚏 " + stopCount + " stops");
        tvRouteDetails.setTextSize(13);
        tvRouteDetails.setTextColor(ContextCompat.getColor(this, R.color.card_text_secondary));
        tvRouteDetails.setPadding(0, 0, 0, 4);

        // Load bus name
        if (busId != null && !busId.isEmpty()) {
            loadBusName(busId, tvRouteDetails, stopCount);
        } else {
            tvRouteDetails.setText("🚌 No bus assigned • 🚏 " + stopCount + " stops");
        }

        // Add to route info layout
        routeInfoLayout.addView(tvRouteName);
        routeInfoLayout.addView(tvRouteDetails);

        // Three dots menu (right side)
        ImageView ivMenu = new ImageView(this);
        ivMenu.setImageResource(R.drawable.ic_more_vert);
        ivMenu.setPadding(16, 16, 16, 16);
        ivMenu.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ivMenu.setClickable(true);
        ivMenu.setColorFilter(ContextCompat.getColor(this, R.color.card_text_secondary));
        ivMenu.setBackground(ContextCompat.getDrawable(this, R.drawable.icon_button_bg));

        // Set menu click listener
        ivMenu.setOnClickListener(v -> {
            showRouteMenu(v, routeId, routeName);
        });

        // Add both layouts to main content
        contentLayout.addView(routeInfoLayout);
        contentLayout.addView(ivMenu);

        // Add content to card
        routeCard.addView(contentLayout);

        // Set click listener for the whole card (for edit)
        routeCard.setOnClickListener(v -> {
            openEditRoute(routeId, routeName);
        });

        // Add ripple effect
        routeCard.setBackground(ContextCompat.getDrawable(this, R.drawable.ripple_effect));

        layoutRoutesList.addView(routeCard);
    }

    private void loadBusName(String busId, TextView textView, int stopCount) {
        db.collection("buses").document(busId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String busNumber = document.getString("busNumber");
                        textView.setText("🚌 " + (busNumber != null ? busNumber : "Unknown") + " • 🚏 " + stopCount + " stops");
                    } else {
                        textView.setText("🚌 Bus not found • 🚏 " + stopCount + " stops");
                    }
                })
                .addOnFailureListener(e -> {
                    textView.setText("🚌 Error loading bus • 🚏 " + stopCount + " stops");
                });
    }

    private void showRouteMenu(View anchorView, String routeId, String routeName) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add("Edit Route");
        popupMenu.getMenu().add("Delete Route");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Edit Route")) {
                openEditRoute(routeId, routeName);
                return true;
            } else if (item.getTitle().equals("Delete Route")) {
                showDeleteRouteConfirmation(routeId, routeName);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void openEditRoute(String routeId, String routeName) {
        // Get the complete route data including stops
        db.collection("routes").document(routeId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String busId = documentSnapshot.getString("busId");

                        Intent intent = new Intent(this, EditRouteActivity.class);
                        intent.putExtra("ROUTE_ID", routeId);
                        intent.putExtra("ROUTE_NAME", routeName);
                        intent.putExtra("ROUTE_BUS_ID", busId != null ? busId : "");
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading route data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteRouteConfirmation(String routeId, String routeName) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Route")
                .setMessage("Are you sure you want to delete '" + routeName + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteRoute(routeId);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteRoute(String routeId) {
        db.collection("routes").document(routeId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Route deleted successfully!", Toast.LENGTH_SHORT).show();
                    loadRoutes(); // Refresh the list

                    // Also remove this route from any buses that were using it
                    updateBusesAfterRouteDeletion(routeId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error deleting route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateBusesAfterRouteDeletion(String routeId) {
        db.collection("buses")
                .whereEqualTo("routeId", routeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("routeId", "");
                            db.collection("buses").document(document.getId()).update(updates);
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRoutes();
    }
}