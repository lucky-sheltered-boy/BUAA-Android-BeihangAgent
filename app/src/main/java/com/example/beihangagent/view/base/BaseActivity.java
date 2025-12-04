package com.example.beihangagent.view.base;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    protected VB binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = getViewBinding();
        setContentView(binding.getRoot());
        initViews();
        initObservers();
    }

    protected abstract VB getViewBinding();

    protected abstract void initViews();

    protected void initObservers() {
        // Optional override
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
