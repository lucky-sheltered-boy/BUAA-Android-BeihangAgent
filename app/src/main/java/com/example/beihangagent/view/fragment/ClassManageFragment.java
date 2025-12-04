package com.example.beihangagent.view.fragment;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentClassManageBinding;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.model.ClassDao;
import com.example.beihangagent.model.ClassMember;
import com.example.beihangagent.model.QuestionStat;
import com.example.beihangagent.model.QuestionStatDao;
import com.example.beihangagent.model.User;
import com.example.beihangagent.model.UserDao;
import com.example.beihangagent.view.adapter.ClassAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.view.fragment.ClassSelectionFragment;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClassManageFragment extends BaseFragment<FragmentClassManageBinding> {

    private ClassDao classDao;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int currentUserId;
    private int userRole; // 0=Student, 1=Teacher
    private ClassAdapter adapter;

    @Override
    protected FragmentClassManageBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentClassManageBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        prefs = requireContext().getSharedPreferences("user_session", requireContext().MODE_PRIVATE);
        classDao = AppDatabase.getDatabase(requireContext()).classDao();
        currentUserId = prefs.getInt("uid", -1);
        userRole = prefs.getInt("role", 0);

        // Setup toolbar navigation - removed as toolbar no longer exists

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        if (userRole == 1) {
            // Teacher
            binding.layoutTeacherSection.setVisibility(View.VISIBLE);
            binding.layoutStudentSection.setVisibility(View.GONE);
            adapter = new ClassAdapter(new ClassAdapter.OnClassActionListener() {
                @Override
                public void onViewMembers(Class classEntity) {
                    viewClassMembers(classEntity);
                }

                @Override
                public void onDeleteClass(Class classEntity) {
                    confirmDeleteClass(classEntity);
                }

                @Override
                public void onLeaveClass(Class classEntity) {
                    // Teacher doesn't leave
                }

                @Override
                public void onGoToForum(Class classEntity) {
                    // Teacher doesn't use this option from menu
                }
            }, true, classDao, getViewLifecycleOwner());
            binding.rvTeacherClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.rvTeacherClasses.setAdapter(adapter);
            loadTeacherClasses();
        } else {
            // Student
            binding.layoutStudentSection.setVisibility(View.VISIBLE);
            binding.layoutTeacherSection.setVisibility(View.GONE);
            adapter = new ClassAdapter(new ClassAdapter.OnClassActionListener() {
                @Override
                public void onViewMembers(Class classEntity) {
                    Toast.makeText(requireContext(), "班级: " + classEntity.className, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onDeleteClass(Class classEntity) {
                    // Student can't delete
                }

                @Override
                public void onLeaveClass(Class classEntity) {
                    confirmLeaveClass(classEntity);
                }

                @Override
                public void onGoToForum(Class classEntity) {
                    goToClassForum(classEntity);
                }
            }, false, classDao, getViewLifecycleOwner());
            binding.rvStudentClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.rvStudentClasses.setAdapter(adapter);
            loadStudentClasses();
        }
    }

    private void setupListeners() {
        if (userRole == 1) {
            binding.btnCreateClass.setOnClickListener(v -> createClass());
        } else {
            binding.btnJoinClass.setOnClickListener(v -> joinClass());
        }
    }

    private void loadTeacherClasses() {
        classDao.getClassesByTeacher(currentUserId).observe(getViewLifecycleOwner(), classes -> {
            adapter.setClasses(classes);
            boolean empty = classes == null || classes.isEmpty();
            binding.tvTeacherEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvTeacherClasses.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void loadStudentClasses() {
        classDao.getClassesByStudent(currentUserId).observe(getViewLifecycleOwner(), classes -> {
            adapter.setClasses(classes);
            boolean empty = classes == null || classes.isEmpty();
            binding.tvStudentEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvStudentClasses.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void createClass() {
        String className = binding.etClassName.getText() == null ? "" : binding.etClassName.getText().toString().trim();
        
        if (TextUtils.isEmpty(className)) {
            Toast.makeText(requireContext(), "请输入班级名称", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnCreateClass.setEnabled(false);
        executor.execute(() -> {
            String classCode = generateClassCode();
            Class newClass = new Class(className, classCode, currentUserId);
            long classId = classDao.insertClass(newClass);
            
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), getString(R.string.class_create_success, classCode), Toast.LENGTH_LONG).show();
                binding.etClassName.setText("");
                binding.btnCreateClass.setEnabled(true);
            });
        });
    }

    private void joinClass() {
        String classCode = binding.etClassCode.getText() == null ? "" : binding.etClassCode.getText().toString().trim().toUpperCase();
        
        if (TextUtils.isEmpty(classCode)) {
            Toast.makeText(requireContext(), "请输入班级邀请码", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnJoinClass.setEnabled(false);
        executor.execute(() -> {
            Class classEntity = classDao.getClassByCode(classCode);
            
            if (classEntity == null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), getString(R.string.class_not_exist), Toast.LENGTH_SHORT).show();
                    binding.btnJoinClass.setEnabled(true);
                });
                return;
            }

            ClassMember existing = classDao.getMembership(classEntity.classId, currentUserId);
            if (existing != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), getString(R.string.class_already_joined), Toast.LENGTH_SHORT).show();
                    binding.btnJoinClass.setEnabled(true);
                });
                return;
            }

            ClassMember member = new ClassMember(classEntity.classId, currentUserId);
            classDao.insertMember(member);
            
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), getString(R.string.class_join_success), Toast.LENGTH_SHORT).show();
                binding.etClassCode.setText("");
                binding.btnJoinClass.setEnabled(true);
            });
        });
    }

    private void viewClassMembers(Class classEntity) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_class_members, null);
        androidx.recyclerview.widget.RecyclerView rvMembers = dialogView.findViewById(R.id.rvMembers);
        TextView tvEmpty = dialogView.findViewById(R.id.tvMembersEmpty);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        
        tvTitle.setText(classEntity.className + " 成员列表");
        
        // Simple adapter for member list
        ClassMemberAdapter memberAdapter = new ClassMemberAdapter();
        
        rvMembers.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(memberAdapter);
        
        // Load members from database
        classDao.getStudentsByClass(classEntity.classId).observe(getViewLifecycleOwner(), users -> {
            if (users != null && !users.isEmpty()) {
                memberAdapter.setMembers(users);
                rvMembers.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.GONE);
            } else {
                rvMembers.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
        
        new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show();
    }

    // Inner class for member adapter with question frequency viewing
    private class ClassMemberAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
        private java.util.List<com.example.beihangagent.model.User> members = new java.util.ArrayList<>();
        private QuestionStatDao questionStatDao;
        private UserDao userDao;
        
        public ClassMemberAdapter() {
            questionStatDao = AppDatabase.getDatabase(requireContext()).questionStatDao();
            userDao = AppDatabase.getDatabase(requireContext()).userDao();
        }
        
        public void setMembers(java.util.List<com.example.beihangagent.model.User> memberList) {
            this.members = memberList != null ? memberList : new java.util.ArrayList<>();
            notifyDataSetChanged();
        }
        
        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class_member, parent, false);
            return new androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {};
        }
        
        @Override
        public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            com.example.beihangagent.model.User member = members.get(position);
            TextView tvName = holder.itemView.findViewById(R.id.tvMemberName);
            TextView tvUsername = holder.itemView.findViewById(R.id.tvMemberUsername);
            
            String displayName = android.text.TextUtils.isEmpty(member.name) ? member.username : member.name;
            tvName.setText(displayName);
            tvUsername.setText("学工号: " + member.username);
            
            // Add click listener to show question frequency
            holder.itemView.setOnClickListener(v -> showStudentQuestionStats(member));
        }
        
        private void showStudentQuestionStats(User student) {
            executor.execute(() -> {
                try {
                    // Query question stats for this student
                    List<QuestionStat> stats = questionStatDao.getStatsByUser(student.uid);
                    
                    requireActivity().runOnUiThread(() -> {
                        showQuestionStatsDialog(student, stats);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "查询提问数据失败", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return members.size();
        }
    }

    private void confirmDeleteClass(Class classEntity) {
        new AlertDialog.Builder(requireContext())
            .setTitle("删除班级")
            .setMessage(getString(R.string.class_delete_confirm, classEntity.className))
            .setPositiveButton("删除", (dialog, which) -> deleteClass(classEntity))
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteClass(Class classEntity) {
        executor.execute(() -> {
            classDao.deleteClass(classEntity.classId);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), getString(R.string.class_deleted), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmLeaveClass(Class classEntity) {
        new AlertDialog.Builder(requireContext())
            .setTitle("退出班级")
            .setMessage(getString(R.string.class_leave_confirm, classEntity.className))
            .setPositiveButton("退出", (dialog, which) -> leaveClass(classEntity))
            .setNegativeButton("取消", null)
            .show();
    }

    private void leaveClass(Class classEntity) {
        executor.execute(() -> {
            classDao.removeMember(classEntity.classId, currentUserId);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), getString(R.string.class_left), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private String generateClassCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    
    private void showQuestionStatsDialog(User student, List<QuestionStat> stats) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_student_stats, null);
        TextView tvStudentName = dialogView.findViewById(R.id.tvStudentName);
        TextView tvTotalQuestions = dialogView.findViewById(R.id.tvTotalQuestions);
        androidx.recyclerview.widget.RecyclerView rvStats = dialogView.findViewById(R.id.rvStats);
        TextView tvEmptyStats = dialogView.findViewById(R.id.tvEmptyStats);
        
        String displayName = TextUtils.isEmpty(student.name) ? student.username : student.name;
        tvStudentName.setText(displayName + " 的提问统计");
        
        if (stats != null && !stats.isEmpty()) {
            int totalQuestions = 0;
            for (QuestionStat stat : stats) {
                totalQuestions += stat.count;
            }
            tvTotalQuestions.setText("总提问数：" + totalQuestions + " 次");
            
            // Setup stats recycler view
            StudentStatsAdapter statsAdapter = new StudentStatsAdapter(stats);
            rvStats.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvStats.setAdapter(statsAdapter);
            rvStats.setVisibility(View.VISIBLE);
            tvEmptyStats.setVisibility(View.GONE);
        } else {
            tvTotalQuestions.setText("总提问数：0 次");
            rvStats.setVisibility(View.GONE);
            tvEmptyStats.setVisibility(View.VISIBLE);
        }
        
        new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show();
    }
    
    // Adapter for student question stats
    private static class StudentStatsAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
        private final List<QuestionStat> stats;
        
        public StudentStatsAdapter(List<QuestionStat> stats) {
            this.stats = stats;
        }
        
        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {};
        }
        
        @Override
        public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            QuestionStat stat = stats.get(position);
            TextView text1 = holder.itemView.findViewById(android.R.id.text1);
            TextView text2 = holder.itemView.findViewById(android.R.id.text2);
            
            text1.setText(stat.topic);
            text2.setText("提问 " + stat.count + " 次");
        }
        
        @Override
        public int getItemCount() {
            return stats.size();
        }
    }

    private void goToClassForum(Class classEntity) {
        ClassSelectionFragment classSelectionFragment = new ClassSelectionFragment();
        
        if (getParentFragmentManager() != null) {
            getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, classSelectionFragment)
                .addToBackStack("ClassSelectionFragment")
                .commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
