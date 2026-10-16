package com.free.hark.linux;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.free.hark.R;
import com.google.android.material.chip.Chip;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 极客内置 Linux 终端交互界面 (TermActivity)
 * 允许用户在手机免 Root PRoot Alpine 容器中直接执行命令、调试 Python 脚本。
 */
public class TermActivity extends AppCompatActivity {

    private LinuxManager linuxManager;
    private TextView tvOutput;
    private ScrollView svOutput;
    private EditText etCmd;
    private ProgressBar pbInstall;
    private Button btnInstall;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_term);

        linuxManager = new LinuxManager(this);

        ImageButton btnBack = findViewById(R.id.btn_term_back);
        btnInstall = findViewById(R.id.btn_install_linux);
        pbInstall = findViewById(R.id.pb_install);
        tvOutput = findViewById(R.id.tv_term_output);
        svOutput = findViewById(R.id.sv_output);
        etCmd = findViewById(R.id.et_term_cmd);
        Button btnSend = findViewById(R.id.btn_term_send);

        btnBack.setOnClickListener(v -> finish());

        checkStatus();

        btnInstall.setOnClickListener(v -> installAlpine());
        btnSend.setOnClickListener(v -> {
            String cmd = etCmd.getText().toString().trim();
            if (!cmd.isEmpty()) {
                runCommand(cmd);
                etCmd.setText("");
            }
        });

        // 快捷命令
        findViewById(R.id.chip_uname).setOnClickListener(v -> runCommand("uname -a"));
        findViewById(R.id.chip_update).setOnClickListener(v -> runCommand("apk update"));
        findViewById(R.id.chip_python).setOnClickListener(v -> runCommand("apk add python3 py3-pip"));
        findViewById(R.id.chip_df).setOnClickListener(v -> runCommand("df -h"));
    }

    private void checkStatus() {
        if (linuxManager.isInstalled()) {
            btnInstall.setVisibility(View.GONE);
            appendOutput("=== Alpine Linux 极客环境已就绪 ===\n输入命令或点击快捷命令即可在容器中执行。\n\n");
        } else {
            btnInstall.setVisibility(View.VISIBLE);
            appendOutput("=== 尚未安装 Alpine Linux ===\n点击右上角「一键安装」即可从清华大学镜像源下载极简 rootfs (~3.5MB)。\n\n");
        }
    }

    private void installAlpine() {
        pbInstall.setVisibility(View.VISIBLE);
        pbInstall.setProgress(0);
        btnInstall.setEnabled(false);

        linuxManager.installAlpine(new LinuxManager.ProgressCallback() {
            @Override
            public void onProgress(String message, int percentage) {
                mainHandler.post(() -> {
                    appendOutput("[安装进度] " + message + "\n");
                    pbInstall.setProgress(percentage);
                });
            }

            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    pbInstall.setVisibility(View.GONE);
                    btnInstall.setVisibility(View.GONE);
                    Toast.makeText(TermActivity.this, "Linux 容器安装完成！", Toast.LENGTH_SHORT).show();
                    appendOutput("\n[就绪] Alpine Linux 环境配置完毕，输入命令即可开始使用！\n\n");
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    pbInstall.setVisibility(View.GONE);
                    btnInstall.setEnabled(true);
                    appendOutput("[错误] " + error + "\n");
                    Toast.makeText(TermActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void runCommand(String cmd) {
        if (!linuxManager.isInstalled()) {
            Toast.makeText(this, "请先点击右上角安装 Alpine Linux 环境", Toast.LENGTH_SHORT).show();
            return;
        }

        appendOutput("# " + cmd + "\n");
        new Thread(() -> {
            try {
                ProcessBuilder pb = linuxManager.buildPRootProcess("/bin/sh", "-c", cmd);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String outLine = line;
                        mainHandler.post(() -> appendOutput(outLine + "\n"));
                    }
                }
                process.waitFor();
                mainHandler.post(() -> appendOutput("\n"));
            } catch (Exception e) {
                mainHandler.post(() -> appendOutput("[执行异常]: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    private void appendOutput(String text) {
        tvOutput.append(text);
        svOutput.post(() -> svOutput.fullScroll(View.FOCUS_DOWN));
    }
}
