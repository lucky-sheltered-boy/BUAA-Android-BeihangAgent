package com.example.beihangagent.view.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.MainActivity;
import com.example.beihangagent.databinding.FragmentProfileBinding;
import com.example.beihangagent.databinding.BottomSheetProfileSettingsBinding;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.User;
import com.example.beihangagent.model.UserDao;
import com.example.beihangagent.model.ChatMessageDao;
import com.example.beihangagent.model.ClassDao;
import com.example.beihangagent.view.LoginActivity;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.view.adapter.QuickActionAdapter;
import com.example.beihangagent.view.fragment.ClassManageFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ProfileFragment extends BaseFragment<FragmentProfileBinding> {

    private SharedPreferences prefs;
    private UserDao userDao;
    private ChatMessageDao chatMessageDao;
    private ClassDao classDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int currentUserId;
    private User currentUser;
    private QuickActionAdapter quickActionAdapter;
    private BottomSheetDialog settingsDialog;
    
    // 随机头像颜色数组
    private final int[] avatarColors = {
        0xFF6B73FF, 0xFF9B59B6, 0xFF3498DB, 0xFF1ABC9C,
        0xFF2ECC71, 0xFFF39C12, 0xFFE67E22, 0xFFE74C3C
    };
    
    // 随机头像图标数组
    private final int[] avatarIcons = {
        R.drawable.ic_person_24,
        R.drawable.ic_school_logo,
        R.drawable.ic_person_24 // 可以添加更多图标
    };

    @Override
    protected FragmentProfileBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentProfileBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        prefs = requireContext().getSharedPreferences("user_session", requireContext().MODE_PRIVATE);
        AppDatabase db = AppDatabase.getDatabase(requireContext());
        userDao = db.userDao();
        chatMessageDao = db.chatMessageDao();
        classDao = db.classDao();
        currentUserId = prefs.getInt("uid", -1);

        setupQuickActions();
        loadUserInfo();
        loadStatistics();
        setupListeners();
    }

    private void setupQuickActions() {
        // 初始化适配器
        quickActionAdapter = new QuickActionAdapter();
        
        // 设置网格布局，4列
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 4);
        binding.rvQuickActions.setLayoutManager(layoutManager);
        binding.rvQuickActions.setAdapter(quickActionAdapter);

        // 创建快捷操作数据
        List<QuickActionAdapter.QuickAction> actions = Arrays.asList(
            new QuickActionAdapter.QuickAction(
                R.drawable.ic_school_24, 
                "班级管理", 
                this::openClassManagement
            ),
            new QuickActionAdapter.QuickAction(
                R.drawable.ic_export_24, 
                "数据导出", 
                this::exportData
            ),
            new QuickActionAdapter.QuickAction(
                R.drawable.ic_analytics_24, 
                "学习报告", 
                this::showLearningReport
            ),
            new QuickActionAdapter.QuickAction(
                android.R.drawable.ic_menu_help, 
                "帮助中心", 
                this::showHelp
            )
        );
        
        // 设置数据并刷新
        quickActionAdapter.setActions(actions);
        Log.d("ProfileFragment", "Quick actions set, count: " + actions.size());
    }

    private void loadUserInfo() {
        executor.execute(() -> {
            currentUser = userDao.getUserById(currentUserId);
            requireActivity().runOnUiThread(() -> {
                if (currentUser != null) {
                    binding.tvUsername.setText(currentUser.username);
                    binding.tvRole.setText(currentUser.role == 1 ? "教师" : "学生");
                    
                    // Display name
                    String displayName = !TextUtils.isEmpty(currentUser.name) ? 
                                       currentUser.name : getString(R.string.profile_name_not_set);
                    binding.tvDisplayName.setText(displayName);
                    
                    // Display college - updated from major
                    String college = !TextUtils.isEmpty(currentUser.preference) ? 
                                 currentUser.preference : getString(R.string.profile_major_not_set);
                    binding.tvMajor.setText(college);
                    
                    // 同步学院信息到SharedPreferences供ChatFragment使用
                    prefs.edit().putString("preference", 
                        !TextUtils.isEmpty(currentUser.preference) ? currentUser.preference : "").apply();
                    
                    // 记录首次使用时间（用于计算学习天数）
                    SharedPreferences personalPrefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
                    if (!personalPrefs.contains("first_use_time")) {
                        personalPrefs.edit().putLong("first_use_time", System.currentTimeMillis()).apply();
                    }
                    
                    // 首先尝试加载已保存的头像
                    loadSavedAvatarOrGenerate();
                    
                    // Setup class observers after user is loaded
                    setupClassObservers();
                }
            });
        });
    }

    private void loadStatistics() {
        executor.execute(() -> {
            final int questionCount;
            final int activeDays;
            
            if (currentUser != null && currentUser.role == 1) {
                // 教师：统计所有班级学生的提问总数
                questionCount = chatMessageDao.countStudentMessagesByTeacher(currentUserId);
                // 教师活跃天数：基于所有学生消息的日期
                activeDays = calculateActiveDaysForTeacher(currentUserId);
            } else {
                // 学生：统计自己的提问数，直接从ChatMessage表统计用户消息
                List<com.example.beihangagent.model.ChatMessage> userMessages = chatMessageDao.getByUser(currentUserId);
                int userQuestionCount = 0;
                for (com.example.beihangagent.model.ChatMessage message : userMessages) {
                    if ("user".equals(message.role)) {
                        userQuestionCount++;
                    }
                }
                questionCount = userQuestionCount;
                
                // 同步更新个性化计数到SharedPreferences
                SharedPreferences personalPrefs = requireContext().getSharedPreferences("personalization", requireContext().MODE_PRIVATE);
                personalPrefs.edit().putInt("total_conversations", userQuestionCount).apply();
                
                // 学生活跃天数：基于自己的消息日期
                activeDays = calculateActiveDaysForStudent(currentUserId);
            }
            
            // Class count - use placeholder for now since LiveData needs observation
            final int classCount = 0; // Will be updated via observers
            
            requireActivity().runOnUiThread(() -> {
                binding.tvQuestionCount.setText(String.valueOf(questionCount));
                binding.tvClassCount.setText(String.valueOf(classCount));
                binding.tvDaysActive.setText(String.valueOf(activeDays));
                
                // 根据用户角色设置不同的标签
                if (currentUser != null && currentUser.role == 1) {
                    // 教师端显示
                    binding.tvQuestionLabel.setText("学生提问");
                    binding.tvClassLabel.setText("我的班级");
                } else {
                    // 学生端显示
                    binding.tvQuestionLabel.setText("我的提问");
                    binding.tvClassLabel.setText("加入班级");
                }
            });
        });
        
        // Setup observers for class count - always setup after user is loaded
        // This will be called again in loadUserInfo after currentUser is set
    }
    
    /**
     * 公共方法：刷新用户统计数据（供其他Fragment调用）
     */
    public void refreshUserStats() {
        if (isAdded() && getContext() != null) {
            loadStatistics();
        }
    }

    private void setupClassObservers() {
        if (currentUser != null && currentUser.role == 1) {
            // Teacher: observe created classes
            classDao.getClassesByTeacher(currentUserId).observe(this, classes -> {
                if (classes != null) {
                    binding.tvClassCount.setText(String.valueOf(classes.size()));
                }
            });
        } else {
            // Student: observe joined classes
            classDao.getClassesByStudent(currentUserId).observe(this, classes -> {
                if (classes != null) {
                    binding.tvClassCount.setText(String.valueOf(classes.size()));
                }
            });
        }
    }

    private void setupListeners() {
        binding.fabSettings.setOnClickListener(v -> showSettingsDialog());
        binding.btnLogout.setOnClickListener(v -> logout());
        // 头像不再可编辑，移除点击监听器
    }

    /**
     * 加载已保存的头像，如果没有则生成新头像
     */
    private void loadSavedAvatarOrGenerate() {
        if (currentUser == null) return;
        
        Log.d("ProfileFragment", "Loading avatar for user: " + currentUser.username + 
              ", avatarPath: " + currentUser.avatarPath + ", avatarType: " + currentUser.avatarType);
        
        // 如果有保存的头像，先加载
        if (!TextUtils.isEmpty(currentUser.avatarPath)) {
            Log.d("ProfileFragment", "Found saved avatar path, avatarType: " + currentUser.avatarType);
            if (currentUser.avatarType != null && currentUser.avatarType == 0) {
                // Fallback头像 - 从文本文件恢复
                Log.d("ProfileFragment", "Loading fallback avatar from: " + currentUser.avatarPath);
                loadAvatarFromFile(currentUser.avatarPath);
                return; // fallback头像通过restoreFallbackAvatar直接设置UI
            } else {
                // Gravatar或其他bitmap头像
                Log.d("ProfileFragment", "Loading bitmap avatar from: " + currentUser.avatarPath);
                Bitmap savedAvatar = loadAvatarFromFile(currentUser.avatarPath);
                if (savedAvatar != null) {
                    Log.d("ProfileFragment", "Loaded saved avatar successfully, applying to UI");
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (binding != null && binding.ivUserAvatar != null) {
                                Log.d("ProfileFragment", "Setting bitmap to ImageView on main thread");
                                binding.ivUserAvatar.setImageBitmap(savedAvatar);
                                binding.ivUserAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                binding.ivUserAvatar.setColorFilter(null);
                                
                                // 根据类型设置背景
                                if (currentUser.avatarType != null && currentUser.avatarType == 1) { // Gravatar
                                    binding.ivUserAvatar.setBackgroundColor(getResources().getColor(android.R.color.white));
                                    Log.d("ProfileFragment", "Set white background for Gravatar");
                                }
                                Log.d("ProfileFragment", "Avatar UI updated successfully");
                            } else {
                                Log.e("ProfileFragment", "binding or ivUserAvatar is null!");
                            }
                        });
                    }
                    return;
                } else {
                    Log.w("ProfileFragment", "Failed to load saved avatar, will regenerate");
                }
            }
        } else {
            Log.d("ProfileFragment", "No saved avatar path found, will generate new avatar");
        }
        
        // 如果没有保存的头像，生成新的
        generateRandomAvatar();
    }

    private void generateRandomAvatar() {
        if (currentUser != null) {
            Log.d("ProfileFragment", "Generating random color avatar for user: " + currentUser.username);
            
            // 直接生成随机颜色头像，不调用网络API
            String hash = generateMD5Hash(currentUser.username.toLowerCase());
            generateFallbackAvatar(hash);
        } else {
            Log.w("ProfileFragment", "Current user is null, cannot generate avatar");
        }
    }
    

    
    private void generateFallbackAvatar(String hash) {
        try {
            Log.d("ProfileFragment", "Generating fallback avatar with hash: " + hash.substring(0, 8));
            
            // 使用哈希值的前8位生成颜色
            int colorIndex = Math.abs(hash.substring(0, 8).hashCode()) % avatarColors.length;
            int backgroundColor = avatarColors[colorIndex];
            Log.d("ProfileFragment", "Selected color index: " + colorIndex + ", color: " + Integer.toHexString(backgroundColor));
            
            // 使用哈希值的后8位选择图标
            int iconIndex = Math.abs(hash.substring(24, 32).hashCode()) % avatarIcons.length;
            int iconRes = avatarIcons[iconIndex];
            Log.d("ProfileFragment", "Selected icon index: " + iconIndex + ", resource: " + iconRes);
            
            // 确保在主线程上设置UI
            if (binding != null && binding.ivUserAvatar != null) {
                // 创建圆形渐变背景
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(backgroundColor);
                
                binding.ivUserAvatar.setBackground(drawable);
                binding.ivUserAvatar.setImageResource(iconRes);
                binding.ivUserAvatar.setColorFilter(Color.WHITE);
                binding.ivUserAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                Log.d("ProfileFragment", "Fallback avatar set successfully");
                
                // 保存fallback头像 - 创建bitmap并保存
                try {
                    // 创建bitmap来保存fallback头像的样式信息
                    String fallbackData = colorIndex + "," + iconIndex; // 保存颜色和图标索引
                    String filename = "avatar_fallback_" + currentUser.uid + ".txt";
                    File avatarDir = new File(requireContext().getFilesDir(), "avatars");
                    if (!avatarDir.exists()) {
                        boolean created = avatarDir.mkdirs();
                        Log.d("ProfileFragment", "Fallback avatar directory created: " + created);
                    }
                    File dataFile = new File(avatarDir, filename);
                    
                    FileOutputStream fos = new FileOutputStream(dataFile);
                    fos.write(fallbackData.getBytes());
                    fos.close();
                    
                    Log.d("ProfileFragment", "Fallback avatar data saved to: " + dataFile.getAbsolutePath() + 
                          ", data: " + fallbackData);
                    
                    // 验证文件保存
                    if (dataFile.exists() && dataFile.length() > 0) {
                        // 更新用户头像信息
                        updateUserAvatar(dataFile.getAbsolutePath(), 0); // 0 = fallback
                        Log.d("ProfileFragment", "Fallback avatar info updated in database");
                    } else {
                        Log.e("ProfileFragment", "Fallback avatar file was not saved properly");
                    }
                } catch (Exception e) {
                    Log.e("ProfileFragment", "Failed to save fallback avatar data", e);
                }
            } else {
                Log.e("ProfileFragment", "Cannot set fallback avatar - binding or imageview is null");
            }
            
            Log.d("ProfileFragment", "Generated fallback avatar for user: " + 
                (currentUser != null ? currentUser.username : "unknown") + ", hash: " + hash.substring(0, 8));
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error generating fallback avatar", e);
            // 如果出错，至少设置一个简单的默认图标
            if (binding != null && binding.ivUserAvatar != null) {
                binding.ivUserAvatar.setImageResource(R.drawable.ic_person_24);
                binding.ivUserAvatar.setBackgroundColor(0xFF6B73FF); // 默认蓝色
                binding.ivUserAvatar.setColorFilter(Color.WHITE);
            }
        }
    }
    
    private String generateMD5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // 如果MD5不可用，使用简单的hashCode
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }

    private void showSettingsDialog() {
        settingsDialog = new BottomSheetDialog(requireContext());
        BottomSheetProfileSettingsBinding settingsBinding = BottomSheetProfileSettingsBinding
            .inflate(getLayoutInflater(), null, false);
        settingsDialog.setContentView(settingsBinding.getRoot());

        // Setup college dropdown
        String[] colleges = getResources().getStringArray(R.array.college_list);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, colleges);
        settingsBinding.spinnerMajor.setAdapter(adapter);

        // Load current user data into dialog
        if (currentUser != null) {
            if (!TextUtils.isEmpty(currentUser.name)) {
                settingsBinding.etName.setText(currentUser.name);
            }
            if (!TextUtils.isEmpty(currentUser.preference)) {
                settingsBinding.spinnerMajor.setText(currentUser.preference, false);
            }
        }

        // Setup listeners for settings dialog
        settingsBinding.btnCloseSettings.setOnClickListener(v -> settingsDialog.dismiss());
        settingsBinding.btnSaveInfo.setOnClickListener(v -> savePersonalInfo(settingsBinding));
        settingsBinding.btnChangePassword.setOnClickListener(v -> changePassword(settingsBinding));
        settingsBinding.btnDataExport.setOnClickListener(v -> {
            settingsDialog.dismiss();
            exportData();
        });
        settingsBinding.btnAbout.setOnClickListener(v -> {
            settingsDialog.dismiss();
            showAbout();
        });

        settingsDialog.show();
    }

    private void savePersonalInfo(BottomSheetProfileSettingsBinding settingsBinding) {
        String name = settingsBinding.etName.getText() == null ? "" : 
                     settingsBinding.etName.getText().toString().trim();
        String major = settingsBinding.spinnerMajor.getText().toString().trim();
        
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "请输入姓名", Toast.LENGTH_SHORT).show();
            return;
        }

        settingsBinding.btnSaveInfo.setEnabled(false);
        executor.execute(() -> {
            if (currentUser != null) {
                currentUser.name = name;
                currentUser.preference = major;
                userDao.updateUser(currentUser);
                prefs.edit()
                    .putString("name", name)
                    .putString("preference", major) // 同步学院信息到SharedPreferences
                    .apply();
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "个人信息保存成功", Toast.LENGTH_SHORT).show();
                    settingsBinding.btnSaveInfo.setEnabled(true);
                    loadUserInfo(); // Refresh displayed info
                });
            }
        });
    }

    private void changePassword(BottomSheetProfileSettingsBinding settingsBinding) {
        String currentPwd = settingsBinding.etCurrentPassword.getText() == null ? "" : 
                           settingsBinding.etCurrentPassword.getText().toString();
        String newPwd = settingsBinding.etNewPassword.getText() == null ? "" : 
                       settingsBinding.etNewPassword.getText().toString();
        String confirmPwd = settingsBinding.etConfirmPassword.getText() == null ? "" : 
                           settingsBinding.etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(currentPwd) || TextUtils.isEmpty(newPwd) || TextUtils.isEmpty(confirmPwd)) {
            Toast.makeText(requireContext(), "请填写所有密码字段", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPwd.equals(confirmPwd)) {
            Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPwd.length() < 6) {
            Toast.makeText(requireContext(), "新密码长度至少6位", Toast.LENGTH_SHORT).show();
            return;
        }

        settingsBinding.btnChangePassword.setEnabled(false);
        executor.execute(() -> {
            if (currentUser != null && currentUser.password.equals(currentPwd)) {
                currentUser.password = newPwd;
                userDao.updateUser(currentUser);
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "密码修改成功", Toast.LENGTH_SHORT).show();
                    settingsBinding.btnChangePassword.setEnabled(true);
                    settingsBinding.etCurrentPassword.setText("");
                    settingsBinding.etNewPassword.setText("");
                    settingsBinding.etConfirmPassword.setText("");
                });
            } else {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "当前密码错误", Toast.LENGTH_SHORT).show();
                    settingsBinding.btnChangePassword.setEnabled(true);
                });
            }
        });
    }

    private void openClassManagement() {
        // Navigate to class management - 使用Activity的FragmentManager确保正确导航
        try {
            ClassManageFragment fragment = new ClassManageFragment();
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .addToBackStack("ProfileFragment")
                    .commit();
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileFragment", "Error navigating to ClassManageFragment", e);
            Toast.makeText(requireContext(), "导航失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportData() {
        // 检查用户角色，只有教师可以导出数据
        if (currentUser == null || currentUser.role != 1) {
            new AlertDialog.Builder(requireContext())
                .setTitle("🚫 权限不足")
                .setMessage("数据导出功能仅限教师使用。\n\n学生用户可以在学习报告中查看个人学习数据。")
                .setPositiveButton("知道了", null)
                .show();
            return;
        }
        
        // 显示导出选项选择对话框
        String[] exportOptions = {
            "📊 按班级导出学生数据",
            "📈 导出所有学生统计",
            "💬 导出对话记录汇总"
        };
        
        new AlertDialog.Builder(requireContext())
            .setTitle("📤 数据导出")
            .setItems(exportOptions, (dialog, which) -> {
                switch (which) {
                    case 0:
                        exportStudentDataByClass();
                        break;
                    case 1:
                        exportAllStudentStats();
                        break;
                    case 2:
                        exportConversationSummary();
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 按班级导出学生数据
     */
    private void exportStudentDataByClass() {
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("准备导出数据")
            .setMessage("正在获取班级列表...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程获取教师的班级列表
        executor.execute(() -> {
            try {
                List<com.example.beihangagent.model.Class> teacherClasses = classDao.getClassesByTeacherSync(currentUserId);
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (teacherClasses.isEmpty()) {
                        Toast.makeText(requireContext(), "您还没有创建任何班级", Toast.LENGTH_SHORT).show();
                    } else {
                        showClassSelectionDialog(teacherClasses);
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "获取班级列表失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 导出所有学生统计
     */
    private void exportAllStudentStats() {
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("导出全体学生统计")
            .setMessage("正在统计所有学生数据...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程收集数据
        executor.execute(() -> {
            try {
                String markdownContent = generateAllStudentStatsMarkdown();
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    shareMarkdownReport(markdownContent, "全体学生统计");
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 显示班级选择对话框
     */
    private void showClassSelectionDialog(List<com.example.beihangagent.model.Class> classes) {
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("加载班级信息")
            .setMessage("正在获取班级学生信息...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程获取班级成员信息
        executor.execute(() -> {
            try {
                String[] classNames = new String[classes.size()];
                for (int i = 0; i < classes.size(); i++) {
                    com.example.beihangagent.model.Class classEntity = classes.get(i);
                    List<com.example.beihangagent.model.ClassMember> members = classDao.getClassMembersSync(classEntity.classId);
                    int memberCount = members.size();
                    classNames[i] = classEntity.className + " (共" + memberCount + "人)";
                }
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    
                    new AlertDialog.Builder(requireContext())
                        .setTitle("选择要导出的班级")
                        .setItems(classNames, (dialog, which) -> {
                            com.example.beihangagent.model.Class selectedClass = classes.get(which);
                            exportClassData(selectedClass);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "获取班级信息失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 导出指定班级的数据
     */
    private void exportClassData(com.example.beihangagent.model.Class classEntity) {
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("导出班级数据")
            .setMessage("正在收集 " + classEntity.className + " 的学生数据...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程收集数据
        executor.execute(() -> {
            try {
                String markdownContent = generateClassDataMarkdown(classEntity);
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    shareMarkdownReport(markdownContent, classEntity.className);
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 生成班级数据的Markdown报告
     */
    private String generateClassDataMarkdown(com.example.beihangagent.model.Class classEntity) {
        StringBuilder markdown = new StringBuilder();
        
        // 报告标题和基本信息
        markdown.append("# 📊 班级学习数据报告\n\n");
        markdown.append("**班级名称：** ").append(classEntity.className).append("\n");
        markdown.append("**班级代码：** ").append(classEntity.classCode).append("\n");
        markdown.append("**生成时间：** ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(new java.util.Date())).append("\n\n");
        
        // 获取班级学生列表
        List<com.example.beihangagent.model.ClassMember> members = classDao.getClassMembersSync(classEntity.classId);
        markdown.append("**班级人数：** ").append(members.size()).append("人\n\n");
        
        // 学生数据表格
        markdown.append("## 📈 学生学习统计\n\n");
        markdown.append("| 序号 | 学生姓名 | 用户名 | 提问次数 | 活跃天数 | 最近提问时间 |\n");
        markdown.append("|------|----------|--------|----------|----------|-------------|\n");
        
        int index = 1;
        for (com.example.beihangagent.model.ClassMember member : members) {
            User student = userDao.getUserById(member.studentId);
            if (student != null) {
                // 统计学生数据
                List<com.example.beihangagent.model.ChatMessage> studentMessages = chatMessageDao.getByUser(student.uid);
                int questionCount = 0;
                long lastQuestionTime = 0;
                
                for (com.example.beihangagent.model.ChatMessage message : studentMessages) {
                    if ("user".equals(message.role)) {
                        questionCount++;
                        if (message.timestamp > lastQuestionTime) {
                            lastQuestionTime = message.timestamp;
                        }
                    }
                }
                
                int activeDays = calculateActiveDaysForStudent(student.uid);
                String lastQuestionStr = lastQuestionTime > 0 ? 
                    new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date(lastQuestionTime)) : "无";
                
                String studentName = !android.text.TextUtils.isEmpty(student.name) ? student.name : "未设置";
                
                markdown.append(String.format("| %d | %s | %s | %d | %d | %s |\n", 
                    index++, studentName, student.username, questionCount, activeDays, lastQuestionStr));
            }
        }
        
        markdown.append("\n");
        
        // 添加详细的对话内容分析
        addDetailedConversationAnalysis(markdown, members);
        
        return markdown.toString();
    }
    
    /**
     * 生成全体学生统计的Markdown报告
     */
    private String generateAllStudentStatsMarkdown() {
        StringBuilder markdown = new StringBuilder();
        
        // 报告标题和基本信息
        markdown.append("# 📊 全体学生学习统计报告\n\n");
        markdown.append("**教师：** ").append(currentUser != null ? currentUser.name : currentUser.username).append("\n");
        markdown.append("**生成时间：** ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(new java.util.Date())).append("\n\n");
        
        // 获取教师的所有班级
        List<com.example.beihangagent.model.Class> teacherClasses = classDao.getClassesByTeacherSync(currentUserId);
        
        // 收集所有学生数据
        java.util.List<StudentStatInfo> allStudents = new java.util.ArrayList<>();
        java.util.Map<String, Integer> classStudentCount = new java.util.HashMap<>();
        
        for (com.example.beihangagent.model.Class classEntity : teacherClasses) {
            List<com.example.beihangagent.model.ClassMember> members = classDao.getClassMembersSync(classEntity.classId);
            classStudentCount.put(classEntity.className, members.size());
            
            for (com.example.beihangagent.model.ClassMember member : members) {
                User student = userDao.getUserById(member.studentId);
                if (student != null) {
                    StudentStatInfo statInfo = new StudentStatInfo();
                    statInfo.className = classEntity.className;
                    statInfo.studentName = !android.text.TextUtils.isEmpty(student.name) ? student.name : "未设置";
                    statInfo.username = student.username;
                    
                    // 统计学生数据
                    List<com.example.beihangagent.model.ChatMessage> studentMessages = chatMessageDao.getByUser(student.uid);
                    int questionCount = 0;
                    long lastQuestionTime = 0;
                    
                    for (com.example.beihangagent.model.ChatMessage message : studentMessages) {
                        if ("user".equals(message.role)) {
                            questionCount++;
                            if (message.timestamp > lastQuestionTime) {
                                lastQuestionTime = message.timestamp;
                            }
                        }
                    }
                    
                    statInfo.questionCount = questionCount;
                    statInfo.activeDays = calculateActiveDaysForStudent(student.uid);
                    statInfo.lastQuestionTime = lastQuestionTime;
                    
                    allStudents.add(statInfo);
                }
            }
        }
        
        // 班级概览
        markdown.append("## 📚 班级概览\n\n");
        markdown.append("| 班级名称 | 学生人数 | 总提问数 | 平均提问数 |\n");
        markdown.append("|----------|----------|----------|------------|\n");
        
        int totalStudents = 0;
        int totalQuestions = 0;
        
        for (com.example.beihangagent.model.Class classEntity : teacherClasses) {
            int classQuestions = 0;
            int classStudents = classStudentCount.get(classEntity.className);
            
            for (StudentStatInfo student : allStudents) {
                if (student.className.equals(classEntity.className)) {
                    classQuestions += student.questionCount;
                }
            }
            
            double avgQuestions = classStudents > 0 ? (double)classQuestions / classStudents : 0;
            markdown.append(String.format("| %s | %d | %d | %.1f |\n", 
                classEntity.className, classStudents, classQuestions, avgQuestions));
            
            totalStudents += classStudents;
            totalQuestions += classQuestions;
        }
        
        markdown.append("\n**总计：** ").append(teacherClasses.size()).append("个班级，")
            .append(totalStudents).append("名学生，").append(totalQuestions).append("次提问\n\n");
        
        // 学生详细统计
        markdown.append("## 👥 学生详细统计\n\n");
        markdown.append("| 序号 | 班级 | 学生姓名 | 用户名 | 提问次数 | 活跃天数 | 最近提问时间 |\n");
        markdown.append("|------|------|----------|--------|----------|----------|-------------|\n");
        
        // 按提问次数排序
        allStudents.sort((a, b) -> Integer.compare(b.questionCount, a.questionCount));
        
        for (int i = 0; i < allStudents.size(); i++) {
            StudentStatInfo student = allStudents.get(i);
            String lastQuestionStr = student.lastQuestionTime > 0 ? 
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date(student.lastQuestionTime)) : "无";
            
            markdown.append(String.format("| %d | %s | %s | %s | %d | %d | %s |\n", 
                i + 1, student.className, student.studentName, student.username, 
                student.questionCount, student.activeDays, lastQuestionStr));
        }
        
        // 添加统计分析
        addOverallStatisticsAnalysis(markdown, allStudents, totalStudents, totalQuestions);
        
        return markdown.toString();
    }
    
    /**
     * 学生统计信息内部类
     */
    private static class StudentStatInfo {
        String className;
        String studentName;
        String username;
        int questionCount;
        int activeDays;
        long lastQuestionTime;
    }
    
    /**
     * 添加整体统计分析
     */
    private void addOverallStatisticsAnalysis(StringBuilder markdown, java.util.List<StudentStatInfo> allStudents, int totalStudents, int totalQuestions) {
        markdown.append("\n## 📈 整体数据分析\n\n");
        
        if (allStudents.isEmpty()) {
            markdown.append("暂无学生数据。\n");
            return;
        }
        
        // 计算统计指标
        double avgQuestionsPerStudent = (double)totalQuestions / totalStudents;
        
        // 找出最活跃和最不活跃的学生
        StudentStatInfo mostActive = allStudents.get(0);
        StudentStatInfo leastActive = allStudents.get(allStudents.size() - 1);
        
        // 活跃度分布
        int highlyActive = 0;  // >10次
        int moderatelyActive = 0;  // 5-10次
        int lowActive = 0;  // 1-4次
        int inactive = 0;  // 0次
        
        for (StudentStatInfo student : allStudents) {
            if (student.questionCount > 10) {
                highlyActive++;
            } else if (student.questionCount >= 5) {
                moderatelyActive++;
            } else if (student.questionCount >= 1) {
                lowActive++;
            } else {
                inactive++;
            }
        }
        
        // 统计分析表格
        markdown.append("### 📊 关键指标\n\n");
        markdown.append("| 指标 | 数值 |\n");
        markdown.append("|------|------|\n");
        markdown.append(String.format("| 学生总数 | %d人 |\n", totalStudents));
        markdown.append(String.format("| 提问总数 | %d次 |\n", totalQuestions));
        markdown.append(String.format("| 人均提问数 | %.1f次 |\n", avgQuestionsPerStudent));
        markdown.append(String.format("| 最活跃学生 | %s (%d次) |\n", mostActive.studentName, mostActive.questionCount));
        markdown.append(String.format("| 参与率 | %.1f%% |\n", totalStudents > 0 ? ((double)(totalStudents - inactive) / totalStudents * 100) : 0));
        
        // 活跃度分布
        markdown.append("\n### 🎯 学生活跃度分布\n\n");
        markdown.append("| 活跃程度 | 人数 | 占比 |\n");
        markdown.append("|----------|------|------|\n");
        markdown.append(String.format("| 高度活跃 (>10次) | %d | %.1f%% |\n", highlyActive, (double)highlyActive / totalStudents * 100));
        markdown.append(String.format("| 中度活跃 (5-10次) | %d | %.1f%% |\n", moderatelyActive, (double)moderatelyActive / totalStudents * 100));
        markdown.append(String.format("| 低度活跃 (1-4次) | %d | %.1f%% |\n", lowActive, (double)lowActive / totalStudents * 100));
        markdown.append(String.format("| 暂未参与 (0次) | %d | %.1f%% |\n", inactive, (double)inactive / totalStudents * 100));
        
        // 教学建议
        markdown.append("\n### 💡 教学建议\n\n");
        if (inactive > 0) {
            markdown.append("1. **关注未参与学生** - 有").append(inactive).append("名学生尚未使用学习助手，建议个别指导\n");
        }
        if (avgQuestionsPerStudent < 5) {
            markdown.append("2. **提高参与度** - 人均提问次数较低，可以通过课堂引导增加学生互动\n");
        }
        if (highlyActive > 0) {
            markdown.append("3. **发挥榜样作用** - 可以让活跃学生分享学习心得，带动其他同学\n");
        }
        
        markdown.append("\n---\n");
        markdown.append("*北航智教助手自动生成*\n");
    }
    
    /**
     * 添加详细的对话内容分析
     */
    private void addDetailedConversationAnalysis(StringBuilder markdown, List<com.example.beihangagent.model.ClassMember> members) {
        markdown.append("## 💬 对话内容分析\n\n");
        
        if (members.isEmpty()) {
            markdown.append("暂无班级成员数据。\n");
            return;
        }
        
        // 收集所有对话消息
        java.util.List<com.example.beihangagent.model.ChatMessage> allMessages = new java.util.ArrayList<>();
        for (com.example.beihangagent.model.ClassMember member : members) {
            List<com.example.beihangagent.model.ChatMessage> studentMessages = chatMessageDao.getByUser(member.studentId);
            allMessages.addAll(studentMessages);
        }
        
        if (allMessages.isEmpty()) {
            markdown.append("暂无对话记录。\n");
            return;
        }
        
        // 话题关键词分析
        java.util.Map<String, Integer> topicKeywords = analyzeTopicKeywords(allMessages);
        
        if (!topicKeywords.isEmpty()) {
            markdown.append("### 🔍 热门话题关键词\n\n");
            markdown.append("| 关键词 | 出现次数 |\n");
            markdown.append("|--------|----------|\n");
            
            // 按频次排序并取前10个
            topicKeywords.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> 
                    markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n")
                );
        }
        
        // 提问类型分析
        analyzeQuestionTypes(markdown, allMessages);
        
        // 活跃时间分析
        analyzeActiveTime(markdown, allMessages);
        
        // 最近热门问题
        addRecentPopularQuestions(markdown, allMessages);
    }
    
    /**
     * 分析话题关键词
     */
    private java.util.Map<String, Integer> analyzeTopicKeywords(List<com.example.beihangagent.model.ChatMessage> messages) {
        java.util.Map<String, Integer> keywords = new java.util.HashMap<>();
        
        // 定义一些学科相关的关键词
        String[] techKeywords = {
            "Java", "Python", "Android", "数据库", "算法", "编程", "代码", 
            "函数", "变量", "循环", "条件", "类", "对象", "方法", "接口",
            "数组", "链表", "树", "图", "排序", "查找", "设计模式",
            "网络", "HTTP", "API", "JSON", "XML", "SQL", "数据结构",
            "线程", "并发", "异步", "同步", "框架", "库", "工具"
        };
        
        for (com.example.beihangagent.model.ChatMessage message : messages) {
            if ("user".equals(message.role) && !android.text.TextUtils.isEmpty(message.content)) {
                String content = message.content.toLowerCase();
                
                for (String keyword : techKeywords) {
                    if (content.contains(keyword.toLowerCase())) {
                        keywords.put(keyword, keywords.getOrDefault(keyword, 0) + 1);
                    }
                }
            }
        }
        
        return keywords;
    }
    
    /**
     * 分析提问类型
     */
    private void analyzeQuestionTypes(StringBuilder markdown, List<com.example.beihangagent.model.ChatMessage> messages) {
        int howQuestions = 0;
        int whatQuestions = 0; 
        int whyQuestions = 0;
        int errorQuestions = 0;
        int codeQuestions = 0;
        
        for (com.example.beihangagent.model.ChatMessage message : messages) {
            if ("user".equals(message.role) && !android.text.TextUtils.isEmpty(message.content)) {
                String content = message.content.toLowerCase();
                
                if (content.contains("怎么") || content.contains("如何") || content.contains("怎样")) {
                    howQuestions++;
                } else if (content.contains("什么") || content.contains("是什么")) {
                    whatQuestions++;
                } else if (content.contains("为什么") || content.contains("原理")) {
                    whyQuestions++;
                } else if (content.contains("错误") || content.contains("报错") || content.contains("异常") || content.contains("bug")) {
                    errorQuestions++;
                } else if (content.contains("代码") || content.contains("函数") || content.contains("方法")) {
                    codeQuestions++;
                }
            }
        }
        
        markdown.append("\n### ❓ 提问类型分布\n\n");
        markdown.append("| 问题类型 | 数量 | 说明 |\n");
        markdown.append("|----------|------|------|\n");
        markdown.append("| 操作型 (怎么/如何) | ").append(howQuestions).append(" | 询问具体操作方法 |\n");
        markdown.append("| 概念型 (什么/是什么) | ").append(whatQuestions).append(" | 询问概念定义 |\n");
        markdown.append("| 原理型 (为什么/原理) | ").append(whyQuestions).append(" | 询问工作原理 |\n");
        markdown.append("| 错误型 (错误/异常) | ").append(errorQuestions).append(" | 遇到问题求助 |\n");
        markdown.append("| 代码型 (代码/函数) | ").append(codeQuestions).append(" | 代码相关问题 |\n");
    }
    
    /**
     * 分析活跃时间
     */
    private void analyzeActiveTime(StringBuilder markdown, List<com.example.beihangagent.model.ChatMessage> messages) {
        int[] hourStats = new int[24];
        
        for (com.example.beihangagent.model.ChatMessage message : messages) {
            if ("user".equals(message.role)) {
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.setTimeInMillis(message.timestamp);
                int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
                hourStats[hour]++;
            }
        }
        
        // 找出最活跃的时间段
        int maxHour = 0;
        int maxCount = hourStats[0];
        for (int i = 1; i < 24; i++) {
            if (hourStats[i] > maxCount) {
                maxCount = hourStats[i];
                maxHour = i;
            }
        }
        
        markdown.append("\n### ⏰ 学习时间分析\n\n");
        markdown.append("**最活跃时间段：** ").append(maxHour).append(":00-").append(maxHour + 1).append(":00 (")
            .append(maxCount).append("次提问)\n\n");
        
        // 时间段统计
        int morningCount = 0;  // 6-12
        int afternoonCount = 0;  // 12-18
        int eveningCount = 0;  // 18-24
        int nightCount = 0;  // 0-6
        
        for (int i = 0; i < 24; i++) {
            if (i >= 6 && i < 12) {
                morningCount += hourStats[i];
            } else if (i >= 12 && i < 18) {
                afternoonCount += hourStats[i];
            } else if (i >= 18 && i < 24) {
                eveningCount += hourStats[i];
            } else {
                nightCount += hourStats[i];
            }
        }
        
        markdown.append("| 时间段 | 提问次数 |\n");
        markdown.append("|--------|----------|\n");
        markdown.append("| 上午 (6:00-12:00) | ").append(morningCount).append(" |\n");
        markdown.append("| 下午 (12:00-18:00) | ").append(afternoonCount).append(" |\n");
        markdown.append("| 晚上 (18:00-24:00) | ").append(eveningCount).append(" |\n");
        markdown.append("| 深夜 (0:00-6:00) | ").append(nightCount).append(" |\n");
    }
    
    /**
     * 添加最近热门问题
     */
    private void addRecentPopularQuestions(StringBuilder markdown, List<com.example.beihangagent.model.ChatMessage> messages) {
        markdown.append("\n### 🔥 最近热门问题\n\n");
        
        // 获取最近7天的用户问题
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        java.util.List<String> recentQuestions = new java.util.ArrayList<>();
        
        for (com.example.beihangagent.model.ChatMessage message : messages) {
            if ("user".equals(message.role) && message.timestamp > sevenDaysAgo && 
                !android.text.TextUtils.isEmpty(message.content) && 
                message.content.length() > 10) {  // 过滤太短的问题
                recentQuestions.add(message.content);
            }
        }
        
        if (recentQuestions.isEmpty()) {
            markdown.append("最近7天暂无问题记录。\n");
            return;
        }
        
        // 显示最近的几个问题（限制长度）
        int count = 0;
        for (String question : recentQuestions) {
            if (count >= 5) break;  // 只显示前5个
            
            String shortQuestion = question.length() > 100 ? 
                question.substring(0, 100) + "..." : question;
            
            markdown.append("- ").append(shortQuestion).append("\n");
            count++;
        }
        
        markdown.append("\n*共 ").append(recentQuestions.size()).append(" 个最近问题*\n");
    }
    
    /**
     * 导出对话摘要
     */
    private void exportConversationSummary() {
        // 显示加载对话框
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("导出对话摘要")
            .setMessage("正在生成对话摘要，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程执行
        executor.execute(() -> {
            try {
                String markdown = generateConversationSummaryMarkdown();
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    shareMarkdownReport(markdown, "对话摘要");
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 分享Markdown报告
     */
    private void shareMarkdownReport(String markdownContent, String className) {
        // 显示导出格式选择对话框
        String[] exportFormats = {"📄 导出为 Markdown", "📃 导出为 PDF"};
        
        new AlertDialog.Builder(requireContext())
            .setTitle("选择导出格式")
            .setItems(exportFormats, (dialog, which) -> {
                switch (which) {
                    case 0:
                        // 导出Markdown
                        exportAsMarkdown(markdownContent, className);
                        break;
                    case 1:
                        // 导出PDF
                        exportAsPDF(markdownContent, className);
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 导出为Markdown格式
     */
    private void exportAsMarkdown(String markdownContent, String className) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, className + " - 学习数据报告");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, markdownContent);
        
        // 保存到文件
        try {
            saveMarkdownToFile(markdownContent, className);
            Toast.makeText(requireContext(), "Markdown报告已生成，请选择分享方式", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "文件保存失败，但可以直接分享", Toast.LENGTH_SHORT).show();
        }
        
        startActivity(android.content.Intent.createChooser(shareIntent, "分享Markdown报告"));
    }
    
    /**
     * 导出为PDF格式
     */
    private void exportAsPDF(String markdownContent, String className) {
        // 显示加载对话框
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("生成PDF报告")
            .setMessage("正在将报告转换为PDF格式，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程生成PDF
        executor.execute(() -> {
            try {
                java.io.File pdfFile = generatePDFFromMarkdown(markdownContent, className);
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (pdfFile != null) {
                        sharePDFFile(pdfFile, className);
                        Toast.makeText(requireContext(), "PDF报告已生成", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), "PDF生成失败", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "PDF生成失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 从Markdown生成PDF文件
     */
    private java.io.File generatePDFFromMarkdown(String markdownContent, String className) throws Exception {
        String fileName = className + "_学习报告_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(new java.util.Date()) + ".pdf";
        
        java.io.File reportsDir = new java.io.File(requireContext().getExternalFilesDir(null), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        
        java.io.File pdfFile = new java.io.File(reportsDir, fileName);
        
        // 使用WebView渲染生成PDF（更好的中文支持）
        String htmlContent = convertMarkdownToStyledHTML(markdownContent);
        return generatePDFFromHTML(htmlContent, pdfFile);
    }
    
    /**
     * 转换Markdown为完整的HTML格式（包含样式）
     */
    private String convertMarkdownToStyledHTML(String markdown) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        html.append("<style>");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 40px; line-height: 1.8; color: #333; background: #fff; }");
        html.append("h1 { color: #1976D2; border-bottom: 3px solid #1976D2; padding-bottom: 15px; margin-bottom: 30px; font-size: 28px; font-weight: 700; }");
        html.append("h2 { color: #1976D2; margin: 40px 0 20px 0; font-size: 22px; font-weight: 600; border-left: 4px solid #1976D2; padding-left: 15px; }");
        html.append("h3 { color: #424242; margin: 30px 0 15px 0; font-size: 18px; font-weight: 600; }");
        html.append("table { border-collapse: collapse; width: 100%; margin: 20px 0; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-radius: 8px; overflow: hidden; }");
        html.append("th { background: linear-gradient(135deg, #1976D2, #42A5F5); color: white; padding: 12px 8px; text-align: center; font-weight: 600; font-size: 13px; }");
        html.append("td { border: 1px solid #e0e0e0; padding: 10px 8px; text-align: left; }");
        html.append("tr:nth-child(even) td { background-color: #f8f9fa; }");
        html.append("tr:hover td { background-color: #e3f2fd; }");
        html.append("strong, b { color: #1976D2; font-weight: 600; }");
        html.append("hr { border: none; height: 2px; background: linear-gradient(90deg, #1976D2, #e0e0e0); margin: 30px 0; border-radius: 1px; }");
        html.append("ul, ol { margin: 15px 0; padding-left: 25px; }");
        html.append("li { margin: 8px 0; line-height: 1.6; }");
        html.append(".emoji { font-size: 16px; }");
        html.append("p { margin: 12px 0; line-height: 1.7; }");
        html.append(".highlight { background: #fff3cd; padding: 2px 6px; border-radius: 3px; }");
        html.append("@media print { body { margin: 20px; } h1 { break-before: avoid; } table { break-inside: avoid; } }");
        html.append("</style></head><body>");
        
        String htmlBody = markdown;
        
        // 处理标题并添加锚点
        htmlBody = htmlBody.replaceAll("(?m)^# (.*?)$", "<h1 id='title'>$1</h1>");
        htmlBody = htmlBody.replaceAll("(?m)^## (.*?)$", "<h2>$1</h2>");
        htmlBody = htmlBody.replaceAll("(?m)^### (.*?)$", "<h3>$1</h3>");
        
        // 处理粗体和高亮
        htmlBody = htmlBody.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        
        // 改进的表格处理
        String[] lines = htmlBody.split("\n");
        StringBuilder processedContent = new StringBuilder();
        boolean inTable = false;
        boolean isHeaderRow = true;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.startsWith("|") && line.endsWith("|")) {
                if (!inTable) {
                    processedContent.append("<table>\n");
                    inTable = true;
                    isHeaderRow = true;
                }
                
                // 检查是否是分隔行
                if (line.matches("\\|[\\s\\-\\|]*\\|")) {
                    continue; // 跳过分隔行
                }
                
                // 处理表格行
                String[] cells = line.substring(1, line.length() - 1).split("\\|");
                String tag = isHeaderRow ? "th" : "td";
                
                processedContent.append("<tr>");
                for (String cell : cells) {
                    processedContent.append("<").append(tag).append(">")
                            .append(cell.trim())
                            .append("</").append(tag).append(">");
                }
                processedContent.append("</tr>\n");
                
                isHeaderRow = false;
            } else {
                if (inTable) {
                    processedContent.append("</table>\n");
                    inTable = false;
                }
                
                // 处理其他内容
                if (line.matches("^[0-9]+\\. .*")) {
                    processedContent.append("<li>").append(line.substring(line.indexOf(". ") + 2)).append("</li>\n");
                } else if (line.startsWith("- ")) {
                    processedContent.append("<li>").append(line.substring(2)).append("</li>\n");
                } else if (line.equals("---")) {
                    processedContent.append("<hr>\n");
                } else if (!line.isEmpty()) {
                    processedContent.append("<p>").append(line).append("</p>\n");
                } else {
                    processedContent.append("<br>\n");
                }
            }
        }
        
        if (inTable) {
            processedContent.append("</table>\n");
        }
        
        html.append(processedContent.toString());
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * 从HTML生成PDF文件（直接使用Canvas渲染）
     */
    private java.io.File generatePDFFromHTML(String htmlContent, java.io.File pdfFile) throws Exception {
        // 直接使用Canvas方式，避免WebView的复杂性和潜在问题
        boolean success = generatePDFWithSimpleCanvas(htmlContent, pdfFile);
        
        if (!success) {
            throw new Exception("PDF生成失败");
        }
        
        return pdfFile;
    }
    
    /**
     * 使用简单Canvas API生成PDF（兼容低版本Android）
     */
    private boolean generatePDFWithSimpleCanvas(String htmlContent, java.io.File pdfFile) {
        try {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            
            // 改进的HTML内容解析，保持更多格式信息
            String textContent = parseHTMLContent(htmlContent);
            
            // 创建PDF页面
            renderFormattedTextToPDF(document, textContent);
            
            // 写入文件
            java.io.FileOutputStream fos = new java.io.FileOutputStream(pdfFile);
            document.writeTo(fos);
            document.close();
            fos.close();
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 解析HTML内容，保持格式标记（优化版本）
     */
    private String parseHTMLContent(String htmlContent) {
        try {
            // 快速移除HTML文档结构和样式（简化版本）
            String cleanContent = htmlContent;
            
            // 移除常见的HTML结构
            cleanContent = cleanContent.replaceAll("(?i)<!DOCTYPE[^>]*>", "");
            cleanContent = cleanContent.replaceAll("(?i)<html[^>]*>", "");
            cleanContent = cleanContent.replaceAll("(?i)</html>", "");
            cleanContent = cleanContent.replaceAll("(?i)<head>.*?</head>", "");
            cleanContent = cleanContent.replaceAll("(?i)<style[^>]*>.*?</style>", "");
            cleanContent = cleanContent.replaceAll("(?i)<script[^>]*>.*?</script>", "");
            cleanContent = cleanContent.replaceAll("(?i)<body[^>]*>", "");
            cleanContent = cleanContent.replaceAll("(?i)</body>", "");
            cleanContent = cleanContent.replaceAll("(?i)<meta[^>]*>", "");
            
            // 转换为简单格式标记
            cleanContent = cleanContent.replaceAll("(?i)<h1[^>]*>(.*?)</h1>", "\n[H1]$1[/H1]\n");
            cleanContent = cleanContent.replaceAll("(?i)<h2[^>]*>(.*?)</h2>", "\n[H2]$1[/H2]\n");
            cleanContent = cleanContent.replaceAll("(?i)<h3[^>]*>(.*?)</h3>", "\n[H3]$1[/H3]\n");
            cleanContent = cleanContent.replaceAll("(?i)<strong[^>]*>(.*?)</strong>", "[BOLD]$1[/BOLD]");
            cleanContent = cleanContent.replaceAll("(?i)<b[^>]*>(.*?)</b>", "[BOLD]$1[/BOLD]");
            cleanContent = cleanContent.replaceAll("(?i)<table[^>]*>", "\n[TABLE_START]\n");
            cleanContent = cleanContent.replaceAll("(?i)</table>", "\n[TABLE_END]\n");
            cleanContent = cleanContent.replaceAll("(?i)<tr[^>]*>", "[ROW_START]");
            cleanContent = cleanContent.replaceAll("(?i)</tr>", "[ROW_END]\n");
            cleanContent = cleanContent.replaceAll("(?i)<th[^>]*>(.*?)</th>", "[TH]$1[/TH]");
            cleanContent = cleanContent.replaceAll("(?i)<td[^>]*>(.*?)</td>", "[TD]$1[/TD]");
            cleanContent = cleanContent.replaceAll("(?i)<hr[^>]*>", "\n[HR]\n");
            cleanContent = cleanContent.replaceAll("(?i)<br[^>]*>", "\n");
            cleanContent = cleanContent.replaceAll("(?i)<p[^>]*>", "\n");
            cleanContent = cleanContent.replaceAll("(?i)</p>", "\n");
            cleanContent = cleanContent.replaceAll("(?i)<li[^>]*>(.*?)</li>", "• $1\n");
            
            // 移除其他HTML标签
            cleanContent = cleanContent.replaceAll("<[^>]+>", "");
            
            // 解码HTML实体
            cleanContent = cleanContent.replaceAll("&nbsp;", " ");
            cleanContent = cleanContent.replaceAll("&lt;", "<");
            cleanContent = cleanContent.replaceAll("&gt;", ">");
            cleanContent = cleanContent.replaceAll("&amp;", "&");
            cleanContent = cleanContent.replaceAll("&quot;", "\"");
            
            // 清理多余空白
            cleanContent = cleanContent.replaceAll("[\n]{3,}", "\n\n");
            cleanContent = cleanContent.trim();
            
            return cleanContent;
            
        } catch (Exception e) {
            // 如果解析失败，返回简化的纯文本版本
            return htmlContent.replaceAll("<[^>]+>", "").replaceAll("&[^;]+;", " ").trim();
        }
    }
    
    /**
     * 渲染格式化文本到PDF
     */
    private void renderFormattedTextToPDF(android.graphics.pdf.PdfDocument document, String textContent) {
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = 
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
        
        android.graphics.Canvas canvas = page.getCanvas();
        
        // 创建不同的画笔
        android.graphics.Paint normalPaint = createTextPaint(11, android.graphics.Color.BLACK, false);
        android.graphics.Paint h1Paint = createTextPaint(20, android.graphics.Color.parseColor("#1976D2"), true);
        android.graphics.Paint h2Paint = createTextPaint(16, android.graphics.Color.parseColor("#1976D2"), true);
        android.graphics.Paint h3Paint = createTextPaint(14, android.graphics.Color.parseColor("#424242"), true);
        android.graphics.Paint boldPaint = createTextPaint(11, android.graphics.Color.parseColor("#1976D2"), true);
        android.graphics.Paint tablePaint = createTextPaint(10, android.graphics.Color.BLACK, false);
        android.graphics.Paint tableHeaderPaint = createTextPaint(10, android.graphics.Color.WHITE, true);
        
        float y = 60;
        int margin = 50;
        int pageWidth = pageInfo.getPageWidth() - 2 * margin;
        boolean inTable = false;
        int tableRowHeight = 25;
        
        String[] lines = textContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // 检查是否需要新页面
            if (y > pageInfo.getPageHeight() - 100) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 60;
                inTable = false; // 新页面重置表格状态
            }
            
            if (line.isEmpty()) {
                y += 10;
                continue;
            }
            
            // 处理不同的内容类型
            if (line.contains("[H1]")) {
                String title = extractContent(line, "H1");
                drawText(canvas, title, margin, y, h1Paint, pageWidth);
                y += 30;
                // 绘制标题下划线
                canvas.drawLine(margin, y - 5, margin + pageWidth * 0.6f, y - 5, h1Paint);
                y += 15;
                
            } else if (line.contains("[H2]")) {
                String subtitle = extractContent(line, "H2");
                y += 10;
                drawText(canvas, subtitle, margin, y, h2Paint, pageWidth);
                y += 25;
                
            } else if (line.contains("[H3]")) {
                String heading = extractContent(line, "H3");
                y += 8;
                drawText(canvas, heading, margin, y, h3Paint, pageWidth);
                y += 20;
                
            } else if (line.contains("[TABLE_START]")) {
                inTable = true;
                y += 10;
                
            } else if (line.contains("[TABLE_END]")) {
                inTable = false;
                y += 15;
                
            } else if (line.contains("[HR]")) {
                y += 10;
                android.graphics.Paint hrPaint = new android.graphics.Paint();
                hrPaint.setColor(android.graphics.Color.parseColor("#E0E0E0"));
                hrPaint.setStrokeWidth(2);
                canvas.drawLine(margin, y, margin + pageWidth, y, hrPaint);
                y += 15;
                
            } else if (inTable && line.contains("[ROW_START]")) {
                // 处理表格行
                y += 5;
                String[] cells = extractTableCells(line);
                float cellWidth = (float) pageWidth / cells.length;
                float currentX = margin;
                
                boolean isHeader = line.contains("[TH]");
                android.graphics.Paint cellPaint = isHeader ? tableHeaderPaint : tablePaint;
                
                // 绘制表格行背景
                if (isHeader) {
                    android.graphics.Paint bgPaint = new android.graphics.Paint();
                    bgPaint.setColor(android.graphics.Color.parseColor("#1976D2"));
                    canvas.drawRect(margin, y - tableRowHeight + 5, margin + pageWidth, y + 5, bgPaint);
                }
                
                // 绘制单元格内容
                for (String cell : cells) {
                    drawTableCell(canvas, cell, currentX, y, cellWidth, tableRowHeight, cellPaint);
                    currentX += cellWidth;
                }
                
                y += tableRowHeight;
                
            } else if (!line.contains("[") && !line.isEmpty()) {
                // 处理普通文本内容
                if (isValidContent(line)) {
                    // 处理普通文本，支持粗体
                    String processedLine = line;
                    android.graphics.Paint textPaint = normalPaint;
                    
                    if (line.contains("[BOLD]")) {
                        processedLine = processedLine.replaceAll("\\[BOLD\\](.*?)\\[/BOLD\\]", "$1");
                        textPaint = boldPaint;
                    }
                    
                    drawText(canvas, processedLine, margin, y, textPaint, pageWidth);
                    y += 16;
                } else {
                    // 对于被过滤的内容，我们可以在日志中记录（调试用）
                    android.util.Log.d("PDFDebug", "Filtered content: " + line);
                }
            } else if (line.contains("[") && !line.contains("[H") && !line.contains("[TABLE") && !line.contains("[ROW") && !line.contains("[TH") && !line.contains("[TD") && !line.contains("[HR")) {
                // 处理可能遗漏的格式化文本
                String cleanLine = line.replaceAll("\\[[^\\]]*\\]", "").trim();
                if (!cleanLine.isEmpty() && isValidContent(cleanLine)) {
                    drawText(canvas, cleanLine, margin, y, normalPaint, pageWidth);
                    y += 16;
                }
            }
        }
        
        document.finishPage(page);
    }
    
    /**
     * 验证内容是否有效（更宽松的版本）
     */
    private boolean isValidContent(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        line = line.trim();
        
        // 只过滤明确的CSS样式代码
        if (line.contains("font-family:") || 
            line.contains("background:") || 
            line.contains("margin:") || 
            line.contains("padding:") || 
            line.contains("border:") || 
            line.contains("width:") || 
            line.contains("height:") ||
            line.matches("^[a-z\\-]+\\s*\\{.*") || // CSS选择器开始
            line.matches(".*\\}\\s*$") || // CSS块结束
            line.matches("^[a-z\\-]+\\s*:\\s*[^;]*;\\s*$")) { // 单行CSS属性
            return false;
        }
        
        // 过滤纯符号行（但保留包含文字的行）
        if (line.matches("^[\\s\\-\\|\\+\\*\\=\\~\\{\\}\\(\\)\\[\\]\\;\\:\\,\\.]+$")) {
            return false;
        }
        
        // 更宽松的内容验证：只要包含字母、数字、中文或常用标点符号即可
        return line.matches(".*[\\p{L}\\p{N}\\u4e00-\\u9fa5\\u3000-\\u303F\\uFF00-\\uFFEF]+.*");
    }
    
    /**
     * 创建文本画笔
     */
    private android.graphics.Paint createTextPaint(int textSize, int color, boolean bold) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setFakeBoldText(bold);
        return paint;
    }
    
    /**
     * 提取标签内容
     */
    private String extractContent(String line, String tag) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[" + tag + "\\](.*?)\\[/" + tag + "\\]");
        java.util.regex.Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : line;
    }
    
    /**
     * 提取表格单元格
     */
    private String[] extractTableCells(String line) {
        java.util.List<String> cells = new java.util.ArrayList<>();
        java.util.regex.Pattern thPattern = java.util.regex.Pattern.compile("\\[TH\\](.*?)\\[/TH\\]");
        java.util.regex.Pattern tdPattern = java.util.regex.Pattern.compile("\\[TD\\](.*?)\\[/TD\\]");
        
        java.util.regex.Matcher thMatcher = thPattern.matcher(line);
        java.util.regex.Matcher tdMatcher = tdPattern.matcher(line);
        
        while (thMatcher.find()) {
            cells.add(thMatcher.group(1));
        }
        while (tdMatcher.find()) {
            cells.add(tdMatcher.group(1));
        }
        
        return cells.toArray(new String[0]);
    }
    
    /**
     * 绘制表格单元格
     */
    private void drawTableCell(android.graphics.Canvas canvas, String text, float x, float y, float width, float height, android.graphics.Paint paint) {
        // 绘制边框
        android.graphics.Paint borderPaint = new android.graphics.Paint();
        borderPaint.setColor(android.graphics.Color.parseColor("#E0E0E0"));
        borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1);
        canvas.drawRect(x, y - height + 5, x + width, y + 5, borderPaint);
        
        // 绘制文本（居中）
        android.graphics.Rect bounds = new android.graphics.Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        float textX = x + (width - bounds.width()) / 2;
        float textY = y - height / 2 + bounds.height() / 2;
        canvas.drawText(text, textX, textY, paint);
    }
    
    /**
     * 绘制自动换行文本
     */
    private void drawText(android.graphics.Canvas canvas, String text, float x, float y, android.graphics.Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint);
        } else {
            // 自动换行
            java.util.List<String> lines = wrapText(text, maxWidth, paint);
            float currentY = y;
            for (String line : lines) {
                canvas.drawText(line, x, currentY, paint);
                currentY += paint.getTextSize() * 1.2f;
            }
        }
    }
    
    /**
     * 渲染文本到PDF
     */
    private void renderTextToPDF(android.graphics.pdf.PdfDocument document, String textContent) {
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = 
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
        
        android.graphics.Canvas canvas = page.getCanvas();
        android.graphics.Paint paint = new android.graphics.Paint();
        android.graphics.Paint titlePaint = new android.graphics.Paint();
        android.graphics.Paint headerPaint = new android.graphics.Paint();
        
        // 设置字体样式
        paint.setTextSize(11);
        paint.setColor(android.graphics.Color.BLACK);
        paint.setAntiAlias(true);
        
        titlePaint.setTextSize(20);
        titlePaint.setColor(android.graphics.Color.parseColor("#1976D2"));
        titlePaint.setFakeBoldText(true);
        titlePaint.setAntiAlias(true);
        
        headerPaint.setTextSize(14);
        headerPaint.setColor(android.graphics.Color.parseColor("#1976D2"));
        headerPaint.setFakeBoldText(true);
        headerPaint.setAntiAlias(true);
        
        float y = 60;
        float lineHeight = 18;
        int margin = 50;
        int pageWidth = pageInfo.getPageWidth() - 2 * margin;
        int currentPage = 1;
        
        String[] lines = textContent.split("\n");
        for (String line : lines) {
            // 检查是否需要新页面
            if (y > pageInfo.getPageHeight() - 80) {
                document.finishPage(page);
                currentPage++;
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 60;
            }
            
            line = line.trim();
            if (line.isEmpty()) {
                y += lineHeight * 0.5f;
                continue;
            }
            
            // 根据内容类型选择画笔
            android.graphics.Paint currentPaint = paint;
            if (line.startsWith("■")) {
                currentPaint = titlePaint;
                lineHeight = 25;
                y += 10;
            } else if (line.startsWith("▶")) {
                currentPaint = headerPaint;
                lineHeight = 20;
                y += 8;
            } else if (line.startsWith("●")) {
                currentPaint = headerPaint;
                currentPaint.setTextSize(12);
                lineHeight = 18;
                y += 5;
            } else {
                lineHeight = 16;
            }
            
            // 处理长行换行
            if (currentPaint.measureText(line) > pageWidth) {
                java.util.List<String> wrappedLines = wrapText(line, pageWidth, currentPaint);
                for (String wrappedLine : wrappedLines) {
                    canvas.drawText(wrappedLine, margin, y, currentPaint);
                    y += lineHeight;
                    
                    if (y > pageInfo.getPageHeight() - 80) {
                        document.finishPage(page);
                        currentPage++;
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 60;
                    }
                }
            } else {
                canvas.drawText(line, margin, y, currentPaint);
                y += lineHeight;
            }
        }
        
        document.finishPage(page);
    }
    
    /**
     * 文本换行处理
     */
    private java.util.List<String> wrapText(String text, float maxWidth, android.graphics.Paint paint) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (paint.measureText(testLine) > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * 分享PDF文件
     */
    private void sharePDFFile(java.io.File pdfFile, String className) {
        android.net.Uri pdfUri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            requireContext().getPackageName() + ".fileprovider",
            pdfFile
        );
        
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, pdfUri);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, className + " - 学习数据报告");
        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(android.content.Intent.createChooser(shareIntent, "分享PDF报告"));
    }
    
    /**
     * 保存Markdown到文件
     */
    private void saveMarkdownToFile(String content, String className) throws Exception {
        String fileName = className + "_学习报告_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(new java.util.Date()) + ".md";
        
        java.io.File reportsDir = new java.io.File(requireContext().getExternalFilesDir(null), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        
        java.io.File reportFile = new java.io.File(reportsDir, fileName);
        java.io.FileWriter writer = new java.io.FileWriter(reportFile);
        writer.write(content);
        writer.close();
        
        android.util.Log.d("ProfileFragment", "Report saved to: " + reportFile.getAbsolutePath());
    }
    
    /**
     * 生成对话摘要的Markdown报告
     */
    private String generateConversationSummaryMarkdown() {
        StringBuilder markdown = new StringBuilder();
        
        // 报告标题和基本信息
        markdown.append("# 💬 对话摘要统计报告\n\n");
        markdown.append("**教师：** ").append(currentUser != null ? currentUser.name : currentUser.username).append("\n");
        markdown.append("**生成时间：** ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(new java.util.Date())).append("\n\n");
        
        // 获取教师的所有班级
        List<com.example.beihangagent.model.Class> teacherClasses = classDao.getClassesByTeacherSync(currentUserId);
        
        if (teacherClasses.isEmpty()) {
            markdown.append("暂无班级数据。\n");
            return markdown.toString();
        }
        
        // 收集所有对话数据
        java.util.List<ConversationStat> allConversations = new java.util.ArrayList<>();
        int totalQuestions = 0;
        int totalStudents = 0;
        
        for (com.example.beihangagent.model.Class classEntity : teacherClasses) {
            List<com.example.beihangagent.model.ClassMember> members = classDao.getClassMembersSync(classEntity.classId);
            totalStudents += members.size();
            
            for (com.example.beihangagent.model.ClassMember member : members) {
                User student = userDao.getUserById(member.studentId);
                if (student != null) {
                    List<com.example.beihangagent.model.ChatMessage> studentMessages = chatMessageDao.getByUser(student.uid);
                    
                    for (com.example.beihangagent.model.ChatMessage message : studentMessages) {
                        if ("user".equals(message.role)) {
                            ConversationStat stat = new ConversationStat();
                            stat.className = classEntity.className;
                            stat.studentName = !android.text.TextUtils.isEmpty(student.name) ? student.name : student.username;
                            stat.question = message.content;
                            stat.timestamp = message.timestamp;
                            stat.questionType = categorizeQuestion(message.content);
                            allConversations.add(stat);
                            totalQuestions++;
                        }
                    }
                }
            }
        }
        
        // 基础统计信息
        markdown.append("## 📊 总体概况\n\n");
        markdown.append("| 统计项 | 数量 |\n");
        markdown.append("|-------|------|\n");
        markdown.append("| 班级总数 | ").append(teacherClasses.size()).append(" |\n");
        markdown.append("| 学生总数 | ").append(totalStudents).append(" |\n");
        markdown.append("| 对话总数 | ").append(totalQuestions).append(" |\n");
        markdown.append("| 平均每生提问 | ").append(String.format("%.1f", 
            totalStudents > 0 ? (double)totalQuestions / totalStudents : 0)).append(" |\n\n");
        
        // 按班级分组的对话统计
        addClassConversationBreakdown(markdown, teacherClasses, allConversations);
        
        // 热门话题分析
        addPopularTopicsAnalysis(markdown, allConversations);
        
        // 最近活跃对话
        addRecentActiveConversations(markdown, allConversations);
        
        // 问题类型趋势
        addQuestionTypeTrends(markdown, allConversations);
        
        return markdown.toString();
    }
    
    /**
     * 对话统计信息内部类
     */
    private static class ConversationStat {
        String className;
        String studentName;
        String question;
        long timestamp;
        String questionType;
    }
    
    /**
     * 添加按班级分组的对话统计
     */
    private void addClassConversationBreakdown(StringBuilder markdown, 
            List<com.example.beihangagent.model.Class> teacherClasses, 
            java.util.List<ConversationStat> allConversations) {
        
        markdown.append("## 🏫 各班级对话详情\n\n");
        
        for (com.example.beihangagent.model.Class classEntity : teacherClasses) {
            java.util.List<ConversationStat> classConversations = new java.util.ArrayList<>();
            for (ConversationStat stat : allConversations) {
                if (stat.className.equals(classEntity.className)) {
                    classConversations.add(stat);
                }
            }
            
            if (!classConversations.isEmpty()) {
                markdown.append("### ").append(classEntity.className).append("\n\n");
                
                // 统计这个班级的各种数据
                java.util.Set<String> activeStudents = new java.util.HashSet<>();
                java.util.Map<String, Integer> questionTypes = new java.util.HashMap<>();
                
                for (ConversationStat stat : classConversations) {
                    activeStudents.add(stat.studentName);
                    questionTypes.put(stat.questionType, questionTypes.getOrDefault(stat.questionType, 0) + 1);
                }
                
                markdown.append("**基本统计：** ")
                    .append(classConversations.size()).append("次对话，")
                    .append(activeStudents.size()).append("名活跃学生\n\n");
                
                // 问题类型分布
                if (!questionTypes.isEmpty()) {
                    markdown.append("**问题类型分布：**\n");
                    for (java.util.Map.Entry<String, Integer> entry : questionTypes.entrySet()) {
                        markdown.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("次\n");
                    }
                    markdown.append("\n");
                }
                
                // 最活跃学生
                java.util.Map<String, Integer> studentQuestionCount = new java.util.HashMap<>();
                for (ConversationStat stat : classConversations) {
                    studentQuestionCount.put(stat.studentName, 
                        studentQuestionCount.getOrDefault(stat.studentName, 0) + 1);
                }
                
                String mostActiveStudent = "";
                int maxQuestions = 0;
                for (java.util.Map.Entry<String, Integer> entry : studentQuestionCount.entrySet()) {
                    if (entry.getValue() > maxQuestions) {
                        maxQuestions = entry.getValue();
                        mostActiveStudent = entry.getKey();
                    }
                }
                
                if (!mostActiveStudent.isEmpty()) {
                    markdown.append("**最活跃学生：** ").append(mostActiveStudent)
                        .append("（").append(maxQuestions).append("次提问）\n\n");
                }
                
                markdown.append("---\n\n");
            }
        }
    }
    
    /**
     * 添加热门话题分析
     */
    private void addPopularTopicsAnalysis(StringBuilder markdown, java.util.List<ConversationStat> allConversations) {
        markdown.append("## 🔥 热门话题分析\n\n");
        
        // 分析问题中的关键词
        java.util.Map<String, Integer> topicKeywords = new java.util.HashMap<>();
        String[] keywords = {
            "Java", "Python", "Android", "数据库", "算法", "编程", "代码", 
            "函数", "变量", "循环", "条件", "类", "对象", "方法",
            "数组", "链表", "树", "图", "排序", "查找", "设计模式",
            "网络", "HTTP", "API", "JSON", "SQL", "线程", "异步"
        };
        
        for (ConversationStat conversation : allConversations) {
            String question = conversation.question.toLowerCase();
            for (String keyword : keywords) {
                if (question.contains(keyword.toLowerCase())) {
                    topicKeywords.put(keyword, topicKeywords.getOrDefault(keyword, 0) + 1);
                }
            }
        }
        
        if (!topicKeywords.isEmpty()) {
            markdown.append("| 关键词 | 提及次数 | 热度 |\n");
            markdown.append("|--------|----------|------|\n");
            
            topicKeywords.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    String heatLevel = entry.getValue() >= 10 ? "🔥🔥🔥" : 
                                    entry.getValue() >= 5 ? "🔥🔥" : "🔥";
                    markdown.append("| ").append(entry.getKey()).append(" | ")
                        .append(entry.getValue()).append(" | ").append(heatLevel).append(" |\n");
                });
            markdown.append("\n");
        } else {
            markdown.append("暂无明显的热门话题关键词。\n\n");
        }
    }
    
    /**
     * 添加最近活跃对话
     */
    private void addRecentActiveConversations(StringBuilder markdown, java.util.List<ConversationStat> allConversations) {
        markdown.append("## ⏰ 最近活跃对话\n\n");
        
        // 获取最近7天的对话
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        java.util.List<ConversationStat> recentConversations = new java.util.ArrayList<>();
        
        for (ConversationStat conversation : allConversations) {
            if (conversation.timestamp > sevenDaysAgo) {
                recentConversations.add(conversation);
            }
        }
        
        if (recentConversations.isEmpty()) {
            markdown.append("最近7天无活跃对话。\n\n");
            return;
        }
        
        // 按时间倒序排列
        recentConversations.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        
        markdown.append("**最近7天共 ").append(recentConversations.size()).append(" 次对话**\n\n");
        markdown.append("| 时间 | 班级 | 学生 | 问题摘要 | 类型 |\n");
        markdown.append("|------|------|------|----------|------|\n");
        
        // 只显示前15个最新的对话
        int count = 0;
        for (ConversationStat conversation : recentConversations) {
            if (count >= 15) break;
            
            String time = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(conversation.timestamp));
            String questionSummary = conversation.question.length() > 30 ? 
                conversation.question.substring(0, 30) + "..." : conversation.question;
            
            markdown.append("| ").append(time).append(" | ")
                .append(conversation.className).append(" | ")
                .append(conversation.studentName).append(" | ")
                .append(questionSummary).append(" | ")
                .append(conversation.questionType).append(" |\n");
            count++;
        }
        
        markdown.append("\n");
    }
    
    /**
     * 添加问题类型趋势分析
     */
    private void addQuestionTypeTrends(StringBuilder markdown, java.util.List<ConversationStat> allConversations) {
        markdown.append("## 📈 问题类型趋势\n\n");
        
        // 统计各类型问题的数量
        java.util.Map<String, Integer> typeCount = new java.util.HashMap<>();
        for (ConversationStat conversation : allConversations) {
            typeCount.put(conversation.questionType, typeCount.getOrDefault(conversation.questionType, 0) + 1);
        }
        
        if (typeCount.isEmpty()) {
            markdown.append("暂无问题类型数据。\n\n");
            return;
        }
        
        int totalQuestions = typeCount.values().stream().mapToInt(Integer::intValue).sum();
        
        markdown.append("| 问题类型 | 数量 | 占比 | 趋势 |\n");
        markdown.append("|----------|------|------|------|\n");
        
        // 按数量排序
        typeCount.entrySet().stream()
            .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                double percentage = (double)entry.getValue() / totalQuestions * 100;
                String trend = percentage > 30 ? "📈 主要" : 
                             percentage > 15 ? "📊 常见" : "📉 较少";
                
                markdown.append("| ").append(entry.getKey()).append(" | ")
                    .append(entry.getValue()).append(" | ")
                    .append(String.format("%.1f%%", percentage)).append(" | ")
                    .append(trend).append(" |\n");
            });
        
        markdown.append("\n### 🎯 教学建议\n\n");
        
        // 基于数据给出教学建议
        String topType = typeCount.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("未知");
        
        switch (topType) {
            case "概念理解":
                markdown.append("1. **加强理论讲解** - 学生对概念理解需求较多，可增加理论课时间\n");
                break;
            case "操作指导":
                markdown.append("1. **增加实践环节** - 学生需要更多操作指导，建议增加实验课或演示\n");
                break;
            case "原理解释":
                markdown.append("1. **深入原理教学** - 学生渴望理解底层原理，可安排专题讲座\n");
                break;
            case "编程相关":
                markdown.append("1. **强化编程训练** - 编程问题较多，建议增加代码练习和项目实践\n");
                break;
            case "问题求助":
                markdown.append("1. **设立答疑时间** - 学生遇到问题较多，建议定期设立答疑时间\n");
                break;
        }
        
        markdown.append("2. **促进互动** - 鼓励更多学生参与，提高课堂活跃度\n");
        markdown.append("3. **个性化指导** - 针对不同类型问题，采用差异化教学策略\n\n");
        
        markdown.append("---\n");
        markdown.append("*北航智教助手对话摘要自动生成*\n");
    }
    
    /**
     * 简单的问题分类
     */
    private String categorizeQuestion(String question) {
        String lowerCase = question.toLowerCase();
        if (lowerCase.contains("是什么") || lowerCase.contains("定义") || lowerCase.contains("概念")) {
            return "概念理解";
        } else if (lowerCase.contains("怎么") || lowerCase.contains("如何") || lowerCase.contains("步骤")) {
            return "操作指导";
        } else if (lowerCase.contains("为什么") || lowerCase.contains("原因") || lowerCase.contains("原理")) {
            return "原理解释";
        } else if (lowerCase.contains("代码") || lowerCase.contains("程序") || lowerCase.contains("编程")) {
            return "编程相关";
        } else if (lowerCase.contains("错误") || lowerCase.contains("问题") || lowerCase.contains("bug")) {
            return "问题求助";
        } else {
            return "其他类型";
        }
    }

    private void showLearningReport() {
        // 检查用户角色，教师不能访问学习报告
        if (currentUser != null && currentUser.role == 1) {
            new AlertDialog.Builder(requireContext())
                .setTitle("📊 学习报告")
                .setMessage("学习报告功能仅限学生使用。\n\n教师可以在“教学统计”页面查看所有学生的学习数据和趣热点分析。")
                .setPositiveButton("知道了", null)
                .show();
            return;
        }
        
        // 实时统计准确的对话数量（与个人中心保持一致）
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("准备学习报告")
            .setMessage("正在统计您的学习数据，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程中执行数据库操作
        executor.execute(() -> {
            try {
                List<com.example.beihangagent.model.ChatMessage> userMessages = chatMessageDao.getByUser(currentUserId);
                int userQuestionCount = 0;
                for (com.example.beihangagent.model.ChatMessage message : userMessages) {
                    if ("user".equals(message.role)) {
                        userQuestionCount++;
                    }
                }
                final int totalConversations = userQuestionCount; // 声明为final以便在lambda中使用
                
                // 同步更新SharedPreferences中的缓存计数
                SharedPreferences personalPrefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
                personalPrefs.edit().putInt("total_conversations", totalConversations).apply();
                
                // 回到主线程继续处理
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    continueShowLearningReport(totalConversations, personalPrefs, userMessages);
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "统计学习数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 强制重新生成报告，忽略缓存
     */
    private void forceRegenerateReport() {
        // 检查用户角色，教师不能访问学习报告
        if (currentUser != null && currentUser.role == 1) {
            new AlertDialog.Builder(requireContext())
                .setTitle("📊 学习报告")
                .setMessage("学习报告功能仅限学生使用。\\n\\n教师可以在\"教学统计\"页面查看所有学生的学习数据和趣热点分析。")
                .setPositiveButton("知道了", null)
                .show();
            return;
        }
        
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("重新生成报告")
            .setMessage("正在重新分析您的最新学习数据，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 在后台线程中执行数据库操作
        executor.execute(() -> {
            try {
                List<com.example.beihangagent.model.ChatMessage> userMessages = chatMessageDao.getByUser(currentUserId);
                int userQuestionCount = 0;
                for (com.example.beihangagent.model.ChatMessage message : userMessages) {
                    if ("user".equals(message.role)) {
                        userQuestionCount++;
                    }
                }
                final int totalConversations = userQuestionCount;
                
                // 同步更新SharedPreferences中的缓存计数
                SharedPreferences personalPrefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
                personalPrefs.edit().putInt("total_conversations", totalConversations).apply();
                
                // 回到主线程继续处理，强制重新生成（不检查缓存）
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    forceGenerateNewReport(totalConversations, personalPrefs, userMessages);
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "重新统计学习数据失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 强制生成新报告，跳过缓存检查
     */
    private void forceGenerateNewReport(int totalConversations, SharedPreferences personalPrefs, List<com.example.beihangagent.model.ChatMessage> userMessages) {
        if (totalConversations < 5) {
            new AlertDialog.Builder(requireContext())
                .setTitle("📊 学习报告")
                .setMessage("您的对话记录较少（当前" + totalConversations + "次，需至少5次），无法生成有效的个性化学习报告。\n\n💬 请多与AI助手互动后再来查看学习报告。\n\n🌟 建议：尝试询问不同类型的问题（概念、实践、理论）")
                .setPositiveButton("知道了", null)
                .setNegativeButton("去聊天", (dialog, which) -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).switchToChat();
                    }
                })
                .show();
            return;
        }
        
        // 强制重新生成，忽略缓存
        String cachedReportKey = "learning_report_" + currentUserId;
        String cachedTimeKey = "learning_report_time_" + currentUserId;
        
        long currentTime = System.currentTimeMillis();
        long sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L;
        
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("重新生成学习报告")
            .setMessage("正在基于最新数据重新分析，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 异步收集历史对话并生成报告
        executor.execute(() -> {
            try {
                // 收集最近7天的历史对话数据
                StringBuilder conversationHistory = new StringBuilder();
                
                // 只统计最近7天的对话
                long sevenDaysAgo = currentTime - sevenDaysInMillis;
                int recentQuestionCount = 0;
                
                for (com.example.beihangagent.model.ChatMessage message : userMessages) {
                    if ("user".equals(message.role) && message.timestamp > sevenDaysAgo) {
                        recentQuestionCount++;
                        conversationHistory.append("Q").append(recentQuestionCount).append(": ").append(message.content).append("\n");
                    }
                }
                
                // 构建学习报告提示词
                String reportPrompt = buildLearningReportPrompt(conversationHistory.toString(), totalConversations, personalPrefs);
                
                // 调用AI API生成报告并缓存
                generateLearningReportWithCache(reportPrompt, loadingDialog, personalPrefs, cachedReportKey, cachedTimeKey);
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "重新生成报告失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void continueShowLearningReport(int totalConversations, SharedPreferences personalPrefs, List<com.example.beihangagent.model.ChatMessage> userMessages) {
        
        if (totalConversations < 5) {
            new AlertDialog.Builder(requireContext())
                .setTitle("📊 学习报告")
                .setMessage("您的对话记录较少（当前" + totalConversations + "次，需至少5次），无法生成有效的个性化学习报告。\n\n💬 请多与AI助手互动后再来查看学习报告。\n\n🌟 建议：尝试询问不同类型的问题（概念、实践、理论）")
                .setPositiveButton("知道了", null)
                .setNegativeButton("去聊天", (dialog, which) -> {
                    // 跳转到聊天页面
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).switchToChat();
                    }
                })
                .show();
            return;
        }
        
        // 检查是否存在有效的缓存报告（针对当前用户）
        String cachedReportKey = "learning_report_" + currentUserId;
        String cachedTimeKey = "learning_report_time_" + currentUserId;
        
        String cachedReport = personalPrefs.getString(cachedReportKey, "");
        long cachedTime = personalPrefs.getLong(cachedTimeKey, 0);
        long currentTime = System.currentTimeMillis();
        long sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L;
        
        // 如果有缓存且在7天内，直接显示
        if (!cachedReport.isEmpty() && (currentTime - cachedTime) < sevenDaysInMillis) {
            showLearningReportDialog(cachedReport, true, cachedTime);
            return;
        }
        
        // 显示加载提示
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
            .setTitle("生成学习报告")
            .setMessage("正在分析您的学习数据，请稍候...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        // 异步收集历史对话并生成报告
        executor.execute(() -> {
            try {
                // 收集最近7天的历史对话数据
                StringBuilder conversationHistory = new StringBuilder();
                // 重用已经获取的用户消息，避免重复数据库调用
                
                // 只统计最近7天的对话
                long sevenDaysAgo = currentTime - sevenDaysInMillis;
                int recentQuestionCount = 0;
                
                for (com.example.beihangagent.model.ChatMessage message : userMessages) {
                    if ("user".equals(message.role) && message.timestamp > sevenDaysAgo) {
                        recentQuestionCount++;
                        conversationHistory.append("Q").append(recentQuestionCount).append(": ").append(message.content).append("\n");
                    }
                }
                
                // 构建学习报告提示词
                String reportPrompt = buildLearningReportPrompt(conversationHistory.toString(), totalConversations, personalPrefs);
                
                // 调用AI API生成报告并缓存
                generateLearningReportWithCache(reportPrompt, loadingDialog, personalPrefs, cachedReportKey, cachedTimeKey);
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "生成报告失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private String buildLearningReportPrompt(String conversationHistory, int totalConversations, SharedPreferences prefs) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请根据以下学习数据生成一份详细的个性化学习报告：\n\n");
        prompt.append("=== 基本信息 ===\n");
        prompt.append("总对话次数：").append(totalConversations).append("次\n");
        
        String questionTypes = prefs.getString("question_types", "");
        if (!questionTypes.isEmpty()) {
            prompt.append("问题类型偏好：").append(questionTypes).append("\n");
        }
        
        String lastQuestionType = prefs.getString("last_question_type", "");
        if (!lastQuestionType.isEmpty()) {
            prompt.append("最近问题类型：").append(lastQuestionType).append("\n");
        }
        
        prompt.append("\n=== 历史对话记录 ===\n");
        prompt.append(conversationHistory);
        
        prompt.append("\n=== 报告要求 ===\n");
        prompt.append("请生成包含以下内容的学习报告：\n");
        prompt.append("1. 学习概况总结\n");
        prompt.append("2. 问题类型分析（概念型、实践型、理论型等）\n");
        prompt.append("3. 知识点掌握情况\n");
        prompt.append("4. 学习风格特征\n");
        prompt.append("5. 改进建议\n");
        prompt.append("6. 推荐学习重点\n\n");
        prompt.append("请用结构化、易读的格式输出，使用markdown格式。");
        
        return prompt.toString();
    }
    
    private void generateLearningReportWithCache(String prompt, AlertDialog loadingDialog, SharedPreferences personalPrefs, String cachedReportKey, String cachedTimeKey) {
        new Thread(() -> {
            try {
                String apiResponse = callAIForLearningReport(prompt);
                
                // 缓存报告内容和生成时间
                personalPrefs.edit()
                    .putString(cachedReportKey, apiResponse)
                    .putLong(cachedTimeKey, System.currentTimeMillis())
                    .apply();
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showLearningReportDialog(apiResponse, false, System.currentTimeMillis());
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "网络错误，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void generateLearningReport(String prompt, AlertDialog loadingDialog) {
        new Thread(() -> {
            try {
                String apiResponse = callAIForLearningReport(prompt);
                
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showLearningReportDialog(apiResponse, false, System.currentTimeMillis());
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "网络错误，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private String callAIForLearningReport(String prompt) {
        // 暂时返回基于真实用户数据的报告，后续可接入真实API
        SharedPreferences personalPrefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
        int totalConversations = personalPrefs.getInt("total_conversations", 0);
        String lastQuestionType = personalPrefs.getString("last_question_type", "");
        
        // 计算学习天数（基于首次使用时间）
        long firstUseTime = personalPrefs.getLong("first_use_time", System.currentTimeMillis());
        long daysSinceFirstUse = (System.currentTimeMillis() - firstUseTime) / (1000 * 60 * 60 * 24);
        if (daysSinceFirstUse == 0) daysSinceFirstUse = 1; // 至少1天
        
        // 根据真实数据生成报告
        return generateReportBasedOnUserData(totalConversations, lastQuestionType, (int)daysSinceFirstUse);
    }
    
    private String generateReportBasedOnUserData(int totalConversations, String lastQuestionType, int learningDays) {
        StringBuilder report = new StringBuilder();
        
        report.append("# 📊 个性化学习报告\n\n");
        
        report.append("## 🎯 学习概览\n");
        report.append("- **学习周期**: ").append(learningDays).append("天\n");
        report.append("- **互动次数**: ").append(totalConversations).append("次\n");
        
        // 计算平均每天互动次数
        double avgPerDay = totalConversations > 0 ? (double)totalConversations / learningDays : 0;
        report.append("- **平均每天互动**: ").append(String.format("%.1f", avgPerDay)).append("次\n");
        
        // 根据互动频率评估活跃度
        String activeness;
        if (avgPerDay >= 3) {
            activeness = "⭐⭐⭐⭐⭐";
        } else if (avgPerDay >= 2) {
            activeness = "⭐⭐⭐⭐☆";
        } else if (avgPerDay >= 1) {
            activeness = "⭐⭐⭐☆☆";
        } else if (avgPerDay >= 0.5) {
            activeness = "⭐⭐☆☆☆";
        } else {
            activeness = "⭐☆☆☆☆";
        }
        report.append("- **学习活跃度**: ").append(activeness).append("\n\n");
        
        // 学习趋势分析
        report.append("## 📈 学习趋势分析\n");
        if (totalConversations >= 20) {
            report.append("🎉 **优秀表现**：您已经进行了").append(totalConversations).append("次学习互动，学习习惯良好！\n\n");
        } else if (totalConversations >= 10) {
            report.append("👍 **稳步进步**：您已完成").append(totalConversations).append("次互动，继续保持！\n\n");
        } else if (totalConversations >= 5) {
            report.append("🌱 **起步阶段**：您已开始").append(totalConversations).append("次学习对话，建议增加互动频率。\n\n");
        } else {
            report.append("📚 **初学阶段**：您刚开始使用学习助手，建议多多互动。\n\n");
        }
        
        // 问题类型分析
        report.append("## 🧠 学习偏好分析\n");
        if (!lastQuestionType.isEmpty()) {
            switch (lastQuestionType) {
                case "概念型":
                    report.append("您最近偏向于**概念理解型**学习，善于从理论角度思考问题。\n");
                    report.append("💡 **建议**: 可以增加一些实践练习来巩固理论知识。\n\n");
                    break;
                case "实践型":
                    report.append("您最近专注于**实践操作型**学习，注重动手能力。\n");
                    report.append("💡 **建议**: 在实践的同时，也要理解背后的原理。\n\n");
                    break;
                case "理论型":
                    report.append("您最近深入**理论研究型**学习，追求深度理解。\n");
                    report.append("💡 **建议**: 尝试将理论应用到实际项目中。\n\n");
                    break;
                default:
                    report.append("您的学习类型比较均衡，这是很好的学习方式。\n\n");
            }
        } else {
            report.append("暂未收集到足够的问题类型数据，请继续使用以便生成更精准的分析。\n\n");
        }
        
        // 个性化建议
        report.append("## 💡 个性化建议\n");
        report.append("### 🎯 近期目标\n");
        
        if (totalConversations < 10) {
            report.append("1. **增加互动频率** - 建议每天至少提问1-2次\n");
            report.append("2. **多样化问题** - 尝试不同类型的问题（概念、实践、理论）\n");
            report.append("3. **持续学习** - 养成定期使用助手的习惯\n\n");
        } else {
            report.append("1. **深入专业领域** - 针对您感兴趣的技术方向深入学习\n");
            report.append("2. **项目实践** - 将学到的知识应用到实际项目中\n");
            report.append("3. **知识总结** - 定期回顾和整理学习内容\n\n");
        }
        
        // 学习成就
        report.append("## 🏆 学习成就\n");
        report.append("🎯 **坚持学习**: 已使用").append(learningDays).append("天\n");
        report.append("💬 **累计互动**: ").append(totalConversations).append("次对话\n");
        
        if (totalConversations >= 20) {
            report.append("🌟 **学习达人**: 互动次数超过20次，您是真正的学习爱好者！\n");
        } else if (totalConversations >= 10) {
            report.append("📖 **勤奋学者**: 互动次数超过10次，学习态度值得称赞！\n");
        }
        
        if (avgPerDay >= 2) {
            report.append("⚡ **高频学习**: 平均每天互动超过2次，学习非常积极！\n");
        }
        
        report.append("\n---\n");
        report.append("*✨ 基于您的真实学习数据生成，继续保持学习热情！*");
        
        return report.toString();
    }
    
    private void showLearningReportDialog(String reportContent, boolean isCached, long generateTime) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("📊 学习报告分析");
        
        // 创建美观的报告界面
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(requireContext());
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 24, 32, 24);
        
        // 使用Markdown渲染的TextView
        android.widget.TextView textView = new android.widget.TextView(requireContext());
        textView.setText(android.text.Html.fromHtml(convertMarkdownToHtml(reportContent), android.text.Html.FROM_HTML_MODE_COMPACT));
        textView.setTextSize(15);
        textView.setLineSpacing(8f, 1.2f);
        textView.setTextColor(0xFF333333);
        
        // 添加滚动视图
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.addView(textView);
        
        mainLayout.addView(scrollView);
        
        // 添加装饰性分割线
        android.view.View divider = new android.view.View(requireContext());
        android.widget.LinearLayout.LayoutParams dividerParams = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dividerParams.setMargins(0, 16, 0, 16);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(0xFFE0E0E0);
        mainLayout.addView(divider);
        
        // 添加统计信息
        android.widget.TextView statsView = new android.widget.TextView(requireContext());
        String statusText;
        if (isCached) {
            long daysSinceGenerate = (System.currentTimeMillis() - generateTime) / (1000 * 60 * 60 * 24);
            statusText = "📋 缓存报告（" + daysSinceGenerate + "天前生成）";
        } else {
            statusText = "🆕 刚刚生成: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date(generateTime));
        }
        statsView.setText(statusText);
        statsView.setTextSize(12);
        statsView.setTextColor(0xFF666666);
        statsView.setGravity(android.view.Gravity.CENTER);
        mainLayout.addView(statsView);
        
        builder.setView(mainLayout);
        builder.setPositiveButton("🔄 重新生成报告", (dialog, which) -> {
            // 关闭当前弹窗后，强制重新生成报告（忽略缓存）
            dialog.dismiss();
            forceRegenerateReport();
        });
        builder.setNegativeButton("📤 分享", (dialog, which) -> shareReport(reportContent));
        builder.setNeutralButton("关闭", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 设置对话框大小和样式
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.92);
            params.height = (int)(getResources().getDisplayMetrics().heightPixels * 0.75);
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);
        }
    }
    
    /**
     * 将简单的Markdown转换为HTML，用于TextView显示
     */
    private String convertMarkdownToHtml(String markdown) {
        String html = markdown;
        
        // 处理标题 - 使用multiline模式处理换行开头的标题
        html = html.replaceAll("(?m)^# (.*?)$", "<h1 style='color:#1976D2; font-size:20sp; margin:12dp 0; font-weight:bold;'>$1</h1>");
        html = html.replaceAll("(?m)^## (.*?)$", "<h2 style='color:#1976D2; font-size:18sp; margin:10dp 0; font-weight:bold;'>$1</h2>");
        html = html.replaceAll("(?m)^### (.*?)$", "<h3 style='color:#1976D2; font-size:16sp; margin:8dp 0; font-weight:bold;'>$1</h3>");
        
        // 处理粗体
        html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b style='color:#333333;'>$1</b>");
        
        // 处理列表
        html = html.replaceAll("(?m)^[0-9]+\\. (.*?)$", "<div style='margin:4dp 0; padding-left:16dp;'>• $1</div>");
        html = html.replaceAll("(?m)^- (.*?)$", "<div style='margin:4dp 0; padding-left:16dp;'>• $1</div>");
        
        // 处理表情符号增强
        html = html.replaceAll("📊", "<font color='#FF9800'>📊</font>");
        html = html.replaceAll("💡", "<font color='#FFC107'>💡</font>");
        html = html.replaceAll("🚀", "<font color='#4CAF50'>🚀</font>");
        html = html.replaceAll("⭐", "<font color='#FFD700'>⭐</font>");
        html = html.replaceAll("🏆", "<font color='#FF6F00'>🏆</font>");
        html = html.replaceAll("📚", "<font color='#8BC34A'>📚</font>");
        html = html.replaceAll("📝", "<font color='#607D8B'>📝</font>");
        
        // 处理分割线
        html = html.replaceAll("(?m)^---$", "<hr style='border:1px solid #E0E0E0; margin:16dp 0;'/>");
        
        // 换行处理
        html = html.replaceAll("\n", "<br/>");
        
        return html;
    }
    
    private void shareReport(String reportContent) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "我的学习报告 - 北航智教助手");
        shareIntent.putExtra(Intent.EXTRA_TEXT, reportContent);
        startActivity(Intent.createChooser(shareIntent, "分享学习报告"));
    }

    private void showHelp() {
        // 打开帮助中心Activity
        Intent intent = new Intent(requireContext(), com.example.beihangagent.view.HelpActivity.class);
        startActivity(intent);
    }

    private void showAbout() {
        // Placeholder for about dialog
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("关于北航智教助手")
            .setMessage("版本：1.0\n开发团队：北航智教助手团队\n\n这是一款专为北航学生和教师设计的智能学习助手应用。")
            .setPositiveButton("确定", null)
            .show();
    }

    private void logout() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                prefs.edit().clear().apply();
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void changePassword() {
        BottomSheetProfileSettingsBinding settingsBinding = BottomSheetProfileSettingsBinding.inflate(getLayoutInflater());
        BottomSheetDialog settingsDialog = new BottomSheetDialog(requireContext());
        settingsDialog.setContentView(settingsBinding.getRoot());
        
        String currentPwd = settingsBinding.etCurrentPassword.getText().toString().trim();
        String newPwd = settingsBinding.etNewPassword.getText().toString().trim();
        String confirmPwd = settingsBinding.etConfirmPassword.getText().toString().trim();

        if (!newPwd.equals(confirmPwd)) {
            Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPwd.length() < 6) {
            Toast.makeText(requireContext(), "新密码至少6位", Toast.LENGTH_SHORT).show();
            return;
        }

        settingsBinding.btnChangePassword.setEnabled(false);
        executor.execute(() -> {
            if (currentUser != null && currentUser.password.equals(currentPwd)) {
                currentUser.password = newPwd;
                userDao.updateUser(currentUser);
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "密码修改成功", Toast.LENGTH_SHORT).show();
                    settingsBinding.etCurrentPassword.setText("");
                    settingsBinding.etNewPassword.setText("");
                    settingsBinding.etConfirmPassword.setText("");
                    settingsBinding.btnChangePassword.setEnabled(true);
                    settingsDialog.dismiss();
                });
            } else {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "当前密码错误", Toast.LENGTH_SHORT).show();
                    settingsBinding.btnChangePassword.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void initObservers() {
        // No observers needed
    }
    
    /**
     * 保存头像到本地文件
     */
    private String saveAvatarToFile(Bitmap bitmap, String filename) {
        try {
            File avatarDir = new File(requireContext().getFilesDir(), "avatars");
            Log.d("ProfileFragment", "Avatar directory: " + avatarDir.getAbsolutePath());
            
            if (!avatarDir.exists()) {
                boolean created = avatarDir.mkdirs();
                Log.d("ProfileFragment", "Avatar directory created: " + created);
                if (!created) {
                    Log.e("ProfileFragment", "Failed to create avatar directory");
                    return null;
                }
            }
            
            // 检查目录权限
            Log.d("ProfileFragment", "Directory exists: " + avatarDir.exists() + 
                  ", canWrite: " + avatarDir.canWrite() + 
                  ", canRead: " + avatarDir.canRead());
            
            File avatarFile = new File(avatarDir, filename);
            Log.d("ProfileFragment", "Saving avatar to: " + avatarFile.getAbsolutePath());
            
            FileOutputStream fos = new FileOutputStream(avatarFile);
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            
            Log.d("ProfileFragment", "Bitmap compression result: " + compressed);
            
            // 验证文件保存
            if (avatarFile.exists()) {
                long fileSize = avatarFile.length();
                Log.d("ProfileFragment", "Avatar saved successfully, file size: " + fileSize + " bytes");
                if (fileSize > 0) {
                    return avatarFile.getAbsolutePath();
                } else {
                    Log.e("ProfileFragment", "Avatar file is empty");
                    return null;
                }
            } else {
                Log.e("ProfileFragment", "Avatar file was not created");
                return null;
            }
        } catch (IOException e) {
            Log.e("ProfileFragment", "Failed to save avatar", e);
            return null;
        } catch (SecurityException e) {
            Log.e("ProfileFragment", "Security exception saving avatar", e);
            return null;
        } catch (Exception e) {
            Log.e("ProfileFragment", "Unexpected error saving avatar", e);
            return null;
        }
    }
    
    /**
     * 从本地文件加载头像
     */
    private Bitmap loadAvatarFromFile(String path) {
        try {
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                Log.d("ProfileFragment", "Loading avatar from: " + path);
                Log.d("ProfileFragment", "File exists: " + file.exists() + ", canRead: " + file.canRead() + 
                      ", size: " + (file.exists() ? file.length() : "N/A") + " bytes");
                
                if (file.exists()) {
                    // 如果是.png文件，直接加载bitmap
                    if (path.endsWith(".png")) {
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        if (bitmap != null) {
                            Log.d("ProfileFragment", "Successfully loaded PNG bitmap: " + 
                                  bitmap.getWidth() + "x" + bitmap.getHeight());
                        } else {
                            Log.e("ProfileFragment", "Failed to decode PNG file");
                        }
                        return bitmap;
                    }
                    // 如果是.txt文件，恢复fallback头像
                    else if (path.endsWith(".txt")) {
                        String data = readTextFromFile(file);
                        Log.d("ProfileFragment", "Fallback avatar data: " + data);
                        if (data != null && data.contains(",")) {
                            String[] parts = data.split(",");
                            int colorIndex = Integer.parseInt(parts[0]);
                            int iconIndex = Integer.parseInt(parts[1]);
                            
                            // 重新应用fallback头像样式
                            restoreFallbackAvatar(colorIndex, iconIndex);
                            return null; // 返回null表示已直接设置UI，无需再次设置
                        }
                    }
                } else {
                    Log.w("ProfileFragment", "Avatar file does not exist: " + path);
                }
            } else {
                Log.w("ProfileFragment", "Avatar path is null or empty");
            }
        } catch (Exception e) {
            Log.e("ProfileFragment", "Failed to load avatar from file: " + path, e);
        }
        return null;
    }
    
    /**
     * 从文件读取文本内容
     */
    private String readTextFromFile(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data);
        } catch (Exception e) {
            Log.e("ProfileFragment", "Failed to read text from file", e);
            return null;
        }
    }
    
    /**
     * 恢复fallback头像样式
     */
    private void restoreFallbackAvatar(int colorIndex, int iconIndex) {
        Log.d("ProfileFragment", "Restoring fallback avatar: color=" + colorIndex + ", icon=" + iconIndex);
        
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (binding != null && binding.ivUserAvatar != null) {
                    int backgroundColor = avatarColors[colorIndex % avatarColors.length];
                    int iconRes = avatarIcons[iconIndex % avatarIcons.length];
                    
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.OVAL);
                    drawable.setColor(backgroundColor);
                    
                    binding.ivUserAvatar.setBackground(drawable);
                    binding.ivUserAvatar.setImageResource(iconRes);
                    binding.ivUserAvatar.setColorFilter(Color.WHITE);
                    binding.ivUserAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                    
                    Log.d("ProfileFragment", "Restored fallback avatar successfully on main thread");
                } else {
                    Log.e("ProfileFragment", "binding or ivUserAvatar is null when restoring fallback!");
                }
            });
        } else {
            Log.e("ProfileFragment", "Activity is null when trying to restore fallback avatar");
        }
    }
    
    /**
     * 更新用户头像信息到数据库
     */
    private void updateUserAvatar(String avatarPath, Integer avatarType) {
        if (currentUser != null) {
            currentUser.avatarPath = avatarPath;
            currentUser.avatarType = avatarType;
            
            // 异步更新数据库
            new Thread(() -> {
                try {
                    AppDatabase.getDatabase(requireContext()).userDao().updateUser(currentUser);
                    Log.d("ProfileFragment", "User avatar updated in database");
                } catch (Exception e) {
                    Log.e("ProfileFragment", "Failed to update user avatar", e);
                }
            }).start();
        }
    }

    /**
     * 计算学生的活跃天数
     */
    private int calculateActiveDaysForStudent(int userId) {
        try {
            List<com.example.beihangagent.model.ChatMessage> messages = chatMessageDao.getByUser(userId);
            if (messages == null || messages.isEmpty()) {
                return 0;
            }
            
            // 收集所有消息的日期（去重）
            java.util.Set<String> activeDates = new java.util.HashSet<>();
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            
            for (com.example.beihangagent.model.ChatMessage message : messages) {
                if ("user".equals(message.role)) { // 只统计用户发送的消息
                    java.util.Date date = new java.util.Date(message.timestamp);
                    String dateStr = dateFormat.format(date);
                    activeDates.add(dateStr);
                }
            }
            
            Log.d("ProfileFragment", "Student " + userId + " active dates: " + activeDates.size());
            return activeDates.size();
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error calculating active days for student: " + userId, e);
            return 0;
        }
    }

    /**
     * 计算教师的活跃天数（基于教学互动）
     */
    private int calculateActiveDaysForTeacher(int teacherId) {
        try {
            // 获取教师的所有班级
            List<com.example.beihangagent.model.Class> teacherClasses = classDao.getClassesByTeacherSync(teacherId);
            if (teacherClasses == null || teacherClasses.isEmpty()) {
                // 如果没有班级，基于自己的消息计算
                return calculateActiveDaysForStudent(teacherId);
            }
            
            java.util.Set<String> activeDates = new java.util.HashSet<>();
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            
            // 统计所有班级学生的活跃日期
            for (com.example.beihangagent.model.Class classEntity : teacherClasses) {
                List<com.example.beihangagent.model.ClassMember> members = classDao.getClassMembersSync(classEntity.classId);
                if (members != null) {
                    for (com.example.beihangagent.model.ClassMember member : members) {
                        List<com.example.beihangagent.model.ChatMessage> studentMessages = chatMessageDao.getByUser(member.studentId);
                        if (studentMessages != null) {
                            for (com.example.beihangagent.model.ChatMessage message : studentMessages) {
                                if ("user".equals(message.role)) {
                                    java.util.Date date = new java.util.Date(message.timestamp);
                                    String dateStr = dateFormat.format(date);
                                    activeDates.add(dateStr);
                                }
                            }
                        }
                    }
                }
            }
            
            // 也包括教师自己的活跃日期
            List<com.example.beihangagent.model.ChatMessage> teacherMessages = chatMessageDao.getByUser(teacherId);
            if (teacherMessages != null) {
                for (com.example.beihangagent.model.ChatMessage message : teacherMessages) {
                    if ("user".equals(message.role)) {
                        java.util.Date date = new java.util.Date(message.timestamp);
                        String dateStr = dateFormat.format(date);
                        activeDates.add(dateStr);
                    }
                }
            }
            
            Log.d("ProfileFragment", "Teacher " + teacherId + " active dates: " + activeDates.size());
            return activeDates.size();
        } catch (Exception e) {
            Log.e("ProfileFragment", "Error calculating active days for teacher: " + teacherId, e);
            return calculateActiveDaysForStudent(teacherId); // 降级处理
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
