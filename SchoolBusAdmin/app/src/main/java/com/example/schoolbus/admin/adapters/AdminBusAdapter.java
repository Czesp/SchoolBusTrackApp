package com.example.schoolbus.admin.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.schoolbus.admin.R;
import com.example.schoolbus.admin.models.Bus;
import java.util.List;
import java.util.Map;

public class AdminBusAdapter extends RecyclerView.Adapter<AdminBusAdapter.BusViewHolder> {

    private List<Bus> busList;
    private Map<String, String> busDriverNames;
    private OnBusClickListener onBusClickListener;

    public interface OnBusClickListener {
        void onBusClick(Bus bus);
    }

    public AdminBusAdapter(List<Bus> busList, Map<String, String> busDriverNames, OnBusClickListener onBusClickListener) {
        this.busList = busList;
        this.busDriverNames = busDriverNames;
        this.onBusClickListener = onBusClickListener;
    }

    public void updateData(List<Bus> newBusList, Map<String, String> newBusDriverNames) {
        this.busList = newBusList;
        this.busDriverNames = newBusDriverNames;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bus, parent, false);
        return new BusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BusViewHolder holder, int position) {
        Bus bus = busList.get(position);
        holder.bind(bus, busDriverNames, onBusClickListener);
    }

    @Override
    public int getItemCount() {
        return busList != null ? busList.size() : 0;
    }

    static class BusViewHolder extends RecyclerView.ViewHolder {
        private TextView tvBusNumber, tvDriverName, tvNextStop, tvETA, tvStatus;

        public BusViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusNumber = itemView.findViewById(R.id.tv_bus_number);
            tvDriverName = itemView.findViewById(R.id.tv_driver_name);
            tvNextStop = itemView.findViewById(R.id.tv_next_stop);
            tvETA = itemView.findViewById(R.id.tv_eta);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }

        public void bind(Bus bus, Map<String, String> busDriverNames, OnBusClickListener listener) {
            if (bus == null) return;

            tvBusNumber.setText("Bus " + (bus.getBusNumber() != null ? bus.getBusNumber() : "N/A"));

            String driverName = busDriverNames != null ? busDriverNames.get(bus.getBusId()) : null;
            tvDriverName.setText(driverName != null ? driverName : "Driver not assigned");

            tvNextStop.setText(bus.getNextStop() != null ? bus.getNextStop() : "Unknown");
            tvETA.setText(formatETA(bus.getEtaToNextStop()));

            // Set status with color coding
            setStatus(bus.getCurrentStatus());

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onBusClick(bus));
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private String formatETA(long eta) {
            if (eta <= 0) return "Unknown";
            return eta + " min";
        }

        private void setStatus(String status) {
            if (status == null) {
                tvStatus.setText("Unknown");
                tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
                return;
            }

            switch (status) {
                case "on_route":
                    tvStatus.setText("On Route");
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.holo_green_light));
                    break;
                case "at_stop":
                    tvStatus.setText("At Stop");
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.holo_orange_light));
                    break;
                case "delayed":
                    tvStatus.setText("Delayed");
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.holo_red_light));
                    break;
                default:
                    tvStatus.setText("Unknown");
                    tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
                    break;
            }
        }
    }
}