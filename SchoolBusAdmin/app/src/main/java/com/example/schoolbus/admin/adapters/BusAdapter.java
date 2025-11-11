package com.example.schoolbus.admin.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.schoolbus.admin.BusListActivity;
import com.example.schoolbus.admin.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class BusAdapter extends RecyclerView.Adapter<BusAdapter.BusVH> {

    private final Context context;
    private final List<BusListActivity.BusItem> buses;
    private final BusActionListener listener;

    public interface BusActionListener {
        void onBusClick(BusListActivity.BusItem bus);
        void onMenuClick(View anchor, BusListActivity.BusItem bus);
    }

    public BusAdapter(Context context, List<BusListActivity.BusItem> buses, BusActionListener listener) {
        this.context = context;
        this.buses = buses;
        this.listener = listener;
    }

    @NonNull @Override
    public BusVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new BusVH(LayoutInflater.from(context).inflate(R.layout.item_bus_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull BusVH holder, int position) {
        BusListActivity.BusItem bus = buses.get(position);
        holder.bind(bus, listener);
    }

    @Override
    public int getItemCount() {
        return buses.size();
    }

    static class BusVH extends RecyclerView.ViewHolder {
        TextView tvBusNumber, tvRoute, tvDriver;
        ImageButton btnMenu;

        public BusVH(@NonNull View itemView) {
            super(itemView);
            tvBusNumber = itemView.findViewById(R.id.tvBusNumber);
            tvRoute = itemView.findViewById(R.id.tvRoute);
            tvDriver = itemView.findViewById(R.id.tvDriver);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }

        void bind(BusListActivity.BusItem bus, BusActionListener listener) {
            tvBusNumber.setText(bus.busNumber);
            tvRoute.setText("Route: Loading...");
            tvDriver.setText("Driver: Loading...");

            itemView.setOnClickListener(v -> listener.onBusClick(bus));
            btnMenu.setOnClickListener(v -> listener.onMenuClick(btnMenu, bus));

            // Load route name
            if (bus.routeId != null && !bus.routeId.isEmpty()) {
                FirebaseFirestore.getInstance().collection("routes").document(bus.routeId)
                        .get().addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String name = doc.getString("routeName");
                                tvRoute.setText("Route: " + (name != null ? name : "Unknown"));
                            } else {
                                tvRoute.setText("Route: Not found");
                            }
                        }).addOnFailureListener(e -> tvRoute.setText("Route: Error"));
            } else {
                tvRoute.setText("Route: Not assigned");
            }

            // Load driver name
            if (bus.driverId != null && !bus.driverId.isEmpty()) {
                FirebaseFirestore.getInstance().collection("users").document(bus.driverId)
                        .get().addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String name = doc.getString("name");
                                tvDriver.setText("Driver: " + (name != null ? name : "Unknown"));
                            } else {
                                tvDriver.setText("Driver: Not found");
                            }
                        }).addOnFailureListener(e -> tvDriver.setText("Driver: Error"));
            } else {
                tvDriver.setText("Driver: Not assigned");
            }
        }
    }
}