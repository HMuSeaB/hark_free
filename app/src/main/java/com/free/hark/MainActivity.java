package com.free.hark;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.free.hark.linux.TermActivity;
import com.free.hark.server.LocalHttpServer;
import com.free.hark.token.TokenTracker;
import com.free.hark.tunnel.TunnelManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * HarkFree 全功能极客仪表盘 (MainActivity)
 * 集成 4 独立进程槽位、局域网 Web 极速互传服务器与 Alpine Linux 容器终端。
 */
public class MainActivity extends AppCompatActivity {

    private LocalHttpServer httpServer;
    private MaterialButton btnToggleServer;
    private MaterialCardView cardServerStatus;
    private TextView tvServerUrl;
    private TokenTracker tokenTracker;
    private TunnelManager tunnelManager;

    private TextView tvUsage0, tvUsage1, tvUsage2, tvUsage3;
    private ProgressBar pbUsage0, pbUsage1, pbUsage2, pbUsage3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tokenTracker = new TokenTracker(this);
        httpServer = new LocalHttpServer(this, 8080);
        tunnelManager = new TunnelManager(this);

        MaterialCardView cardSlot0 = findViewById(R.id.card_slot0);
        MaterialCardView cardSlot1 = findViewById(R.id.card_slot1);
        MaterialCardView cardSlot2 = findViewById(R.id.card_slot2);
        MaterialCardView cardSlot3 = findViewById(R.id.card_slot3);

        tvUsage0 = findViewById(R.id.tv_usage_0);
        tvUsage1 = findViewById(R.id.tv_usage_1);
        tvUsage2 = findViewById(R.id.tv_usage_2);
        tvUsage3 = findViewById(R.id.tv_usage_3);

        pbUsage0 = findViewById(R.id.pb_usage_0);
        pbUsage1 = findViewById(R.id.pb_usage_1);
        pbUsage2 = findViewById(R.id.pb_usage_2);
        pbUsage3 = findViewById(R.id.pb_usage_3);

        MaterialButton btnOpenTerm = findViewById(R.id.btn_open_term);
        MaterialButton btnOpenMail = findViewById(R.id.btn_open_mail);
        btnToggleServer = findViewById(R.id.btn_toggle_server);
        cardServerStatus = findViewById(R.id.card_server_status);
        tvServerUrl = findViewById(R.id.tv_server_url);

        btnOpenMail.setOnClickListener(v -> startActivity(new Intent(this, com.free.hark.mail.MailActivity.class)));

        cardSlot0.setOnClickListener(v -> launchSlot(SlotActivity.Slot0.class, "账号槽位 1", "slot0"));
        cardSlot1.setOnClickListener(v -> launchSlot(SlotActivity.Slot1.class, "账号槽位 2", "slot1"));
        cardSlot2.setOnClickListener(v -> launchSlot(SlotActivity.Slot2.class, "账号槽位 3", "slot2"));
        cardSlot3.setOnClickListener(v -> launchSlot(SlotActivity.Slot3.class, "账号槽位 4", "slot3"));

        // 长按槽位卡片:自定义该槽位的启动 URL(默认仍为 hark.com)
        cardSlot0.setOnLongClickListener(v -> { showSlotUrlDialog("slot0", "账号槽位 1"); return true; });
        cardSlot1.setOnLongClickListener(v -> { showSlotUrlDialog("slot1", "账号槽位 2"); return true; });
        cardSlot2.setOnLongClickListener(v -> { showSlotUrlDialog("slot2", "账号槽位 3"); return true; });
        cardSlot3.setOnLongClickListener(v -> { showSlotUrlDialog("slot3", "账号槽位 4"); return true; });

        // 终端入口
        btnOpenTerm.setOnClickListener(v -> {
            Intent intent = new Intent(this, TermActivity.class);
            startActivity(intent);
        });

        // 本地 Web 互传开关
        btnToggleServer.setOnClickListener(v -> toggleServer());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsageDisplay();
    }

    private void launchSlot(Class<?> slotClass, String slotName, String slotId) {
        Intent intent = new Intent(this, slotClass);
        intent.putExtra("SLOT_NAME", slotName);
        intent.putExtra("SLOT_ID", slotId);
        // 槽位自定义启动 URL:用户长按卡片设置,留空则回落 hark.com
        String customUrl = slotUrl(slotId);
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            intent.putExtra("SLOT_URL", customUrl.trim());
        }
        startActivity(intent);
    }

    /** 读取槽位自定义启动 URL(SharedPreferences "slot_urls",默认空 = 使用 hark.com) */
    private String slotUrl(String slotId) {
        return getSharedPreferences("slot_urls", MODE_PRIVATE).getString(slotId, "");
    }

    /** 长按槽位卡片弹窗:设置/清除该槽位的自定义启动 URL */
    private void showSlotUrlDialog(String slotId, String slotName) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://example.com(留空并保存 = 恢复默认)");
        input.setText(slotUrl(slotId));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("自定义启动 URL · " + slotName)
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }
                    getSharedPreferences("slot_urls", MODE_PRIVATE).edit().putString(slotId, url).apply();
                    Toast.makeText(this, url.isEmpty() ? "已清除,下次进入恢复 hark.com" : "已保存,下次进入槽位生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("恢复默认", (d, w) -> {
                    getSharedPreferences("slot_urls", MODE_PRIVATE).edit().remove(slotId).apply();
                    Toast.makeText(this, "已恢复默认(hark.com)", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void refreshUsageDisplay() {
        updateCardUsage("slot0", tvUsage0, pbUsage0, "独立进程 :slot0");
        updateCardUsage("slot1", tvUsage1, pbUsage1, "独立进程 :slot1");
        updateCardUsage("slot2", tvUsage2, pbUsage2, "独立进程 :slot2");
        updateCardUsage("slot3", tvUsage3, pbUsage3, "独立进程 :slot3");
    }

    private void updateCardUsage(String slotId, TextView tv, ProgressBar pb, String defaultPrefix) {
        TokenTracker.UsageInfo info = tokenTracker.getSlotUsage(slotId);
        if (info.dailyLimit > 0) {
            tv.setText(info.getSummaryText());
            pb.setProgress(info.getPercentage());
        } else {
            tv.setText(defaultPrefix + " · 点击进入同步额度");
            pb.setProgress(0);
        }
    }

    private void toggleServer() {
        if (httpServer.isRunning()) {
            httpServer.stop();
            btnToggleServer.setText("启动互传");
            cardServerStatus.setVisibility(View.GONE);
            stopService(new Intent(this, KeepAliveService.class));
            tunnelManager.stopTunnel();
            Toast.makeText(this, "局域网互传服务已关闭", Toast.LENGTH_SHORT).show();
        } else {
            try {
                httpServer.start();
                String ip = getLocalIpAddress();
                String url = "http://" + ip + ":8080/?t=" + httpServer.getAccessToken();
                tvServerUrl.setText("电脑浏览器打开: " + url);
                cardServerStatus.setVisibility(View.VISIBLE);
            btnToggleServer.setText("关闭互传");
            Toast.makeText(this, "服务已启动: " + url, Toast.LENGTH_LONG).show();
            // 前台保活:防止系统回收进程断线
            startForegroundService(new Intent(this, KeepAliveService.class));
            // 引导加入电池优化白名单(对标 HarkMulti"取消电池优化防断线")
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + getPackageName())));
                }
            } catch (Exception ignored) {}
            // Cloudflare 隧道:把互传/控制台发布到公网,Agent 可从任何网络连入(令牌保护仍生效)
            tunnelManager.startTunnel(8080, new TunnelManager.TunnelCallback() {
                @Override
                public void onUrlResolved(String publicUrl) {
                    runOnUiThread(() -> tvServerUrl.append("\n公网桥接: " + publicUrl + "/?t=" + httpServer.getAccessToken()));
                }
                @Override
                public void onStopped() {}
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "公网桥接失败: " + error, Toast.LENGTH_SHORT).show());
                }
            });
            } catch (Exception e) {
                Toast.makeText(this, "启动服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (httpServer != null && httpServer.isRunning()) {
            httpServer.stop();
        }
    }
}
