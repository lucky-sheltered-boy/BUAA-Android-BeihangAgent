package com.example.beihangagent.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.example.beihangagent.R;
import com.example.beihangagent.databinding.ActivityHelpBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HelpActivity extends AppCompatActivity {
    
    private ActivityHelpBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        initViews();
        openPDF();
    }
    
    private void initViews() {
        // 设置标题栏
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("帮助中心");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // 返回按钮
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        // 重新加载按钮
        binding.btnReload.setOnClickListener(v -> openPDF());
    }
    
    private void openPDF() {
        try {
            binding.progressBar.setVisibility(android.view.View.VISIBLE);
            
            // 将assets中的PDF复制到缓存目录
            File pdfFile = copyPdfToCache();
            
            if (pdfFile != null && pdfFile.exists()) {
                // 使用FileProvider获取URI
                Uri pdfUri = FileProvider.getUriForFile(this, 
                    getPackageName() + ".fileprovider", pdfFile);
                
                // 创建Intent打开PDF
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(pdfUri, "application/pdf");
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                // 检查是否有应用可以打开PDF
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    // PDF打开后关闭当前页面
                    finish();
                } else {
                    // 没有PDF查看器，显示提示
                    showNoPdfViewerDialog();
                }
            } else {
                Toast.makeText(this, "无法加载帮助文档", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开文档时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            binding.progressBar.setVisibility(android.view.View.GONE);
        }
    }
    
    private File copyPdfToCache() {
        try {
            // 从assets复制PDF到缓存目录
            InputStream inputStream = getAssets().open("help_manual.pdf");
            File cacheDir = new File(getCacheDir(), "help");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File pdfFile = new File(cacheDir, "help_manual.pdf");
            FileOutputStream outputStream = new FileOutputStream(pdfFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            outputStream.close();
            inputStream.close();
            
            return pdfFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void showNoPdfViewerDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("需要PDF查看器")
            .setMessage("您的设备上没有PDF查看器应用。请安装一个PDF阅读器（如Adobe Reader、WPS等）来查看帮助文档。")
            .setPositiveButton("去应用商店", (dialog, which) -> {
                try {
                    // 打开应用商店搜索PDF阅读器
                    Intent intent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse("market://search?q=pdf reader"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开应用商店", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}