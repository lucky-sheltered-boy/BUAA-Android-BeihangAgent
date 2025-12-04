package com.example.beihangagent.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.Class;
import java.util.ArrayList;
import java.util.List;

public class ClassSelectionAdapter extends RecyclerView.Adapter<ClassSelectionAdapter.ViewHolder> {

    private List<Class> classes = new ArrayList<>();
    private final OnClassSelectedListener listener;

    public interface OnClassSelectedListener {
        void onClassSelected(Class clazz);
    }

    public ClassSelectionAdapter(OnClassSelectedListener listener) {
        this.listener = listener;
    }

    public void setClasses(List<Class> classes) {
        this.classes = classes != null ? classes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class clazz = classes.get(position);
        holder.tvClassName.setText(clazz.className);
        holder.tvClassCode.setText("邀请码: " + clazz.classCode);
        holder.itemView.setOnClickListener(v -> listener.onClassSelected(clazz));
    }

    @Override
    public int getItemCount() {
        return classes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvClassName, tvClassCode;
        ViewHolder(View itemView) {
            super(itemView);
            tvClassName = itemView.findViewById(R.id.tvClassName);
            tvClassCode = itemView.findViewById(R.id.tvClassCode);
        }
    }
}
