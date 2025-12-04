package com.example.beihangagent.view.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentChatBinding;
import com.example.beihangagent.view.adapter.ChatAdapter;
import com.example.beihangagent.view.adapter.ConversationAdapter;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.viewmodel.ChatViewModel;
import com.example.beihangagent.model.UserProfile;
import com.example.beihangagent.model.ConversationRecord;
import com.example.beihangagent.util.PersonalizationAnalyzer;
import com.example.beihangagent.util.UserProfileDao;

import android.view.View;
import android.widget.AdapterView;
import com.google.android.material.chip.Chip;
import com.example.beihangagent.model.Conversation;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChatFragment extends BaseFragment<FragmentChatBinding> {

    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private List<String> selectedComparisonModels = new ArrayList<>();
    private final String[] AVAILABLE_MODELS = {"gpt-4o", "gpt-4.1", "gpt-4-turbo", "gpt-4"};
    
    // 个性化相关字段
    private UserProfileDao profileDao;
    private UserProfile currentUserProfile;
    private String currentSessionId;
    private int currentUserId = 1; // 当前用户ID，这里简化为固定值
    
    // System Prompts
    public static final String PROMPT_TUTOR = "你是北航%s的助教，请引导学生思考，不要直接给出答案。你的回答应该循循善诱，帮助学生理解%s的核心概念。";
    public static final String PROMPT_REVIEW = "你是一个专业的代码审查员，请仔细分析提供的代码，只指出真实存在的问题。不要臆想或假设不存在的问题。重点检查：1）语法错误；2）逻辑错误；3）潜在的运行时异常；4）性能问题；5）代码规范问题。如果代码没有明显问题，请如实说明代码质量良好。";
    public static final String PROMPT_OPTIMIZE = "请为代码添加中文注释并进行优化。在 RecyclerView 中单独渲染优化后的代码块。";

    private static final String[] LOADING_MESSAGES = {
        "正在让 CPU 飞一会儿...",
        "AI 正在头脑风暴...",
        "正在查阅 StackOverflow...",
        "正在编译思路...",
        "喝口咖啡，马上就好...",
        "正在连接北航知识库..."
    };

    private static final String[] DAILY_TIPS = {
        "💡 每日小贴士：在 Java 中，使用 StringBuilder 进行字符串拼接比使用 + 号更高效哦！",
        "💡 每日小贴士：ViewBinding 可以有效避免 NullPointerException，推荐在所有 Fragment 中使用。",
        "💡 每日小贴士：Retrofit 的 ConverterFactory 顺序很重要，GsonConverterFactory 通常放在最后。",
        "💡 每日小贴士：Room 数据库操作必须在后台线程执行，否则会阻塞 UI 线程。",
        "💡 每日小贴士：使用 LiveData 可以感知生命周期，避免内存泄漏。",
        "💡 每日小贴士：ConstraintLayout 可以减少布局嵌套层级，提升渲染性能。"
    };

    /**
     * 初始化个性化功能
     */
    private void initPersonalization() {
        // 使用简单的SharedPreferences来存储个性化状态，避免复杂的数据库操作
        currentSessionId = "session_" + System.currentTimeMillis();
        
        // 从SharedPreferences读取对话计数
        SharedPreferences prefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
        int totalConversations = prefs.getInt("total_conversations", 0);
        
        // 创建一个简化的用户档案对象
        currentUserProfile = new UserProfile(currentUserId);
        currentUserProfile.totalConversations = totalConversations;
        
        // 立即更新状态显示
        updatePersonalizationStatus();
    }
    
    /**
     * 更新用户档案分析
     */
    private void updateUserProfileAnalysis() {
        new Thread(() -> {
            try {
                List<ConversationRecord> recentRecords = profileDao.getRecentConversations(currentUserId, 50);
                if (!recentRecords.isEmpty()) {
                    currentUserProfile = PersonalizationAnalyzer.updateProfile(currentUserProfile, recentRecords);
                    profileDao.updateProfile(currentUserProfile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * 更新个性化状态显示
     */
    private void updatePersonalizationStatus() {
        if (currentUserProfile == null) {
            // 智能化标签已移除
            return;
        }
        
        // 智能化标签已移除
        
        int conversations = currentUserProfile.totalConversations;
        
        String statusText;
        int color;
        
        if (conversations < 3) {
            statusText = getString(R.string.personalization_learning);
            color = 0xFF9E9E9E; // 灰色
        } else if (conversations < 10) {
            statusText = getString(R.string.personalization_adapting);
            color = 0xFF03A9F4; // 蓝色
        } else if (conversations < 30) {
            statusText = getString(R.string.personalization_personalized);
            color = 0xFF4CAF50; // 绿色
        } else {
            statusText = getString(R.string.personalization_intelligent);
            color = 0xFFFFC107; // 金色
        }
        
        // 智能化标签已移除
        // binding.tvPersonalizationStatus.setTextColor(color);
    }
    
    /**
     * 记录用户对话以供个性化分析
     */
    private void recordConversation(String question, String response) {
        // 使用SharedPreferences简单记录对话次数
        SharedPreferences prefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
        int totalConversations = prefs.getInt("total_conversations", 0) + 1;
        
        // 简单分析问题类型
        String questionType = analyzeQuestionType(question);
        String currentTypes = prefs.getString("question_types", "");
        if (!currentTypes.contains(questionType)) {
            currentTypes = currentTypes.isEmpty() ? questionType : currentTypes + "," + questionType;
        }
        
        // 保存到SharedPreferences
        prefs.edit()
            .putInt("total_conversations", totalConversations)
            .putString("question_types", currentTypes)
            .putString("last_question_type", questionType)
            .apply();
            
        // 更新当前档案
        if (currentUserProfile != null) {
            currentUserProfile.totalConversations = totalConversations;
            updatePersonalizationStatus();
        }
    }
    
    /**
     * 简单分析问题类型
     */
    private String analyzeQuestionType(String question) {
        String lowerQuestion = question.toLowerCase();
        if (lowerQuestion.contains("是什么") || lowerQuestion.contains("概念") || lowerQuestion.contains("定义")) {
            return "概念型";
        } else if (lowerQuestion.contains("代码") || lowerQuestion.contains("实现") || lowerQuestion.contains("编写")) {
            return "实践型";
        } else if (lowerQuestion.contains("为什么") || lowerQuestion.contains("原理") || lowerQuestion.contains("理论")) {
            return "理论型";
        } else if (lowerQuestion.contains("怎么") || lowerQuestion.contains("如何") || lowerQuestion.contains("步骤")) {
            return "操作型";
        } else {
            return "综合型";
        }
    }

    @Override
    protected FragmentChatBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentChatBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        adapter = new ChatAdapter(requireContext());

        binding.rvChat.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChat.setAdapter(adapter);
        
        // 初始化个性化组件
        initPersonalization();
        
        // Set random daily tip
        binding.tvEmptyStateTip.setText(DAILY_TIPS[new Random().nextInt(DAILY_TIPS.length)]);

        String[] modes = {"智能导师", "代码评审", "代码优化", "模型对比"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, modes);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMode.setAdapter(spinnerAdapter);
        binding.spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateInputMode(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] models = AVAILABLE_MODELS;
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, models);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerModel.setAdapter(modelAdapter);
        
        binding.tvModelSelection.setOnClickListener(v -> showModelSelectionDialog());

        String[] styles = {"默认", "简洁", "详尽", "拓展"};
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, styles);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStyle.setAdapter(styleAdapter);

        binding.btnSend.setOnClickListener(v -> sendSimpleMessage());
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendSimpleMessage();
                return true;
            }
            return false;
        });
        
        binding.btnSubmitAdvanced.setOnClickListener(v -> sendAdvancedMessage());
        
        // Add listeners for expandable input
        binding.layoutCodeTrigger.setOnClickListener(v -> setAdvancedInputExpanded(true));
        binding.btnCloseAdvanced.setOnClickListener(v -> setAdvancedInputExpanded(false));
        binding.viewOverlay.setOnClickListener(v -> setAdvancedInputExpanded(false));
        
        setupConversationControls();
        setupSettingsToggle();
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.deleteEmptyConversations();
    }

    private void setupSettingsToggle() {
        binding.btnToggleSettings.setOnClickListener(v -> {
            if (binding.cardModeSelector.getVisibility() == View.VISIBLE) {
                binding.cardModeSelector.setVisibility(View.GONE);
            } else {
                binding.cardModeSelector.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupConversationControls() {
        binding.btnHistory.setOnClickListener(v -> showHistoryDialog());
        binding.btnNewChat.setOnClickListener(v -> {
            viewModel.createNewConversation("新对话");
            Toast.makeText(requireContext(), "新对话已创建", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnEditTitle.setOnClickListener(v -> showRenameDialog());

        viewModel.currentConversationIdLiveData.observe(getViewLifecycleOwner(), this::updateTitle);
        
        viewModel.conversations.observe(getViewLifecycleOwner(), list -> {
             updateTitle(viewModel.getCurrentConversationId());
        });
    }
    
    private void updateTitle(long id) {
        if (id != -1) {
            List<Conversation> list = viewModel.conversations.getValue();
            if (list != null) {
                for (Conversation c : list) {
                    if (c.id == id) {
                        binding.tvCurrentConversation.setText(c.title);
                        return;
                    }
                }
            }
        }
        binding.tvCurrentConversation.setText("新对话");
    }

    private void showHistoryDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.dialog_history);
        
        RecyclerView rvHistory = dialog.findViewById(R.id.rvHistory);
        if (rvHistory != null) {
            rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
            ConversationAdapter historyAdapter = new ConversationAdapter(
                conversation -> {
                    viewModel.switchConversation(conversation.id);
                    dialog.dismiss();
                },
                conversation -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除对话")
                        .setMessage("确定要删除这个对话吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            viewModel.deleteConversation(conversation);
                            Toast.makeText(requireContext(), "对话已删除", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                }
            );
            rvHistory.setAdapter(historyAdapter);
            
            viewModel.conversations.observe(getViewLifecycleOwner(), list -> {
                historyAdapter.setConversations(list);
            });
        }
        dialog.show();
    }

    private void showRenameDialog() {
        long currentId = viewModel.getCurrentConversationId();
        // Allow renaming even if currentId is -1 (New Chat)
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rename_conversation, null);
        com.google.android.material.textfield.TextInputEditText etRename = dialogView.findViewById(R.id.etRename);
        
        // Pre-fill current title
        String currentTitle = binding.tvCurrentConversation.getText().toString();
        etRename.setText(currentTitle);
        etRename.setSelection(currentTitle.length());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改对话名称")
            .setView(dialogView)
            .setPositiveButton("确定", (dialog, which) -> {
                String newTitle = etRename.getText() != null ? etRename.getText().toString().trim() : "";
                if (!TextUtils.isEmpty(newTitle)) {
                    viewModel.renameConversation(currentId, newTitle);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateInputMode(int position) {
        // Reset style to default when mode changes
        if (binding.spinnerStyle != null && binding.spinnerStyle.getAdapter() != null) {
            binding.spinnerStyle.setSelection(0);
        }

        if (position == 0 || position == 3) { // AI Tutor or Model Comparison
            binding.layoutSimpleInput.setVisibility(View.VISIBLE);
            binding.layoutCodeTrigger.setVisibility(View.GONE);
            binding.layoutAdvancedInput.setVisibility(View.GONE);
            binding.viewOverlay.setVisibility(View.GONE);
            
            if (position == 3) { // Comparison Mode
                binding.tvModelLabel.setText("模型对比");
                binding.spinnerModel.setVisibility(View.GONE);
                binding.tvModelSelection.setVisibility(View.VISIBLE);
                updateModelSelectionText();
            } else {
                binding.tvModelLabel.setText(getString(R.string.chat_model_label));
                binding.spinnerModel.setVisibility(View.VISIBLE);
                binding.tvModelSelection.setVisibility(View.GONE);
            }
            
            binding.spinnerModel.setEnabled(true);
            binding.spinnerModel.setAlpha(1.0f);
            
        } else { // Code Review or Optimize
            binding.layoutSimpleInput.setVisibility(View.GONE);
            // Reset to collapsed state when switching modes
            setAdvancedInputExpanded(false);
            
            binding.tvModelLabel.setText(getString(R.string.chat_model_label));
            binding.spinnerModel.setVisibility(View.VISIBLE);
            binding.tvModelSelection.setVisibility(View.GONE);
            binding.spinnerModel.setEnabled(true);
            binding.spinnerModel.setAlpha(1.0f);
            
            // Toggle Chips based on mode
            boolean isReview = (position == 1);
            
            // Review Options
            binding.chipSecurity.setVisibility(isReview ? View.VISIBLE : View.GONE);
            binding.chipStyle.setVisibility(isReview ? View.VISIBLE : View.GONE);
            binding.chipBugs.setVisibility(isReview ? View.VISIBLE : View.GONE);
            binding.chipNaming.setVisibility(isReview ? View.VISIBLE : View.GONE);
            
            // Optimize Options
            binding.chipPerformance.setVisibility(isReview ? View.GONE : View.VISIBLE);
            binding.chipComments.setVisibility(isReview ? View.GONE : View.VISIBLE);
            binding.chipComplexity.setVisibility(isReview ? View.GONE : View.VISIBLE);
            binding.chipSimplify.setVisibility(isReview ? View.GONE : View.VISIBLE);
        }
    }

    private void setAdvancedInputExpanded(boolean expanded) {
        if (expanded) {
            binding.layoutCodeTrigger.setVisibility(View.GONE);
            binding.layoutAdvancedInput.setVisibility(View.VISIBLE);
            binding.viewOverlay.setVisibility(View.VISIBLE);
            binding.etCodeInput.requestFocus();
        } else {
            binding.layoutCodeTrigger.setVisibility(View.VISIBLE);
            binding.layoutAdvancedInput.setVisibility(View.GONE);
            binding.viewOverlay.setVisibility(View.GONE);
            // Clear focus to hide keyboard if needed
            binding.etCodeInput.clearFocus();
        }
    }

    private int lastMessageCount = 0;

    @Override
    protected void initObservers() {
        viewModel.messages.observe(getViewLifecycleOwner(), messages -> {
            int currentCount = messages.size();
            boolean shouldScroll = false;
            
            // Only scroll when a new non-pending message is added
            if (currentCount > lastMessageCount) {
                shouldScroll = true;
            } else if (currentCount == lastMessageCount && !messages.isEmpty()) {
                // Check if the last message just changed from pending to non-pending
                if (!messages.get(messages.size() - 1).isPending) {
                    shouldScroll = true;
                }
            }
            
            adapter.setMessages(messages);
            
            if (!messages.isEmpty()) {
                // Only scroll to bottom if not pending and should scroll
                if (shouldScroll && !messages.get(messages.size() - 1).isPending) {
                    // Find the last AI message and scroll to its position with a slight delay
                    binding.rvChat.post(() -> {
                        int aiMessagePosition = -1;
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            if ("assistant".equals(messages.get(i).role)) {
                                aiMessagePosition = i;
                                break;
                            }
                        }
                        
                        // Scroll to AI response start position - fixed to show AI message from the top
                        if (aiMessagePosition != -1) {
                            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.rvChat.getLayoutManager();
                            if (layoutManager != null) {
                                layoutManager.scrollToPositionWithOffset(aiMessagePosition, 0);
                            }
                        }
                    });
                }
                binding.tvEmptyStateTip.setVisibility(View.GONE);
            } else {
                binding.tvEmptyStateTip.setVisibility(View.VISIBLE);
            }
            
            lastMessageCount = currentCount;
        });

        viewModel.error.observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
        
        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnSend.setEnabled(!isLoading);
            binding.btnSubmitAdvanced.setEnabled(!isLoading);
            if (isLoading) {
                String loadingText = LOADING_MESSAGES[new Random().nextInt(LOADING_MESSAGES.length)];
                binding.btnSubmitAdvanced.setText(loadingText);
            } else {
                binding.btnSubmitAdvanced.setText("开始分析");
            }
        });
    }

    private void appendTonePrompt(StringBuilder prompt) {
        int stylePos = binding.spinnerStyle.getSelectedItemPosition();
        switch (stylePos) {
            case 0: // 默认
                break;
            case 1: // 简洁
                prompt.append("\n请直接回答问题，不要做多余的展开和太细致的讲解。");
                break;
            case 2: // 详尽
                prompt.append("\n请对问题进行非常详细的解释，尽可能覆盖相关的知识点和细节。");
                break;
            case 3: // 拓展
                prompt.append("\n请基于对话记录，揣测用户的提问方向，并在回答后给用户一些相关的提问示例。");
                break;
        }
    }

    private void sendSimpleMessage() {
        String msg = binding.etMessage.getText() == null ? "" : binding.etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) {
            binding.tilMessage.setError(getString(R.string.chat_input_empty));
            binding.tilMessage.postDelayed(() -> binding.tilMessage.setError(null), 1500);
            return;
        }
        binding.tilMessage.setError(null);
        binding.etMessage.setText("");

        int mode = binding.spinnerMode.getSelectedItemPosition();
        if (mode == 3) { // Model Comparison
            if (selectedComparisonModels.isEmpty()) {
                Toast.makeText(requireContext(), "请至少选择一个对比模型", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.sendComparisonToAi(msg, getDynamicTutorPrompt(), new ArrayList<>(selectedComparisonModels));
            return;
        }

        // Simple mode is always AI Tutor
        int stylePos = binding.spinnerStyle.getSelectedItemPosition();
        String basePrompt = getDynamicTutorPrompt();
        
        // Modify base prompt based on style selection
        if (stylePos == 1) {
            // "简洁" style - remove Socratic constraint, be more direct
            SharedPreferences prefs = requireContext().getSharedPreferences("user_session", requireContext().MODE_PRIVATE);
            String userCollege = prefs.getString("preference", "");
            if (userCollege.isEmpty()) {
                basePrompt = "你是北航的助教。请直接回答学生的问题，简洁明了。学生未设置学院信息，请提供通用的学术指导。";
            } else {
                basePrompt = "你是北航" + userCollege + "的助教。请直接回答学生的问题，简洁明了。";
            }
        } else if (stylePos == 3) {
            // "拓展" style - one-question-one-answer format with examples
            SharedPreferences prefs = requireContext().getSharedPreferences("user_session", requireContext().MODE_PRIVATE);
            String userCollege = prefs.getString("preference", "");
            if (userCollege.isEmpty()) {
                basePrompt = "你是北航的助教。请直接回答学生的问题，并在回答后给出相关的提问示例。学生未设置学院信息，请提供通用的学术指导。";
            } else {
                basePrompt = "你是北航" + userCollege + "的助教。请直接回答学生的问题，并在回答后给出相关的提问示例。";
            }
        }

        StringBuilder currentPrompt = new StringBuilder(basePrompt);
        if (msg.contains("NullPointerException")) {
            currentPrompt.append(" (注意：学生遇到了空指针异常，请重点讲解空指针防御)");
        }
        
        appendTonePrompt(currentPrompt);
        
        sendToViewModel(msg, currentPrompt.toString());
    }

    private void sendAdvancedMessage() {
        String code = binding.etCodeInput.getText() == null ? "" : binding.etCodeInput.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(requireContext(), "请输入代码", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.etCodeInput.setText("");

        int position = binding.spinnerMode.getSelectedItemPosition();
        String basePrompt = (position == 1) ? PROMPT_REVIEW : PROMPT_OPTIMIZE;
        StringBuilder finalPrompt = new StringBuilder(basePrompt);
        
        List<String> focusAreas = new ArrayList<>();
        
        // Review Options
        if (binding.chipSecurity.isChecked() && binding.chipSecurity.getVisibility() == View.VISIBLE) focusAreas.add("安全性漏洞");
        if (binding.chipStyle.isChecked() && binding.chipStyle.getVisibility() == View.VISIBLE) focusAreas.add("代码规范(Google Style)");
        if (binding.chipBugs.isChecked() && binding.chipBugs.getVisibility() == View.VISIBLE) focusAreas.add("潜在Bug分析");
        if (binding.chipNaming.isChecked() && binding.chipNaming.getVisibility() == View.VISIBLE) focusAreas.add("变量命名规范");

        // Optimize Options
        if (binding.chipPerformance.isChecked() && binding.chipPerformance.getVisibility() == View.VISIBLE) focusAreas.add("性能瓶颈");
        if (binding.chipComments.isChecked() && binding.chipComments.getVisibility() == View.VISIBLE) focusAreas.add("详细中文注释");
        if (binding.chipComplexity.isChecked() && binding.chipComplexity.getVisibility() == View.VISIBLE) focusAreas.add("时间/空间复杂度分析");
        if (binding.chipSimplify.isChecked() && binding.chipSimplify.getVisibility() == View.VISIBLE) focusAreas.add("代码逻辑精简");

        appendTonePrompt(finalPrompt);

        if (!focusAreas.isEmpty()) {
            finalPrompt.append("\n请重点关注以下方面：").append(String.join("、", focusAreas));
        }

        sendToViewModel(code, finalPrompt.toString());
        setAdvancedInputExpanded(false);
    }

    private void sendToViewModel(String msg, String systemPrompt) {
        String modelName = binding.spinnerModel.getSelectedItem() == null
            ? "gpt-4o"
            : binding.spinnerModel.getSelectedItem().toString();

        Toast.makeText(requireContext(), getString(R.string.chat_using_model, modelName), Toast.LENGTH_SHORT).show();
        
        // 发送到AI并观察响应
        viewModel.sendToAi(msg, systemPrompt, modelName);
        
        // 观察AI响应以记录完整对话
        viewModel.messages.observe(getViewLifecycleOwner(), messages -> {
            if (!messages.isEmpty()) {
                // 获取最新的AI响应
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if ("assistant".equals(messages.get(i).role)) {
                        recordConversation(msg, messages.get(i).content);
                        break;
                    }
                }
            }
        });
    }

    private void showModelSelectionDialog() {
        boolean[] checkedItems = new boolean[AVAILABLE_MODELS.length];
        for (int i = 0; i < AVAILABLE_MODELS.length; i++) {
            if (selectedComparisonModels.contains(AVAILABLE_MODELS[i])) {
                checkedItems[i] = true;
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择对比模型")
            .setMultiChoiceItems(AVAILABLE_MODELS, checkedItems, (dialog, which, isChecked) -> {
                if (isChecked) {
                    selectedComparisonModels.add(AVAILABLE_MODELS[which]);
                } else {
                    selectedComparisonModels.remove(AVAILABLE_MODELS[which]);
                }
                updateModelSelectionText();
            })
            .setPositiveButton("确定", null)
            .show();
    }

    private void updateModelSelectionText() {
        if (selectedComparisonModels.isEmpty()) {
            binding.tvModelSelection.setText("选择对比模型");
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                binding.tvModelSelection.setText(String.join(" / ", selectedComparisonModels));
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < selectedComparisonModels.size(); i++) {
                    sb.append(selectedComparisonModels.get(i));
                    if (i < selectedComparisonModels.size() - 1) sb.append(" / ");
                }
                binding.tvModelSelection.setText(sb.toString());
            }
        }
    }

    private String getDynamicTutorPrompt() {
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", requireContext().MODE_PRIVATE);
        String userCollege = prefs.getString("preference", "");
        
        // 如果用户没有设置学院，返回通用提示词
        if (userCollege.isEmpty()) {
            return "你是北航的助教，请引导学生思考，不要直接给出答案。你的回答应该循循善诱，帮助学生理解相关学科的核心概念。由于学生未设置学院信息，请提供通用的学术指导。";
        }
        
        String subject;
        if (userCollege.contains("计算机")) {
            subject = "计算机科学";
        } else if (userCollege.contains("软件")) {
            subject = "软件工程";
        } else if (userCollege.contains("网络") || userCollege.contains("安全")) {
            subject = "网络安全";
        } else if (userCollege.contains("电子") || userCollege.contains("信息")) {
            subject = "电子信息";
        } else if (userCollege.contains("数学")) {
            subject = "数学";
        } else if (userCollege.contains("物理")) {
            subject = "物理学";
        } else if (userCollege.contains("机械")) {
            subject = "机械工程";
        } else if (userCollege.contains("材料")) {
            subject = "材料科学";
        } else if (userCollege.contains("能源") || userCollege.contains("动力")) {
            subject = "能源动力";
        } else if (userCollege.contains("航空") || userCollege.contains("航天")) {
            subject = "航空航天";
        } else if (userCollege.contains("交通")) {
            subject = "交通运输";
        } else if (userCollege.contains("经济") || userCollege.contains("管理")) {
            subject = "经济管理";
        } else if (userCollege.contains("人文") || userCollege.contains("社会")) {
            subject = "人文社科";
        } else if (userCollege.contains("外国语")) {
            subject = "外语";
        } else if (userCollege.contains("法学")) {
            subject = "法学";
        } else if (userCollege.contains("公共管理")) {
            subject = "公共管理";
        } else {
            subject = "相关学科";
        }
        
        // 基础提示词
        String basePrompt = String.format(PROMPT_TUTOR, userCollege, subject);
        
        // 添加个性化增强
        String personalizedEnhancement = getSimplePersonalizedPrompt();
        if (!personalizedEnhancement.isEmpty()) {
            return basePrompt + " " + personalizedEnhancement;
        }
        
        return basePrompt;
    }
    
    /**
     * 基于SharedPreferences的简单个性化提示词
     */
    private String getSimplePersonalizedPrompt() {
        SharedPreferences prefs = requireContext().getSharedPreferences("personalization", Context.MODE_PRIVATE);
        int totalConversations = prefs.getInt("total_conversations", 0);
        String lastQuestionType = prefs.getString("last_question_type", "");
        
        StringBuilder enhancement = new StringBuilder();
        
        // 根据对话次数调整
        if (totalConversations < 3) {
            enhancement.append("这是用户的前几次提问，请耐心引导，从基础概念开始。");
        } else if (totalConversations < 10) {
            enhancement.append("用户已进行多次对话，可以适当增加深度。");
        } else if (totalConversations >= 10) {
            enhancement.append("用户已较为熟悉，可以提供更深入的指导和高级概念。");
        }
        
        // 根据最近的问题类型调整
        if (!lastQuestionType.isEmpty()) {
            switch (lastQuestionType) {
                case "概念型":
                    enhancement.append("用户偏好概念理解，多用类比和图解说明。");
                    break;
                case "实践型":
                    enhancement.append("用户注重实际应用，多提供代码示例和实际案例。");
                    break;
                case "理论型":
                    enhancement.append("用户关注理论基础，可以深入探讨原理和机制。");
                    break;
                case "操作型":
                    enhancement.append("用户需要具体步骤，请提供详细的操作指南。");
                    break;
                default:
                    enhancement.append("请根据问题特点灵活调整回答方式。");
                    break;
            }
        }
        
        return enhancement.toString();
    }
}
