package com.example.beihangagent.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;
import com.example.beihangagent.MainActivity;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.ActivityLoginBinding;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.User;
import com.example.beihangagent.view.base.BaseActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends BaseActivity<ActivityLoginBinding> {

    private ExecutorService executorService;
    private SharedPreferences prefs;

    @Override
    protected ActivityLoginBinding getViewBinding() {
        return ActivityLoginBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initViews() {
        executorService = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences("user_session", MODE_PRIVATE);

        binding.btnBack.setOnClickListener(v -> finishAffinity());

        boolean rememberDefault = prefs.getBoolean("remember_me", true);
        binding.switchRemember.setChecked(rememberDefault);

        if (shouldAutoLogin()) {
            navigateToMain();
            return;
        }

        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString();
            String password = binding.etPassword.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            login(username, password);
        });

        binding.tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private boolean shouldAutoLogin() {
        return prefs.getBoolean("logged_in", false)
                && prefs.getBoolean("remember_me", true)
                && prefs.getInt("uid", -1) != -1;
    }

    private void login(String username, String password) {
        binding.btnLogin.setEnabled(false);
        binding.btnLogin.setText("登录中...");

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            // 查询用户
            User user = db.userDao().login(username, password);

            runOnUiThread(() -> {
                binding.btnLogin.setEnabled(true);
                binding.btnLogin.setText(R.string.login_action);

                if (user != null) {
                    // 登录成功
                    String displayName = !TextUtils.isEmpty(user.name) ? user.name : user.username;
                    Toast.makeText(this, "欢迎回来，" + displayName, Toast.LENGTH_SHORT).show();
                    saveUserSession(user, binding.switchRemember.isChecked());
                    navigateToMain();
                } else {
                    // 登录失败：账号不存在 或 密码错误
                    Toast.makeText(this, "账号或密码错误，请检查", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveUserSession(User user, boolean rememberMe) {
        prefs.edit()
                .putInt("uid", user.uid)
                .putInt("role", user.role)
                .putString("username", user.username)
                .putString("name", user.name == null ? "" : user.name)
                .putString("user_preference", user.preference == null ? "" : user.preference)
                .putBoolean("logged_in", true)
                .putBoolean("remember_me", rememberMe)
                .apply();
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
