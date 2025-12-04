package com.example.beihangagent.view.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.AnalysisHotspot;
import java.util.ArrayList;
import java.util.List;

public class HotspotAdapter extends RecyclerView.Adapter<HotspotAdapter.HotspotHolder> {

    private final List<AnalysisHotspot> items = new ArrayList<>();
    private final LayoutInflater inflater;
    private final Context context;

    public HotspotAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void submitList(List<AnalysisHotspot> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HotspotHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_hotspot, parent, false);
        return new HotspotHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HotspotHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class HotspotHolder extends RecyclerView.ViewHolder {
        private final TextView tvLabel;
        private final TextView tvBadge;
        private final TextView tvSample;
        private final TextView tvStats;

        HotspotHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvHotspotLabel);
            tvBadge = itemView.findViewById(R.id.tvHotspotBadge);
            tvSample = itemView.findViewById(R.id.tvHotspotSample);
            tvStats = itemView.findViewById(R.id.tvHotspotStats);
        }

        void bind(AnalysisHotspot hotspot) {
            // 添加调试信息
            android.util.Log.d("HotspotAdapter", "Binding hotspot: " +
                "label=" + hotspot.label + 
                ", sample=" + hotspot.sample + 
                ", count=" + hotspot.count + 
                ", type=" + hotspot.type);
            
            if (hotspot.label != null && !hotspot.label.isEmpty()) {
                tvLabel.setText(hotspot.label);
                tvLabel.setVisibility(View.VISIBLE);
            } else {
                tvLabel.setText("未知分类");
                tvLabel.setVisibility(View.VISIBLE);
            }
            
            if (hotspot.sample != null && !hotspot.sample.isEmpty()) {
                tvSample.setText(hotspot.sample);
                tvSample.setVisibility(View.VISIBLE);
            } else {
                tvSample.setText("暂无示例");
                tvSample.setVisibility(View.VISIBLE);
            }
            
            if (hotspot.type == AnalysisHotspot.Type.ERROR) {
                tvBadge.setText(R.string.analysis_hot_badge_error);
            } else {
                tvBadge.setText(R.string.analysis_hot_badge_topic);
            }
            String frequency = context.getString(R.string.analysis_hot_frequency, hotspot.count);
            String recentText = formatRelativeTime(hotspot.lastSeen);
            tvStats.setText(frequency + " · " + recentText);
        }
        
        private String formatRelativeTime(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            
            if (diff < 60 * 1000) { // Less than 1 minute
                return "刚刚";
            } else if (diff < 60 * 60 * 1000) { // Less than 1 hour
                long minutes = diff / (60 * 1000);
                return "最近：" + minutes + "分钟前";
            } else if (diff < 24 * 60 * 60 * 1000) { // Less than 1 day
                long hours = diff / (60 * 60 * 1000);
                return "最近：" + hours + "小时前";
            } else { // More than 1 day
                long days = diff / (24 * 60 * 60 * 1000);
                return "最近：" + days + "天前";
            }
        }
    }
}
