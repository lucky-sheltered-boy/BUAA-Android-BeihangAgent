package com.example.beihangagent.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.model.ClassDao;
import java.util.ArrayList;
import java.util.List;

public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.ClassViewHolder> {

    private List<Class> classList = new ArrayList<>();
    private final OnClassActionListener listener;
    private final boolean isTeacher;
    private final ClassDao classDao;
    private final LifecycleOwner lifecycleOwner;

    public interface OnClassActionListener {
        void onViewMembers(Class classEntity);
        void onDeleteClass(Class classEntity);
        void onLeaveClass(Class classEntity);
        void onGoToForum(Class classEntity);
    }

    public ClassAdapter(OnClassActionListener listener, boolean isTeacher, ClassDao classDao, LifecycleOwner lifecycleOwner) {
        this.listener = listener;
        this.isTeacher = isTeacher;
        this.classDao = classDao;
        this.lifecycleOwner = lifecycleOwner;
    }

    public void setClasses(List<Class> classes) {
        this.classList = classes != null ? classes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class, parent, false);
        return new ClassViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassViewHolder holder, int position) {
        holder.bind(classList.get(position));
    }

    @Override
    public int getItemCount() {
        return classList.size();
    }

    class ClassViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvClassName;
        private final TextView tvClassCode;
        private final TextView tvMemberCount;
        private final ImageButton btnMore;

        ClassViewHolder(@NonNull View itemView) {
            super(itemView);
            tvClassName = itemView.findViewById(R.id.tvClassName);
            tvClassCode = itemView.findViewById(R.id.tvClassCode);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            btnMore = itemView.findViewById(R.id.btnMore);
        }

        void bind(Class classEntity) {
            tvClassName.setText(classEntity.className);
            tvClassCode.setText("邀请码: " + classEntity.classCode);

            // Load member count from database
            classDao.getClassMemberCount(classEntity.classId).observe(lifecycleOwner, count -> {
                if (count != null) {
                    tvMemberCount.setText("学生人数: " + count);
                } else {
                    tvMemberCount.setText("学生人数: 0");
                }
            });

            btnMore.setOnClickListener(v -> showPopupMenu(v, classEntity));
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onViewMembers(classEntity);
                }
            });
        }

        private void showPopupMenu(View view, Class classEntity) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.menu_class_item, popup.getMenu());
            
            if (isTeacher) {
                // Teacher can view members and delete class, but cannot leave or go to forum
                popup.getMenu().findItem(R.id.action_leave_class).setVisible(false);
                popup.getMenu().findItem(R.id.action_go_to_forum).setVisible(false);
            } else {
                // Student can go to forum and leave class, cannot view members or delete
                popup.getMenu().findItem(R.id.action_view_members).setVisible(false);
                popup.getMenu().findItem(R.id.action_delete_class).setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_view_members && isTeacher) {
                    if (listener != null) listener.onViewMembers(classEntity);
                    return true;
                } else if (itemId == R.id.action_delete_class && isTeacher) {
                    if (listener != null) listener.onDeleteClass(classEntity);
                    return true;
                } else if (itemId == R.id.action_go_to_forum && !isTeacher) {
                    if (listener != null) listener.onGoToForum(classEntity);
                    return true;
                } else if (itemId == R.id.action_leave_class && !isTeacher) {
                    if (listener != null) listener.onLeaveClass(classEntity);
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }
}
