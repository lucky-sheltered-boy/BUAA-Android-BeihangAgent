package com.example.beihangagent.view.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment<VB extends ViewBinding> extends Fragment {

    protected VB binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = getViewBinding(inflater, container);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initObservers();
    }

    protected abstract VB getViewBinding(LayoutInflater inflater, ViewGroup container);

    protected abstract void initViews();

    protected void initObservers() {
        // Optional override
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
