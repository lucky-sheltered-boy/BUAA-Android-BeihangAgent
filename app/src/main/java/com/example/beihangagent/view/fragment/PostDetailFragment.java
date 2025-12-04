package com.example.beihangagent.view.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentPostDetailBinding;
import com.example.beihangagent.model.PostWithUser;
import com.example.beihangagent.view.adapter.CommentAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.viewmodel.ForumViewModel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostDetailFragment extends BaseFragment<FragmentPostDetailBinding> {

    private ForumViewModel viewModel;
    private CommentAdapter adapter;
    private int postId;
    private int userId;
    private int role;
    private PostWithUser currentPost;
    private Integer replyToUserId = null;
    private String replyToUserName = null;

    @Override
    protected FragmentPostDetailBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentPostDetailBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(requireActivity()).get(ForumViewModel.class);
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        userId = prefs.getInt("uid", -1);
        role = prefs.getInt("role", 0);

        if (getArguments() != null) {
            postId = getArguments().getInt("post_id");
        }

        adapter = new CommentAdapter();
        adapter.setOnCommentClickListener(comment -> {
            replyToUserId = comment.user.uid;
            replyToUserName = comment.user.name != null && !comment.user.name.isEmpty() ? comment.user.name : comment.user.username;
            binding.etComment.setHint("回复 @" + replyToUserName + ":");
            binding.etComment.requestFocus();
            // Show keyboard logic could be added here
        });
        binding.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvComments.setAdapter(adapter);

        binding.btnSendComment.setOnClickListener(v -> {
            String content = binding.etComment.getText().toString().trim();
            if (!content.isEmpty()) {
                if (replyToUserId != null && replyToUserName != null) {
                    content = "回复 @" + replyToUserName + ": " + content;
                }
                viewModel.createComment(postId, userId, content, replyToUserId);
                binding.etComment.setText("");
                binding.etComment.setHint("写下你的评论...");
                replyToUserId = null;
                replyToUserName = null;
                Toast.makeText(requireContext(), "评论成功", Toast.LENGTH_SHORT).show();
            }
        });
        
        binding.toolbar.setNavigationIcon(R.drawable.ic_back_white);
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        binding.btnLike.setOnClickListener(v -> {
            // The listener is updated in observer
        });
        
        if (role == 1) { // Teacher
            binding.btnDelete.setVisibility(View.VISIBLE);
            binding.btnPin.setVisibility(View.VISIBLE);
            
            binding.btnDelete.setOnClickListener(v -> {
                if (currentPost != null) {
                    new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("确认删除")
                        .setMessage("确定要删除这条帖子吗？此操作不可恢复。")
                        .setPositiveButton("删除", (dialog, which) -> {
                            viewModel.deletePost(currentPost.post);
                            requireActivity().getSupportFragmentManager().popBackStack();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                }
            });
            
            binding.btnPin.setOnClickListener(v -> {
                if (currentPost != null) {
                    viewModel.togglePin(currentPost.post);
                }
            });
        }
    }
    
    private boolean isLiked = false;

    @Override
    protected void initObservers() {
        viewModel.getPost(postId).observe(getViewLifecycleOwner(), postWithUser -> {
            if (postWithUser != null) {
                currentPost = postWithUser;
                binding.tvDetailTitle.setText(postWithUser.post.title);
                binding.tvDetailContent.setText(postWithUser.post.content);
                
                String name = postWithUser.user != null ? (postWithUser.user.name != null && !postWithUser.user.name.isEmpty() ? postWithUser.user.name : postWithUser.user.username) : "Unknown";
                binding.tvDetailAuthor.setText(name + " | " + 
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(postWithUser.post.timestamp)));
                
                binding.btnPin.setText(postWithUser.post.isPinned ? R.string.forum_unpin : R.string.forum_pin);
            }
        });

        viewModel.getComments(postId).observe(getViewLifecycleOwner(), comments -> {
            adapter.setComments(comments);
        });

        viewModel.getLikeCount(postId).observe(getViewLifecycleOwner(), count -> {
            binding.tvLikeCount.setText(String.valueOf(count));
        });

        viewModel.isLiked(postId, userId).observe(getViewLifecycleOwner(), liked -> {
            isLiked = (liked != null && liked);
            binding.btnLike.setText(isLiked ? "👍 已赞" : "👍 点赞");
            binding.btnLike.setOnClickListener(v -> viewModel.toggleLike(postId, userId, isLiked));
        });
    }
}
