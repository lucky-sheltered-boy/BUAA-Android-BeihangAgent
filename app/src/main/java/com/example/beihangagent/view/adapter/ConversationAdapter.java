package com.example.beihangagent.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.Conversation;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private List<Conversation> conversations = new ArrayList<>();
    private final OnItemClickListener listener;
    private final OnDeleteClickListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(Conversation conversation);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Conversation conversation);
    }

    public ConversationAdapter(OnItemClickListener listener, OnDeleteClickListener deleteListener) {
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    public void setConversations(List<Conversation> conversations) {
        this.conversations = conversations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.bind(conversation, listener, deleteListener);
    }

    @Override
    public int getItemCount() {
        return conversations != null ? conversations.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        View btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvConversationTitle);
            tvDate = itemView.findViewById(R.id.tvConversationDate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(final Conversation conversation, final OnItemClickListener listener, final OnDeleteClickListener deleteListener) {
            tvTitle.setText(conversation.title);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            tvDate.setText(sdf.format(new Date(conversation.timestamp)));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(conversation);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onDeleteClick(conversation);
                }
            });
        }
    }
}
