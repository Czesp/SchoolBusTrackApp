package com.example.schoolbus.admin.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.schoolbus.admin.R;
import com.example.schoolbus.admin.models.NotificationHistory;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AdminNotificationHistoryAdapter extends RecyclerView.Adapter<AdminNotificationHistoryAdapter.NotificationViewHolder> {

    private List<NotificationHistory> notificationsList;
    private OnNotificationClickListener clickListener;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

    public AdminNotificationHistoryAdapter(List<NotificationHistory> notificationsList) {
        this.notificationsList = notificationsList;
    }

    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_history, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationHistory notification = notificationsList.get(position);
        holder.bind(notification);

        // Set click listener for the entire item
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onNotificationClick(notification);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notificationsList.size();
    }

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationHistory notification);
    }

    public void updateNotifications(List<NotificationHistory> newNotifications) {
        this.notificationsList = newNotifications;
        notifyDataSetChanged();
    }

    public void markNotificationAsRead(int position) {
        if (position >= 0 && position < notificationsList.size()) {
            NotificationHistory notification = notificationsList.get(position);
            notification.setRead(true);
            notifyItemChanged(position);
        }
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMessage, tvTime, tvBusInfo, tvType;
        private View viewStatusIndicator;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvBusInfo = itemView.findViewById(R.id.tv_bus_info);
            tvType = itemView.findViewById(R.id.tv_type);
            viewStatusIndicator = itemView.findViewById(R.id.view_status_indicator);
        }

        void bind(NotificationHistory notification) {
            // Set message
            tvMessage.setText(notification.getMessage());

            // Handle timestamp with proper formatting
            if (notification.getTimestamp() != null) {
                try {
                    java.util.Date date = notification.getTimestampAsDate();
                    String timeText = formatTimeForDisplay(date);
                    tvTime.setText(timeText);

                    // Debug logging
                    android.util.Log.d("TimeDebug", "Admin - Formatted: " + timeText +
                            " from timestamp: " + notification.getTimestamp() +
                            " type: " + notification.getTimestamp().getClass().getSimpleName());
                } catch (Exception e) {
                    android.util.Log.e("TimeDebug", "Admin - Error formatting time: " + e.getMessage());
                    tvTime.setText("Time Error");
                }
            } else {
                tvTime.setText("No Time");
                android.util.Log.d("TimeDebug", "Admin - Timestamp is null");
            }

            // Set bus info
            if (notification.getBusNumber() != null && !notification.getBusNumber().isEmpty()) {
                tvBusInfo.setText("Bus " + notification.getBusNumber());
                tvBusInfo.setVisibility(View.VISIBLE);
            } else {
                tvBusInfo.setVisibility(View.GONE);
            }

            // Set notification type with appropriate styling
            String typeText = getTypeDisplayText(notification.getType());
            tvType.setText(typeText);

            int backgroundRes = getTypeBackground(notification.getType());
            tvType.setBackgroundResource(backgroundRes);

            // Handle read/unread status
            boolean isRead = notification.getRead() != null && notification.getRead();
            if (isRead) {
                viewStatusIndicator.setVisibility(View.GONE);
                // Optional: Slightly fade read notifications
                itemView.setAlpha(0.8f);
            } else {
                viewStatusIndicator.setVisibility(View.VISIBLE);
                itemView.setAlpha(1.0f);
            }
        }

        private String formatTimeForDisplay(java.util.Date date) {
            java.util.Date now = new java.util.Date();
            long diff = now.getTime() - date.getTime();
            long hours = diff / (60 * 60 * 1000);

            if (hours < 24) {
                // Today - show only time
                return timeFormat.format(date);
            } else {
                // Older than 24 hours - show date and time
                return dateFormat.format(date);
            }
        }

        private String getTypeDisplayText(String type) {
            if (type == null) return "📋 Info";

            switch (type.toUpperCase()) {
                case "ARRIVAL_PREDICTION":
                    return "🚌 Arrival Prediction";
                case "SAFETY_ALERT":
                    return "🚨 Safety Alert";
                case "ROUTE_DEVIATION":
                    return "📍 Route Alert";
                case "SPEED_VIOLATION":
                    return "⚡ Speed Alert";
                case "PROLONGED_STOP":
                    return "⏰ Stop Alert";
                case "MAINTENANCE_ALERT":
                    return "🔧 Maintenance";
                default:
                    return "📋 " + type;
            }
        }

        private int getTypeBackground(String type) {
            if (type == null) return R.drawable.bg_notification_info;

            switch (type.toUpperCase()) {
                case "ARRIVAL_PREDICTION":
                    return R.drawable.bg_notification_arrival;
                case "SAFETY_ALERT":
                case "ROUTE_DEVIATION":
                case "SPEED_VIOLATION":
                case "PROLONGED_STOP":
                    return R.drawable.bg_notification_safety;
                case "MAINTENANCE_ALERT":
                    return R.drawable.bg_notification_info;
                default:
                    return R.drawable.bg_notification_info;
            }
        }
    }
}