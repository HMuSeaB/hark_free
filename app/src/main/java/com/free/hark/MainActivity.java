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

    private TextView tvUsage0, tvUsage1, tvUsage2, tvUsage3;
    private ProgressBar pbUsage0, pbUsage1, pbUsage2, pbUsage3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tokenTracker = new TokenTracker(this);
        httpServer = new LocalHttpServer(this, 8080);

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
        btnToggleServer = findViewById(R.id.btn_toggle_server);
        cardServerStatus = findViewById(R.id.card_server_status);
        tvServerUrl = findViewById(R.id.tv_server_url);

        cardSlot0.setOnClickListener(v -> launchSlot(SlotActivity.Slot0.class, "账号槽位 1", "slot0"));
        cardSlot1.setOnClickListener(v -> launchSlot(SlotActivity.Slot1.class, "账号槽位 2", "slot1"));
        cardSlot2.setOnClickListener(v -> launchSlot(SlotActivity.Slot2.class, "账号槽位 3", "slot2"));
        cardSlot3.setOnClickListener(v -> launchSlot(SlotActivity.Slot3.class, "账号槽位 4", "slot3"));

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
        startActivity(intent);
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
