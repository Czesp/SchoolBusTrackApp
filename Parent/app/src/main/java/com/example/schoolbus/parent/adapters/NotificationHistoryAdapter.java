package com.example.schoolbus.parent.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.schoolbus.parent.R;
import com.example.schoolbus.parent.models.NotificationHistory;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationHistoryAdapter extends RecyclerView.Adapter<NotificationHistoryAdapter.NotificationViewHolder> {

    private List<NotificationHistory> notificationsList;
    private OnNotificationClickListener clickListener;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

    public NotificationHistoryAdapter(List<NotificationHistory> notificationsList) {
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
        private TextView tvMessage, tvTime, tvBusInfo, tvType, tvAdditionalInfo;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvBusInfo = itemView.findViewById(R.id.tv_bus_info);
            tvType = itemView.findViewById(R.id.tv_type);
            tvAdditionalInfo = itemView.findViewById(R.id.tv_additional_info);
        }

        void bind(NotificationHistory notification) {
            // Set message
            tvMessage.setText(notification.getMessage());

            // Format time - using the fixed getTimestampAsDate method
            if (notification.getTimestamp() != null) {
                try {
                    java.util.Date date = notification.getTimestampAsDate();
                    String time = timeFormat.format(date);
                    String dateStr = dateFormat.format(date);
                    tvTime.setText(time + "\n" + dateStr);
                } catch (Exception e) {
                    tvTime.setText("--:--\n--- --");
                    android.util.Log.e("TimeFormat", "Error formatting time: " + e.getMessage());
                }
            } else {
                tvTime.setText("--:--\n--- --");
            }

            // Set bus info
            if (notification.getBusNumber() != null && !notification.getBusNumber().isEmpty()) {
                tvBusInfo.setText("🚌 Bus " + notification.getBusNumber());
            } else {
                tvBusInfo.setText("🚌 Bus Info");
                tvBusInfo.setVisibility(View.GONE); // Hide if no bus number
            }

            // Set type with appropriate styling
            String type = notification.getType();
            if (type != null) {
                switch (type) {
                    case "ARRIVAL_PREDICTION":
                        tvType.setText("🚌 Arrival");
                        tvType.setBackgroundResource(R.drawable.bg_notification_arrival);
                        break;
                    case "SAFETY_ALERT":
                        tvType.setText("🚨 Safety");
                        tvType.setBackgroundResource(R.drawable.bg_notification_general);
                        break;
                    case "ROUTE_UPDATE":
                        tvType.setText("📍 Route");
                        tvType.setBackgroundResource(R.drawable.bg_notification_general);
                        break;
                    default:
                        tvType.setText("📬 Info");
                        tvType.setBackgroundResource(R.drawable.bg_notification_alert);
                        break;
                }
            } else {
                tvType.setText("📬 Info");
                tvType.setBackgroundResource(R.drawable.bg_notification_alert);
            }

            // Set additional info - using only fields that exist in your model
            StringBuilder additionalInfo = new StringBuilder();

            // Show next stop if available
            if (notification.getNextStop() != null && !notification.getNextStop().isEmpty()) {
                additionalInfo.append("Next: ").append(notification.getNextStop());
            }

            // Show ETA if available
            if (notification.getEtaMinutes() != null && notification.getEtaMinutes() > 0) {
                if (additionalInfo.length() > 0) additionalInfo.append(" • ");
                additionalInfo.append("ETA: ").append(notification.getEtaMinutes()).append("min");
            }

            // Show factors if available
            if (notification.getFactors() != null && !notification.getFactors().isEmpty()) {
                if (additionalInfo.length() > 0) additionalInfo.append(" • ");
                additionalInfo.append("Factors: ").append(notification.getFactors().size());
            }

            if (additionalInfo.length() > 0) {
                tvAdditionalInfo.setText(additionalInfo.toString());
                tvAdditionalInfo.setVisibility(View.VISIBLE);
            } else {
                tvAdditionalInfo.setVisibility(View.GONE);
            }

            // Visual indicator for unread notifications
            if (notification.getRead() != null && !notification.getRead()) {
                itemView.setAlpha(1.0f);
                // Show unread indicator
                itemView.setBackgroundResource(R.drawable.bg_notification_unread);
            } else {
                itemView.setAlpha(0.8f);
                itemView.setBackgroundResource(R.drawable.bg_notification_read);
            }
        }
    }
}