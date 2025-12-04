package com.example.beihangagent.view.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentNotificationBinding;
import com.example.beihangagent.view.adapter.NotificationAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.viewmodel.ForumViewModel;

public class NotificationFragment extends BaseFragment<FragmentNotificationBinding> {

    private ForumViewModel viewModel;
    private NotificationAdapter adapter;
    private int userId;

    @Override
    protected FragmentNotificationBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentNotificationBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(requireActivity()).get(ForumViewModel.class);
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        userId = prefs.getInt("uid", -1);

        adapter = new NotificationAdapter(notification -> {
            // Mark as read
            if (!notification.isRead) {
                viewModel.markNotificationAsRead(notification.id);
            }
            
            // Navigate to the post related to the notification
            PostDetailFragment fragment = new PostDetailFragment();
            android.os.Bundle args = new android.os.Bundle();
            args.putInt("post_id", notification.relatedPostId);
            fragment.setArguments(args);
            
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
        });

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotifications.setAdapter(adapter);
        
        binding.toolbar.setNavigationIcon(R.drawable.ic_back_white);
        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        
        binding.btnMarkAllRead.setOnClickListener(v -> {
            viewModel.markNotificationsAsRead(userId);
        });
    }

    @Override
    protected void initObservers() {
        viewModel.getNotifications(userId).observe(getViewLifecycleOwner(), notifications -> {
            adapter.setNotifications(notifications);
            binding.tvEmptyState.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
