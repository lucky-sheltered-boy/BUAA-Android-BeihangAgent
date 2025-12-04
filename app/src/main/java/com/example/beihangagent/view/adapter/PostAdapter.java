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
import com.example.beihangagent.model.PostWithUser;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private List<PostWithUser> posts = new ArrayList<>();
    private final OnPostClickListener listener;
    
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

    public interface OnPostClickListener {
        void onPostClick(PostWithUser post);
    }

    public PostAdapter(OnPostClickListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<PostWithUser> posts) {
        this.posts = posts != null ? posts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvContent, tvTime, tvPinned, tvAuthor;
        ImageView ivAvatar;

        PostViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvPinned = itemView.findViewById(R.id.tvPinned);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            ivAvatar = itemView.findViewById(R.id.ivPostAvatar);
            
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onPostClick(posts.get(pos));
                }
            });
        }

        void bind(PostWithUser item) {
            tvTitle.setText(item.post.title);
            tvContent.setText(item.post.content);
            tvTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(item.post.timestamp)));
            tvPinned.setVisibility(item.post.isPinned ? View.VISIBLE : View.GONE);
            
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
                    // Fallback头像 - 从文本文件恢复 (兼容旧数据，但优先使用默认头像)
                    // 如果用户觉得旧的丑，这里也可以直接用默认头像
                    // 为了保持一致性，我们这里也使用默认头像，除非是用户特意设置的图片
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
            imageView.setColorFilter(null); // ic_default_avatar is already white
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
                Log.w("PostAdapter", "Failed to read avatar data", e);
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
                Log.w("PostAdapter", "Failed to load bitmap", e);
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
