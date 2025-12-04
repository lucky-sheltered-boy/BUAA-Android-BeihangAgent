package com.example.beihangagent.view.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.databinding.FragmentClassSelectionBinding;
import com.example.beihangagent.view.adapter.ClassSelectionAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.viewmodel.ForumViewModel;

public class ClassSelectionFragment extends BaseFragment<FragmentClassSelectionBinding> {

    private ForumViewModel viewModel;
    private ClassSelectionAdapter adapter;
    private int userId;
    private int role;

    @Override
    protected FragmentClassSelectionBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentClassSelectionBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(requireActivity()).get(ForumViewModel.class);
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        userId = prefs.getInt("uid", -1);
        role = prefs.getInt("role", 0);

        adapter = new ClassSelectionAdapter(clazz -> {
            viewModel.selectClass(clazz);
            // Navigate to ForumFragment
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(com.example.beihangagent.R.id.nav_host_fragment, new ForumFragment())
                .addToBackStack(null)
                .commit();
        });

        binding.btnPublicForum.setOnClickListener(v -> {
            viewModel.enterPublicClass();
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(com.example.beihangagent.R.id.nav_host_fragment, new ForumFragment())
                .addToBackStack(null)
                .commit();
        });

        binding.btnMailbox.setOnClickListener(v -> {
            NotificationFragment fragment = new NotificationFragment();
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(com.example.beihangagent.R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
        });

        binding.rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvClasses.setAdapter(adapter);
    }

    @Override
    protected void initObservers() {
        if (role == 1) { // Teacher
            viewModel.getTeacherClasses(userId).observe(getViewLifecycleOwner(), classes -> {
                adapter.setClasses(classes);
            });
        } else { // Student
            viewModel.getStudentClasses(userId).observe(getViewLifecycleOwner(), classes -> {
                adapter.setClasses(classes);
            });
        }

        viewModel.getUnreadNotificationCount(userId).observe(getViewLifecycleOwner(), count -> {
            if (count > 0) {
                binding.tvUnreadCount.setVisibility(android.view.View.VISIBLE);
                binding.tvUnreadCount.setText(String.valueOf(count));
            } else {
                binding.tvUnreadCount.setVisibility(android.view.View.GONE);
            }
        });
    }
}
