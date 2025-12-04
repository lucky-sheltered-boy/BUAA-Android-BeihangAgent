package com.example.beihangagent.view.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.databinding.ItemQuickActionBinding;

import java.util.ArrayList;
import java.util.List;

public class QuickActionAdapter extends RecyclerView.Adapter<QuickActionAdapter.ViewHolder> {

    public static class QuickAction {
        public final int iconRes;
        public final String title;
        public final Runnable action;

        public QuickAction(int iconRes, String title, Runnable action) {
            this.iconRes = iconRes;
            this.title = title;
            this.action = action;
        }
    }

    private List<QuickAction> actions = new ArrayList<>();

    public void setActions(List<QuickAction> actions) {
        this.actions = actions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuickActionBinding binding = ItemQuickActionBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuickAction action = actions.get(position);
        holder.binding.ivIcon.setImageResource(action.iconRes);
        holder.binding.tvTitle.setText(action.title);
        holder.itemView.setOnClickListener(v -> {
            if (action.action != null) {
                action.action.run();
            }
        });
    }

    @Override
    public int getItemCount() {
        return actions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemQuickActionBinding binding;

        ViewHolder(ItemQuickActionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}