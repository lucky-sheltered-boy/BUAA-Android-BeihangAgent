package com.example.beihangagent.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.example.beihangagent.MainActivity;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.ActivityRegisterBinding;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.User;
import com.example.beihangagent.view.base.BaseActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class RegisterActivity extends BaseActivity<ActivityRegisterBinding> {

    private ExecutorService executorService;
    private SharedPreferences prefs;

    // 颜色常量：默认灰，成功绿，失败红
    private final int COLOR_DEFAULT = Color.parseColor("#9E9E9E");
    private final int COLOR_SUCCESS = Color.parseColor("#4CAF50");
    private final int COLOR_ERROR = Color.parseColor("#F44336");

    @Override
    protected ActivityRegisterBinding getViewBinding() {
        return ActivityRegisterBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initViews() {
        executorService = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences("user_session", MODE_PRIVATE);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvLoginLink.setOnClickListener(v -> finish());

        // 初始化实时校验监听
        setupRealTimeValidation();

        // 注册按钮点击逻辑
        binding.btnRegister.setOnClickListener(v -> {
            String username = binding.etRegUsername.getText().toString().trim();
            String password = binding.etRegPassword.getText().toString().trim();
            String confirmPassword = binding.etRegConfirmPassword.getText().toString().trim();

            int checkedId = binding.toggleGroupRole.getCheckedButtonId();
            int role = (checkedId == R.id.btnTeacher) ? 1 : 0;

            // 1. 校验身份
            if (checkedId == -1) {
                Toast.makeText(this, "请选择您的身份", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 校验用户名（如果存在 Error 则阻止提交）
            if (binding.tilRegUsername.getError() != null || username.isEmpty()) {
                binding.tilRegUsername.setError("请填写有效的学号/邮箱");
                return;
            }

            // 3. 校验密码规则
            if (password.length() < 8 || isPureNumber(password)) {
                Toast.makeText(this, "密码不符合格式要求", Toast.LENGTH_SHORT).show();
                return;
            }

            // 4. 校验确认密码
            if (!password.equals(confirmPassword)) {
                binding.tilRegConfirmPassword.setError("两次密码输入不一致");
                return;
            }

            register(username, password, role);
        });
    }

    private void setupRealTimeValidation() {
        // --- 学号查重 (失去焦点时触发) ---
        binding.etRegUsername.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String inputUsername = binding.etRegUsername.getText().toString().trim();
                if (!inputUsername.isEmpty()) {
                    checkUsernameUnique(inputUsername);
                } else {
                    // 空内容清除状态
                    binding.tilRegUsername.setError(null);
                    binding.tilRegUsername.setHelperText(null);
                }
            }
        });

        // --- 密码规则校验 (输入内容变化时触发) ---
        binding.etRegPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString();
                updatePasswordRuleUI(input);
            }
        });

        // 初始状态更新一次（确保是灰色）
        updatePasswordRuleUI("");
    }

    // 后台查询用户名唯一性
    private void checkUsernameUnique(String username) {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            User existingUser = db.userDao().checkUser(username);

            runOnUiThread(() -> {
                if (existingUser != null) {
                    // 已注册：红色错误
                    binding.tilRegUsername.setError("该账号已被注册");
                    binding.tilRegUsername.setHelperText(null);
                } else {
                    // 可用：绿色提示
                    binding.tilRegUsername.setError(null);
                    binding.tilRegUsername.setHelperText("√ 该账号可用");
                    binding.tilRegUsername.setHelperTextColor(ColorStateList.valueOf(COLOR_SUCCESS));
                }
            });
        });
    }

    // 更新密码规则UI显示 (颜色 + 图标)
    private void updatePasswordRuleUI(String password) {
        boolean isLengthOk = password.length() >= 8;
        // 非纯数字校验：不为空 且 不全是数字
        boolean isComplexityOk = !password.isEmpty() && !isPureNumber(password);

        updateRuleView(binding.tvRuleLength, isLengthOk, password.isEmpty());
        updateRuleView(binding.tvRuleComplexity, isComplexityOk, password.isEmpty());
    }

    // 切换单个规则View的状态 (灰/绿/红)
    private void updateRuleView(TextView textView, boolean passed, boolean isEmpty) {
        int color;
        int iconRes;

        if (isEmpty) {
            // 空状态：灰色，默认图标
            color = COLOR_DEFAULT;
            iconRes = R.drawable.ic_rule_fail;
        } else if (passed) {
            // 通过：绿色，对勾图标
            color = COLOR_SUCCESS;
            iconRes = R.drawable.ic_rule_pass;
        } else {
            // 失败：红色，错误图标
            color = COLOR_ERROR;
            iconRes = R.drawable.ic_rule_fail;
        }

        textView.setTextColor(color);

        // 设置左侧图标并染色
        Drawable drawable = ContextCompat.getDrawable(this, iconRes);
        if (drawable != null) {
            drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
            drawable.setTint(color);
            textView.setCompoundDrawables(drawable, null, null, null);
        }
    }

    private boolean isPureNumber(String str) {
        return Pattern.matches("\\d+", str);
    }

    private void register(String username, String password, int role) {
        binding.btnRegister.setEnabled(false);
        binding.btnRegister.setText("注册中...");

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            // 提交前双重检查
            User existingUser = db.userDao().checkUser(username);

            if (existingUser != null) {
                runOnUiThread(() -> {
                    binding.tilRegUsername.setError("该账号已被注册");
                    resetButtonState();
                });
            } else {
                // 写入数据库
                User newUser = new User(username, password, role);
                db.userDao().insert(newUser);

                // 读取完整信息（含uid）以便自动登录
                User registeredUser = db.userDao().login(username, password);

                runOnUiThread(() -> {
                    Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show();
                    if (registeredUser != null) {
                        saveUserSession(registeredUser);
                        navigateToMain();
                    } else {
                        finish();
                    }
                });
            }
        });
    }

    private void resetButtonState() {
        binding.btnRegister.setEnabled(true);
        binding.btnRegister.setText(R.string.register_action);
    }

    private void saveUserSession(User user) {
        prefs.edit()
                .putInt("uid", user.uid)
                .putInt("role", user.role)
                .putString("username", user.username)
                .putString("name", user.name == null ? "" : user.name)
                .putString("user_preference", user.preference == null ? "" : user.preference)
                .putBoolean("logged_in", true)
                .putBoolean("remember_me", true)
                .apply();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}