package com.example.beihangagent;

import android.content.Intent;
import android.content.SharedPreferences;
import androidx.fragment.app.Fragment;
import com.example.beihangagent.databinding.ActivityMainBinding;
import com.example.beihangagent.view.LoginActivity;
import com.example.beihangagent.view.base.BaseActivity;
import com.example.beihangagent.view.fragment.AnalysisFragment;
import com.example.beihangagent.view.fragment.ChatFragment;
import com.example.beihangagent.view.fragment.ProfileFragment;
import com.example.beihangagent.util.DataImporter;

public class MainActivity extends BaseActivity<ActivityMainBinding> {

    private SharedPreferences prefs;

    @Override
    protected ActivityMainBinding getViewBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initViews() {
        prefs = getSharedPreferences("user_session", MODE_PRIVATE);
        int role = prefs.getInt("role", 0); // 0: Student, 1: Teacher

        // 每次启动都检查并导入测试数据（如果需要的话）
        DataImporter importer = new DataImporter(this);
        importer.importTestData();

        if (role == 1) {
            // Teacher: Hide Chat, Show Analysis
            binding.bottomNavigation.getMenu().findItem(R.id.nav_chat).setVisible(false);
            binding.bottomNavigation.getMenu().findItem(R.id.nav_analysis).setVisible(true);
            
            // Reorder for Teacher: Analysis (Left), Forum (Middle), Profile (Right)
            // Since we can't easily reorder menu items programmatically without clearing and re-adding,
            // we will rely on the menu xml order, but we need to make sure the visibility is correct.
            // The menu XML order is Chat, Forum, Analysis, Profile.
            // If we hide Chat, the order becomes Forum, Analysis, Profile.
            // The user wants Analysis, Forum, Profile.
            // We can create a separate menu for teachers or just accept the order Forum, Analysis, Profile for now
            // OR we can swap the order in XML and hide differently.
            // Let's try to use a different menu for teacher if possible, or just swap items.
            // Simpler approach: Create a teacher specific menu resource.
            binding.bottomNavigation.getMenu().clear();
            binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu_teacher);
            
            loadFragment(new AnalysisFragment());
        } else {
            // Student: Show Chat, Hide Analysis
            // Default menu is fine, just hide Analysis
            binding.bottomNavigation.getMenu().findItem(R.id.nav_analysis).setVisible(false);
            loadFragment(new ChatFragment());
        }
        // Ensure Forum is visible for both
        binding.bottomNavigation.getMenu().findItem(R.id.nav_forum).setVisible(true);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_chat) {
                loadFragment(new ChatFragment());
                return true;
            } else if (itemId == R.id.nav_analysis) {
                loadFragment(new AnalysisFragment());
                return true;
            } else if (itemId == R.id.nav_forum) {
                loadFragment(new com.example.beihangagent.view.fragment.ClassSelectionFragment());
                return true;
            } else if (itemId == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }
    
    /**
     * 切换到聊天页面（仅对学生有效）
     */
    public void switchToChat() {
        int role = prefs.getInt("role", 0);
        if (role == 0) { // 学生角色
            binding.bottomNavigation.setSelectedItemId(R.id.nav_chat);
            loadFragment(new ChatFragment());
        }
    }
}