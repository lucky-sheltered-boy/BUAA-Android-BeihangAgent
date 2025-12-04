package com.example.beihangagent.view.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.style.BackgroundColorSpan;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beihangagent.R;
import com.example.beihangagent.model.ChatMessage;
import com.example.beihangagent.util.BeihangGrammarLocator;
import com.google.android.material.tabs.TabLayout;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.prism4j.Prism4j;
import org.commonmark.node.Code;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import androidx.annotation.Nullable;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;
    private static final int VIEW_TYPE_COMPARISON = 3;

    private List<ChatMessage> messages = new ArrayList<>();
    private final Markwon markwon;
    private final ClipboardManager clipboardManager;
    private final Context appContext;
    private final Pattern codeBlockPattern = Pattern.compile("```([\\w+#.-]*)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    public ChatAdapter(Context context) {
        Prism4j prism4j = new Prism4j(new BeihangGrammarLocator());
        
        this.markwon = Markwon.builder(context)
            // LaTeX插件 - 支持块级公式渲染
            .usePlugin(JLatexMathPlugin.create(34f, new JLatexMathPlugin.BuilderConfigure() {
                @Override
                public void configureBuilder(JLatexMathPlugin.Builder builder) {
                    builder.inlinesEnabled(true);  // 启用行内公式
                    builder.blocksEnabled(true);   // 启用块级公式
                }
            }))
            // 内联解析器插件
            .usePlugin(MarkwonInlineParserPlugin.create())
            // 表格插件
            .usePlugin(TablePlugin.create(context))
            // 语法高亮插件 - 使用Darkula主题
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            // 增强的代码块主题
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                    builder
                        // 代码块样式 - 使用灰色背景
                        .codeBlockBackgroundColor(ContextCompat.getColor(context, R.color.code_block_bg))
                        .codeTextColor(ContextCompat.getColor(context, R.color.code_block_text))
                        .codeBlockMargin(16)
                        .codeBlockTextSize(36)
                        // 内联代码样式 
                        .codeBackgroundColor(ContextCompat.getColor(context, R.color.inline_code_bg))
                        .codeTextSize(32);
                }
                
                @Override
                public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                    builder.setFactory(Code.class, (configuration, props) -> {
                        return new android.text.style.StyleSpan(android.graphics.Typeface.BOLD) {
                            @Override
                            public void updateDrawState(android.text.TextPaint tp) {
                                super.updateDrawState(tp);
                                tp.setTypeface(android.graphics.Typeface.MONOSPACE);
                                tp.setFakeBoldText(true);
                                tp.setTextSize(32f);
                                tp.setColor(ContextCompat.getColor(context, R.color.beihang_blue));
                            }
                        };
                    });
                }
            })
            .build();
            
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.appContext = context.getApplicationContext();
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        if (message.type == ChatMessage.TYPE_COMPARISON) {
            return VIEW_TYPE_COMPARISON;
        }
        return "user".equals(message.role) ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_user, parent, false);
            return new UserMessageHolder(view);
        } else if (viewType == VIEW_TYPE_COMPARISON) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_comparison, parent, false);
            return new ComparisonMessageHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_ai, parent, false);
            return new AiMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserMessageHolder) {
            ((UserMessageHolder) holder).bind(message);
        } else if (holder instanceof AiMessageHolder) {
            ((AiMessageHolder) holder).bind(message, markwon);
        } else if (holder instanceof ComparisonMessageHolder) {
            ((ComparisonMessageHolder) holder).bind(message, markwon);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class UserMessageHolder extends RecyclerView.ViewHolder {
        private final TextView tvContent;

        UserMessageHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
        }

        void bind(ChatMessage message) {
            String content = message.content != null ? message.content : "";
            tvContent.setText(content);
        }
    }

    class AiMessageHolder extends RecyclerView.ViewHolder {
        private final ImageButton btnCopyAnswer;
        private final LinearLayout layoutMessageBlocks;
        private final View progressPending;
        private final LayoutInflater inflater;

        AiMessageHolder(@NonNull View itemView) {
            super(itemView);
            btnCopyAnswer = itemView.findViewById(R.id.btnCopyAnswer);
            layoutMessageBlocks = itemView.findViewById(R.id.layoutMessageBlocks);
            progressPending = itemView.findViewById(R.id.progressPending);
            inflater = LayoutInflater.from(itemView.getContext());
        }

        void bind(ChatMessage message, Markwon markwon) {
            if (layoutMessageBlocks != null) {
                layoutMessageBlocks.removeAllViews();
                renderMessageBlocks(message, markwon);
            }

            // Show pending indicator if message is pending
            if (progressPending != null) {
                progressPending.setVisibility(message.isPending ? View.VISIBLE : View.GONE);
            }

            if (btnCopyAnswer != null) {
                boolean hasContent = message.content != null && !message.content.trim().isEmpty();
                // Hide copy button when pending
                btnCopyAnswer.setVisibility(message.isPending ? View.GONE : View.VISIBLE);
                btnCopyAnswer.setEnabled(hasContent);
                btnCopyAnswer.setOnClickListener(hasContent ?
                        v -> copyToClipboard(R.string.chat_copy_label_answer, message.content)
                        : null);
            }
        }

        private void renderMessageBlocks(ChatMessage message, Markwon markwon) {
            String content = message.content != null ? message.content : "";
            if (content.isEmpty()) {
                return;
            }

            // 预处理LaTeX公式：将\[...\]转换为$$...$$
            content = preprocessLatexFormulas(content);

            Matcher matcher = codeBlockPattern.matcher(content);
            int lastIndex = 0;
            while (matcher.find()) {
                addTextBlock(content.substring(lastIndex, matcher.start()), markwon);
                addCodeBlock(matcher.group(1), matcher.group(2), markwon);
                lastIndex = matcher.end();
            }
            addTextBlock(content.substring(lastIndex), markwon);

            if (layoutMessageBlocks.getChildCount() == 0 && message.type == ChatMessage.TYPE_CODE) {
                addCodeBlock("", content, markwon);
            }
        }

        private void addTextBlock(String markdown, Markwon markwon) {
            if (markdown == null) {
                return;
            }
            String trimmed = markdown.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            
            TextView textView = new TextView(itemView.getContext());
            textView.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
            textView.setTextIsSelectable(true);
            textView.setPadding(0, 6, 0, 6);
            textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            markwon.setMarkdown(textView, trimmed);
            layoutMessageBlocks.addView(textView);
        }

        private void addCodeBlock(String language, String codeBlock, Markwon markwon) {
            if (codeBlock == null) {
                return;
            }
            String trimmed = codeBlock.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            View codeView = inflater.inflate(R.layout.item_ai_code_block, layoutMessageBlocks, false);
            TextView tvCode = codeView.findViewById(R.id.tvCodeContent);
            ImageButton btnCopyCode = codeView.findViewById(R.id.btnCopyCodeBlock);
            TextView tvLabel = codeView.findViewById(R.id.tvCodeLabel);

            String fencedLanguage = language == null ? "" : language.trim();
            if (tvLabel != null) {
                if (TextUtils.isEmpty(fencedLanguage)) {
                    tvLabel.setText(R.string.chat_copy_label_code);
                } else {
                    tvLabel.setText(fencedLanguage);
                }
            }

            if (markwon != null) {
                StringBuilder fencedBuilder = new StringBuilder("```");
                if (!TextUtils.isEmpty(fencedLanguage)) {
                    fencedBuilder.append(fencedLanguage);
                }
                fencedBuilder.append("\n").append(trimmed).append("\n```");
                markwon.setMarkdown(tvCode, fencedBuilder.toString());
            } else {
                tvCode.setText(trimmed);
            }

            if (btnCopyCode != null) {
                btnCopyCode.setOnClickListener(v -> copyToClipboard(R.string.chat_copy_label_code, trimmed));
            }
            layoutMessageBlocks.addView(codeView);
        }
    }

    class ComparisonMessageHolder extends RecyclerView.ViewHolder {
        private final TabLayout tabLayout;
        private final TextView tvContent;
        private final ImageButton btnCopy;
        private final View progressPending;
        private final List<String> contents = new ArrayList<>();

        public ComparisonMessageHolder(@NonNull View itemView) {
            super(itemView);
            tabLayout = itemView.findViewById(R.id.tabLayoutComparison);
            tvContent = itemView.findViewById(R.id.tvComparisonContent);
            btnCopy = itemView.findViewById(R.id.btnCopyComparison);
            progressPending = itemView.findViewById(R.id.progressComparisonPending);
        }

        public void bind(ChatMessage message, Markwon markwon) {
            contents.clear();
            tabLayout.removeAllTabs();
            
            // Handle pending state
            if (progressPending != null) {
                progressPending.setVisibility(message.isPending ? View.VISIBLE : View.GONE);
            }
            if (message.isPending) {
                tvContent.setVisibility(View.GONE);
                btnCopy.setVisibility(View.GONE);
                return;
            } else {
                tvContent.setVisibility(View.VISIBLE);
                btnCopy.setVisibility(View.VISIBLE);
            }
            
            try {
                JSONObject json = new JSONObject(message.content);
                
                // Handle new format: {"comparisons": [{"model": "...", "content": "..."}]}
                if (json.has("comparisons")) {
                    JSONArray comparisons = json.getJSONArray("comparisons");
                    for (int i = 0; i < comparisons.length(); i++) {
                        JSONObject item = comparisons.getJSONObject(i);
                        String model = item.optString("model", "Model " + (i + 1));
                        String content = item.optString("content", "");
                        
                        tabLayout.addTab(tabLayout.newTab().setText(model));
                        contents.add(content);
                    }
                } 
                // Handle legacy format: {modelA: ..., contentA: ..., modelB: ..., contentB: ...}
                else {
                    String modelA = json.optString("modelA", "Model A");
                    String contentA = json.optString("contentA", "");
                    tabLayout.addTab(tabLayout.newTab().setText(modelA));
                    contents.add(contentA);

                    if (json.has("modelB")) {
                        String modelB = json.optString("modelB", "Model B");
                        String contentB = json.optString("contentB", "");
                        tabLayout.addTab(tabLayout.newTab().setText(modelB));
                        contents.add(contentB);
                    }
                }

                // Clear existing listeners to avoid multiple calls
                tabLayout.clearOnTabSelectedListeners();
                tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        updateContent(tab.getPosition(), markwon);
                    }
                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}
                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });

                // Show content for currently selected tab (default 0)
                if (tabLayout.getTabCount() > 0) {
                    TabLayout.Tab firstTab = tabLayout.getTabAt(0);
                    if (firstTab != null) firstTab.select();
                    updateContent(0, markwon);
                }

            } catch (Exception e) {
                tvContent.setText("Error parsing comparison data: " + e.getMessage());
            }
        }

        private void updateContent(int position, Markwon markwon) {
            if (position >= 0 && position < contents.size()) {
                String content = contents.get(position);
                if (TextUtils.isEmpty(content)) {
                    tvContent.setText("Loading...");
                    btnCopy.setEnabled(false);
                    btnCopy.setOnClickListener(null);
                } else {
                    markwon.setMarkdown(tvContent, content);
                    btnCopy.setEnabled(true);
                    btnCopy.setOnClickListener(v -> copyToClipboard(R.string.chat_copy_label_answer, content));
                }
            }
        }
    }

    private void copyToClipboard(@StringRes int labelRes, String text) {
        if (clipboardManager == null || text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String label = appContext.getString(labelRes);
        ClipData clip = ClipData.newPlainText(label, trimmed);
        clipboardManager.setPrimaryClip(clip);
        String toastText = appContext.getString(R.string.chat_copy_success, label);
        android.widget.Toast.makeText(appContext, toastText, android.widget.Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 预处理LaTeX公式格式
     */
    private String preprocessLatexFormulas(String content) {
        try {
            StringBuilder result = new StringBuilder();
            int i = 0;
            while (i < content.length()) {
                // 检查块级公式 \[
                if (i < content.length() - 1 && content.charAt(i) == '\\' && content.charAt(i + 1) == '[') {
                    result.append("$$");
                    i += 2;
                }
                // 检查块级公式 \]
                else if (i < content.length() - 1 && content.charAt(i) == '\\' && content.charAt(i + 1) == ']') {
                    result.append("$$");
                    i += 2;
                }
                // 检查行内公式 \(
                else if (i < content.length() - 1 && content.charAt(i) == '\\' && content.charAt(i + 1) == '(') {
                    result.append("$");
                    i += 2;
                }
                // 检查行内公式 \)
                else if (i < content.length() - 1 && content.charAt(i) == '\\' && content.charAt(i + 1) == ')') {
                    result.append("$");
                    i += 2;
                }
                else {
                    result.append(content.charAt(i));
                    i++;
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            return content;
        }
    }
}
