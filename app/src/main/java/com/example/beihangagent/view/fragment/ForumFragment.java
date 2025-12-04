package com.example.beihangagent.view.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentForumBinding;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.view.adapter.PostAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.viewmodel.ForumViewModel;

public class ForumFragment extends BaseFragment<FragmentForumBinding> {

    private ForumViewModel viewModel;
    private PostAdapter adapter;
    private Class currentClass;
    private int userId;

    @Override
    protected FragmentForumBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentForumBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(requireActivity()).get(ForumViewModel.class);
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        userId = prefs.getInt("uid", -1);

        adapter = new PostAdapter(postWithUser -> {
            // Navigate to PostDetailFragment
            PostDetailFragment fragment = new PostDetailFragment();
            android.os.Bundle args = new android.os.Bundle();
            args.putInt("post_id", postWithUser.post.id);
            fragment.setArguments(args);
            
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
        });

        binding.rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPosts.setAdapter(adapter);

        binding.fabAddPost.setOnClickListener(v -> showCreatePostDialog());
        
        binding.btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
    }

    @Override
    protected void initObservers() {
        viewModel.getSelectedClass().observe(getViewLifecycleOwner(), clazz -> {
            if (clazz != null) {
                currentClass = clazz;
                if ("PUBLIC".equals(clazz.classCode)) {
                    binding.tvClassName.setText("公共讨论区");
                } else {
                    binding.tvClassName.setText(clazz.className + " 讨论区");
                }
                viewModel.getPosts(clazz.classId).observe(getViewLifecycleOwner(), posts -> {
                    adapter.setPosts(posts);
                    binding.tvEmptyState.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void showCreatePostDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_post, null);
        EditText etTitle = view.findViewById(R.id.etPostTitle);
        EditText etContent = view.findViewById(R.id.etPostContent);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.forum_post_create)
            .setView(view)
            .setPositiveButton(R.string.forum_post_publish, (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String content = etContent.getText().toString().trim();
                if (!title.isEmpty() && !content.isEmpty()) {
                    viewModel.createPost(currentClass.classId, userId, title, content);
                    Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
