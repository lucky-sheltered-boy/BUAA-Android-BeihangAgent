package com.example.beihangagent.view.fragment;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.FragmentAnalysisBinding;
import com.example.beihangagent.model.QuestionStat;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.view.base.BaseFragment;
import com.example.beihangagent.view.adapter.HotspotAdapter;
import com.example.beihangagent.viewmodel.AnalysisViewModel;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import java.util.ArrayList;
import java.util.List;

public class AnalysisFragment extends BaseFragment<FragmentAnalysisBinding> {

    private AnalysisViewModel viewModel;
    private HotspotAdapter hotspotAdapter;
    private ArrayAdapter<ClassSpinnerItem> classSpinnerAdapter;
    private List<Class> teacherClasses = new ArrayList<>();

    @Override
    protected FragmentAnalysisBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentAnalysisBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initViews() {
        viewModel = new ViewModelProvider(this).get(AnalysisViewModel.class);
        hotspotAdapter = new HotspotAdapter(requireContext());
        binding.rvHotspots.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHotspots.setAdapter(hotspotAdapter);
        
        binding.btnEnterForum.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new ClassSelectionFragment())
                .addToBackStack(null)
                .commit();
        });

        configureChart();
        setupClassSpinner();
    }

    @Override
    protected void initObservers() {
        viewModel.getStats().observe(getViewLifecycleOwner(), this::renderStats);
        viewModel.getHotspots().observe(getViewLifecycleOwner(), hotspots -> {
            hotspotAdapter.submitList(hotspots);
            boolean empty = hotspots == null || hotspots.isEmpty();
            binding.tvHotspotEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvHotspots.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
        
        // For teachers, observe their classes
        if (viewModel.isTeacher()) {
            viewModel.getTeacherClasses().observe(getViewLifecycleOwner(), this::updateClassSpinner);
        }
    }

    private void configureChart() {
        binding.pieChart.getDescription().setEnabled(false);
        binding.pieChart.setEntryLabelColor(Color.BLACK);
        binding.pieChart.setUsePercentValues(true);
        binding.pieChart.setCenterText("AI 助教提问占比");
        binding.pieChart.setHoleColor(Color.TRANSPARENT);
        binding.pieChart.setNoDataText(""); // 禁用默认的无数据文本
    }

    private void renderStats(List<QuestionStat> stats) {
        if (stats == null || stats.isEmpty()) {
            binding.pieChart.clear();
            binding.pieChart.invalidate();
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvEmpty.setVisibility(View.GONE);

        // 计算总数
        int totalCount = 0;
        for (QuestionStat stat : stats) {
            totalCount += stat.count;
        }
        final int finalTotalCount = totalCount; // 创建final变量供内部类使用

        List<PieEntry> entries = new ArrayList<>();
        for (QuestionStat stat : stats) {
            // 所有条目都使用完整标签，确保图例显示完整
            entries.add(new PieEntry(stat.count, stat.topic));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        
        // 自定义丰富的颜色数组
        int[] colors = {
                Color.parseColor("#FF6B6B"), // 红色
                Color.parseColor("#4ECDC4"), // 青色
                Color.parseColor("#45B7D1"), // 蓝色
                Color.parseColor("#96CEB4"), // 绿色
                Color.parseColor("#FFEAA7"), // 黄色
                Color.parseColor("#DDA0DD"), // 紫色
                Color.parseColor("#FFB347"), // 橙色
                Color.parseColor("#87CEEB"), // 天蓝色
                Color.parseColor("#98FB98"), // 浅绿色
                Color.parseColor("#F0E68C"), // 卡其色
                Color.parseColor("#FFB6C1"), // 浅粉色
                Color.parseColor("#20B2AA"), // 浅海绿色
                Color.parseColor("#9370DB"), // 中紫色
                Color.parseColor("#FF7F50"), // 珊瑚色
                Color.parseColor("#6495ED"), // 矢车菊蓝
                Color.parseColor("#90EE90"), // 浅绿色
                Color.parseColor("#FFE4B5"), // 鹿皮色
                Color.parseColor("#B0C4DE"), // 浅钢蓝色
                Color.parseColor("#FFA07A"), // 浅鲑鱼色
                Color.parseColor("#00CED1")  // 深绿松石色
        };
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(14f);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        binding.pieChart.setData(data);
        
        // 启用图例
        binding.pieChart.getLegend().setEnabled(true);
        binding.pieChart.getLegend().setWordWrapEnabled(true);
        
        // 禁用扇形标签（种类名称）
        binding.pieChart.setDrawEntryLabels(false);
        
        // 启用百分比显示，只有占比大于5%的才显示百分比
        binding.pieChart.setUsePercentValues(true);
        data.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // value 是百分比值
                if (value > 5.0f) {
                    return String.format("%.1f%%", value);
                }
                return ""; // 占比小于等于5%不显示百分比
            }
        });
        
        // 设置百分比数值样式
        data.setValueTextSize(12f);
        data.setValueTextColor(Color.BLACK);
        
        binding.pieChart.animateY(800);
        binding.pieChart.invalidate();
    }
    
    private void setupClassSpinner() {
        if (!viewModel.isTeacher()) {
            binding.spinnerClass.setVisibility(View.GONE);
            return;
        }
        
        classSpinnerAdapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_spinner_item, new ArrayList<>());
        classSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerClass.setAdapter(classSpinnerAdapter);
        
        binding.spinnerClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ClassSpinnerItem item = classSpinnerAdapter.getItem(position);
                if (item != null) {
                    if (item.classId == -1) {
                        viewModel.selectAllClasses();
                    } else {
                        viewModel.selectClass(item.classId);
                    }
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void updateClassSpinner(List<Class> classes) {
        if (!viewModel.isTeacher()) {
            return;
        }
        
        teacherClasses = classes;
        classSpinnerAdapter.clear();
        
        // Add "All Classes" option
        classSpinnerAdapter.add(new ClassSpinnerItem(-1, getString(R.string.analysis_class_all)));
        
        // Add individual classes
        for (Class clazz : classes) {
            classSpinnerAdapter.add(new ClassSpinnerItem(clazz.classId, clazz.className));
        }
        
        classSpinnerAdapter.notifyDataSetChanged();
    }
    
    // Inner class for spinner items
    private static class ClassSpinnerItem {
        final int classId;
        final String className;
        
        ClassSpinnerItem(int classId, String className) {
            this.classId = classId;
            this.className = className;
        }
        
        @Override
        public String toString() {
            return className;
        }
    }
}
