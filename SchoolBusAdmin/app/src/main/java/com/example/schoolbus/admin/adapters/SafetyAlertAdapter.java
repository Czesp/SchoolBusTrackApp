package com.example.schoolbus.admin.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.schoolbus.admin.R;
import com.example.schoolbus.admin.models.SafetyAlert;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SafetyAlertAdapter extends RecyclerView.Adapter<SafetyAlertAdapter.AlertViewHolder> {

    public interface OnAlertClickListener {
        void onAlertClick(SafetyAlert alert);
    }

    private List<SafetyAlert> alertsList;
    private SimpleDateFormat dateFormat;
    private FirebaseFirestore db;
    private OnAlertClickListener clickListener;

    public SafetyAlertAdapter(List<SafetyAlert> alertsList, OnAlertClickListener clickListener) {
        this.alertsList = alertsList;
        this.dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        this.db = FirebaseFirestore.getInstance();
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_safety_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        SafetyAlert alert = alertsList.get(position);
        holder.bind(alert);

        // ADD CLICK LISTENER FOR THE ENTIRE ITEM
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAlertClick(alert);
            }
        });
    }

    @Override
    public int getItemCount() {
        return alertsList.size();
    }

    public void updateAlerts(List<SafetyAlert> newAlerts) {
        this.alertsList = newAlerts;
        notifyDataSetChanged();
    }

    class AlertViewHolder extends RecyclerView.ViewHolder {
        private TextView tvAlertType, tvAlertMessage, tvAlertTime, tvBusNumber;
        private MaterialButton btnResolve;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertType = itemView.findViewById(R.id.tv_alert_type);
            tvAlertMessage = itemView.findViewById(R.id.tv_alert_message);
            tvAlertTime = itemView.findViewById(R.id.tv_alert_time);
            tvBusNumber = itemView.findViewById(R.id.tv_bus_number);
            btnResolve = itemView.findViewById(R.id.btnResolve);
        }

        public void bind(SafetyAlert alert) {
            // Set alert type with icon
            String typeText = getAlertTypeWithIcon(alert.getAlertType());
            tvAlertType.setText(typeText);

            // Set message
            tvAlertMessage.setText(alert.getMessage());

            // Set bus number
            if (alert.getBusNumber() != null) {
                tvBusNumber.setText("Bus " + alert.getBusNumber());
            } else {
                tvBusNumber.setText("Bus Info");
            }

            // Set time with proper formatting
            if (alert.getTimestamp() != null) {
                try {
                    String time = dateFormat.format(alert.getTimestamp().toDate());
                    tvAlertTime.setText(time);
                } catch (Exception e) {
                    android.util.Log.e("TimeDebug", "Admin SafetyAlert - Error formatting time: " + e.getMessage());
                    tvAlertTime.setText("--:--");
                }
            } else {
                tvAlertTime.setText("--:--");
            }

            // Set background color based on severity
            setAlertSeverityStyle(alert.getSeverity());

            // Setup resolve button
            btnResolve.setOnClickListener(v -> {
                markAlertAsResolved(alert);
            });
        }

        private void markAlertAsResolved(SafetyAlert alert) {
            if (alert.getAlertId() != null) {
                db.collection("safety_alerts").document(alert.getAlertId())
                        .update("resolved", true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(itemView.getContext(),
                                    "Alert resolved successfully", Toast.LENGTH_SHORT).show();
                            // Remove from local list
                            alertsList.remove(getAdapterPosition());
                            notifyItemRemoved(getAdapterPosition());
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(itemView.getContext(),
                                    "Failed to resolve alert", Toast.LENGTH_SHORT).show();
                        });
            }
        }

        private String getAlertTypeWithIcon(String alertType) {
            switch (alertType) {
                case "SPEED_VIOLATION":
                    return "🚨 Speed Alert";
                case "ROUTE_DEVIATION":
                    return "📍 Route Alert";
                case "PROLONGED_STOP":
                    return "⏰ Stop Alert";
                default:
                    return "🚌 Safety Alert";
            }
        }

        private void setAlertSeverityStyle(String severity) {
            int backgroundColor;
            if ("HIGH".equals(severity)) {
                backgroundColor = itemView.getContext().getColor(R.color.alert_high);
            } else if ("MEDIUM".equals(severity)) {
                backgroundColor = itemView.getContext().getColor(R.color.alert_medium);
            } else {
                backgroundColor = itemView.getContext().getColor(R.color.alert_low);
            }

            itemView.setBackgroundColor(backgroundColor);
        }
    }
}