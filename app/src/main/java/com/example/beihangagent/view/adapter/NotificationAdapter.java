package com.example.beihangagent.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.Notification;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<Notification> notifications = new ArrayList<>();
    private final OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications != null ? notifications : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(notifications.get(position));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;

        NotificationViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvNotificationContent);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onNotificationClick(notifications.get(pos));
                }
            });
        }

        void bind(Notification notification) {
            tvContent.setText(notification.message);
            tvTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(notification.timestamp)));
            
            if (!notification.isRead) {
                itemView.setBackgroundColor(0xFFE3F2FD); // Light blue for unread
            } else {
                itemView.setBackgroundColor(0xFFFFFFFF); // White for read
            }
        }
    }
}
