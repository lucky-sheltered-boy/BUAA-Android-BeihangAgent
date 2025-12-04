package com.example.beihangagent.view.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.CommentWithUser;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<CommentWithUser> comments = new ArrayList<>();
    
    // 头像颜色和图标数组（与ProfileFragment保持一致）
    private final int[] avatarColors = {
        0xFF6B73FF, 0xFF9B59B6, 0xFF3498DB, 0xFF1ABC9C,
        0xFF2ECC71, 0xFFF39C12, 0xFFE67E22, 0xFFE74C3C
    };
    
    private final int[] avatarIcons = {
        R.drawable.ic_person_24,
        R.drawable.ic_school_logo,
        R.drawable.ic_person_24
    };

    public interface OnCommentClickListener {
        void onCommentClick(CommentWithUser comment);
    }

    private OnCommentClickListener listener;

    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.listener = listener;
    }

    public void setComments(List<CommentWithUser> comments) {
        this.comments = comments != null ? comments : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(comments.get(position));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime, tvAuthor;
        ImageView ivAvatar;

        CommentViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvCommentContent);
            tvTime = itemView.findViewById(R.id.tvCommentTime);
            tvAuthor = itemView.findViewById(R.id.tvCommentAuthor);
            ivAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onCommentClick(comments.get(pos));
                }
            });
        }

        void bind(CommentWithUser item) {
            String content = item.comment.content;
            if (content != null && content.startsWith("回复 @")) {
                // Highlight the "回复 @Username:" part
                int colonIndex = content.indexOf(":");
                if (colonIndex > 0) {
                    android.text.SpannableString spannable = new android.text.SpannableString(content);
                    spannable.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#005193")), 
                        0, colonIndex + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tvContent.setText(spannable);
                } else {
                    tvContent.setText(content);
                }
            } else {
                tvContent.setText(content);
            }
            
            tvTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(item.comment.timestamp)));
            
            String name = item.user != null ? (item.user.name != null && !item.user.name.isEmpty() ? item.user.name : item.user.username) : "Unknown";
            tvAuthor.setText(name);
            
            // 加载用户头像
            loadUserAvatar(item.user, ivAvatar);
        }

        private void loadUserAvatar(com.example.beihangagent.model.User user, ImageView imageView) {
            if (user == null) {
                // 设置默认头像
                setDefaultAvatar(imageView);
                return;
            }

            // 尝试加载保存的头像
            if (user.avatarPath != null && !user.avatarPath.isEmpty()) {
                if (user.avatarType != null && user.avatarType == 0) {
                    // Fallback头像 - 从文本文件恢复
                    setDefaultAvatar(imageView);
                    return;
                } else {
                    // Gravatar或其他bitmap头像
                    Bitmap savedAvatar = loadBitmapFromFile(user.avatarPath);
                    if (savedAvatar != null) {
                        imageView.setImageBitmap(savedAvatar);
                        imageView.setColorFilter(null);
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        if (user.avatarType != null && user.avatarType == 1) { // Gravatar
                            imageView.setBackgroundColor(0xFFFFFFFF); // 白色背景
                        }
                        return;
                    }
                }
            }

            // 如果没有保存的头像，使用默认头像
            setDefaultAvatar(imageView);
        }

        private void setDefaultAvatar(ImageView imageView) {
            imageView.setBackgroundResource(R.drawable.bg_avatar_circle);
            imageView.setImageResource(R.drawable.ic_default_avatar);
            imageView.setColorFilter(null);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setPadding(4, 4, 4, 4);
        }

        private String readAvatarData(String filePath) {
            try {
                File file = new File(filePath);
                if (file.exists() && filePath.endsWith(".txt")) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();
                    return new String(data);
                }
            } catch (Exception e) {
                Log.w("CommentAdapter", "Failed to read avatar data", e);
            }
            return null;
        }

        private Bitmap loadBitmapFromFile(String filePath) {
            try {
                if (filePath != null && !filePath.isEmpty()) {
                    File file = new File(filePath);
                    if (file.exists() && filePath.endsWith(".png")) {
                        return BitmapFactory.decodeFile(filePath);
                    }
                }
            } catch (Exception e) {
                Log.w("CommentAdapter", "Failed to load bitmap", e);
            }
            return null;
        }

        private String generateSimpleHash(String input) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] messageDigest = md.digest(input.toLowerCase().getBytes());
                StringBuilder sb = new StringBuilder();
                for (byte b : messageDigest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                return String.valueOf(Math.abs(input.hashCode()));
            }
        }
    }
}
