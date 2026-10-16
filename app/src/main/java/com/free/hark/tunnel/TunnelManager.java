package com.free.hark.tunnel;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 官方 Cloudflare 穿透隧道管理器 (TunnelManager)
 * 允许用户自填 Cloudflare Zero Trust Token 或使用官方临时隧道，将本地 Web/互传发布至公网。
 */
public class TunnelManager {

    private static final String PREF_NAME = "hark_tunnel";
    private final Context context;
    private final SharedPreferences prefs;
    private Process tunnelProcess;
    private String publicUrl = "";

    public interface TunnelCallback {
        void onUrlResolved(String url);
        void onStopped();
        void onError(String error);
    }

    public TunnelManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveCustomToken(String token) {
        prefs.edit().putString("custom_cf_token", token.trim()).apply();
    }

    public String getCustomToken() {
        return prefs.getString("custom_cf_token", "");
    }

    public boolean isRunning() {
        return tunnelProcess != null && tunnelProcess.isAlive();
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    /**
     * 启动隧道打洞
     */
    public void startTunnel(int localPort, TunnelCallback callback) {
        if (isRunning()) {
            stopTunnel();
        }

        new Thread(() -> {
            try {
                String token = getCustomToken();
                String cloudflaredBin = context.getApplicationInfo().nativeLibraryDir + "/libcloudflared.so";

                ProcessBuilder pb;
                if (!token.isEmpty()) {
                    // 使用用户自己的 Cloudflare Zero Trust 隧道
                    pb = new ProcessBuilder(cloudflaredBin, "tunnel", "run", "--token", token);
                } else {
                    // 使用官方 Quick Tunnel 临时打洞
                    pb = new ProcessBuilder(cloudflaredBin, "tunnel", "--url", "http://localhost:" + localPort, "--no-autoupdate");
                }

                pb.redirectErrorStream(true);
                tunnelProcess = pb.start();

                Pattern urlPattern = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = urlPattern.matcher(line);
                        if (matcher.find()) {
                            publicUrl = matcher.group();
                            callback.onUrlResolved(publicUrl);
                        }
                    }
                }

                tunnelProcess.waitFor();
                callback.onStopped();

            } catch (Exception e) {
                callback.onError("启动隧道异常: " + e.getMessage());
            }
        }).start();
    }

    public void stopTunnel() {
        if (tunnelProcess != null) {
            tunnelProcess.destroy();
            tunnelProcess = null;
        }
        publicUrl = "";
    }
}
