package com.example.schoolbus.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.schoolbus.admin.adapters.BusAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class BusListActivity extends AppCompatActivity {

    private RecyclerView recyclerViewBuses;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabAddBus;
    private View emptyStateLayout;
    private ImageButton btnProfile;

    private FirebaseFirestore db;
    private BusAdapter adapter;
    private List<BusItem> busList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_list);

        db = FirebaseFirestore.getInstance();
        busList = new ArrayList<>();

        initViews();
        setupRecyclerView();
        setupListeners();
        loadBuses();
    }

    private void initViews() {
        recyclerViewBuses = findViewById(R.id.recyclerViewBuses);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        fabAddBus = findViewById(R.id.fabAddBus);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        btnProfile = findViewById(R.id.btnProfile);

        // Customize swipe refresh colors
        swipeRefreshLayout.setColorSchemeResources(
                R.color.dashboard_accent_blue,
                R.color.dashboard_accent_yellow
        );
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.white);
    }

    private void setupRecyclerView() {
        adapter = new BusAdapter(this, busList, new BusAdapter.BusActionListener() {
            @Override
            public void onBusClick(BusItem bus) {
                openEditBus(bus);
            }

            @Override
            public void onMenuClick(View anchor, BusItem bus) {
                showBusMenu(anchor, bus);
            }
        });

        recyclerViewBuses.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewBuses.setAdapter(adapter);
        recyclerViewBuses.setHasFixedSize(true);
    }

    private void setupListeners() {
        fabAddBus.setOnClickListener(v -> startActivity(new Intent(this, CreateBusActivity.class)));

        swipeRefreshLayout.setOnRefreshListener(() -> loadBuses());

        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, AdminProfileActivity.class)));

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                onBackPressed(); // or finish();
            });
        }
    }

    private void loadBuses() {
        // Show loading state using SwipeRefreshLayout instead of progressBar
        if (!swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        db.collection("buses")
                .get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (task.isSuccessful()) {
                        busList.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            BusItem bus = new BusItem(
                                    doc.getId(),
                                    doc.getString("busNumber"),
                                    doc.getString("routeId"),
                                    doc.getString("driverId"),
                                    doc.getBoolean("isActive") != null && doc.getBoolean("isActive")
                            );
                            busList.add(bus);
                        }
                        updateUI();
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "Error loading buses: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        updateUI(); // Still update UI even on error
                    }
                });
    }

    private void updateUI() {
        if (busList.isEmpty()) {
            recyclerViewBuses.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerViewBuses.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void openEditBus(BusItem bus) {
        Intent intent = new Intent(this, EditBusActivity.class);
        intent.putExtra("BUS_ID", bus.id);
        intent.putExtra("BUS_NUMBER", bus.busNumber);
        intent.putExtra("ROUTE_ID", bus.routeId != null ? bus.routeId : "");
        intent.putExtra("DRIVER_ID", bus.driverId != null ? bus.driverId : "");
        intent.putExtra("IS_ACTIVE", bus.isActive);
        startActivity(intent);
    }

    private void showBusMenu(View anchor, BusItem bus) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add("Edit");
        popupMenu.getMenu().add("Delete");

        popupMenu.setOnMenuItemClickListener(item -> {
            if ("Edit".equals(item.getTitle())) {
                openEditBus(bus);
                return true;
            } else if ("Delete".equals(item.getTitle())) {
                showDeleteConfirmation(bus);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showDeleteConfirmation(BusItem bus) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Bus")
                .setMessage("Delete '" + bus.busNumber + "' permanently?")
                .setPositiveButton("Delete", (dialog, which) -> deleteBus(bus))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteBus(BusItem bus) {
        db.collection("buses").document(bus.id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Bus deleted successfully!", Toast.LENGTH_SHORT).show();
                    loadBuses();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error deleting bus: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBuses();
    }

    // ──────────────────────────────────────────────
    // DATA MODEL
    // ──────────────────────────────────────────────

    public static class BusItem {
        String id;
        public String busNumber;
        public String routeId;
        public String driverId;
        boolean isActive;

        public BusItem(String id, String busNumber, String routeId, String driverId, boolean isActive) {
            this.id = id;
            this.busNumber = busNumber != null ? busNumber : "Unknown";
            this.routeId = routeId;
            this.driverId = driverId;
            this.isActive = isActive;
        }
    }
}