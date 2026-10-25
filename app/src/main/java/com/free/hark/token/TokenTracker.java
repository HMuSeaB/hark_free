package com.free.hark.token;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 官方 Token 额度追踪与持久化管理器 (TokenTracker)
 * 跨进程方案:槽位进程写 JSON 文件(原子替换),主进程每次读取都直接走磁盘,
 * 彻底解决 SharedPreferences 多进程内存缓存导致的"永远显示 0%"。
 */
public class TokenTracker {

    private final File storeFile;

    public static class UsageInfo {
        public long dailyLimit = 0;
        public long dailyUsed = 0;
        public String resetsAt = "";
        public long updateTime = 0;

        public int getPercentage() {
            if (dailyLimit <= 0) return 0;
            return (int) Math.min(100, (dailyUsed * 100) / dailyLimit);
        }

        public String getSummaryText() {
            if (dailyLimit <= 0) return "尚未获取额度（点击进入同步）";
            return String.format("今日已用: %d%% (%d / %d) · 重置于 %s",
                    getPercentage(), dailyUsed, dailyLimit, resetsAt.isEmpty() ? "次日" : resetsAt);
        }
    }

    public TokenTracker(Context context) {
        this.storeFile = new File(context.getFilesDir(), "token_usage.json");
    }

    public synchronized void saveSlotUsage(String slotId, long used, long limit, String resetsAt) {
        try {
            JSONObject root = readAll();
            root.put(slotId + "_used", used);
            root.put(slotId + "_limit", limit);
            root.put(slotId + "_reset", resetsAt == null ? "" : resetsAt);
            root.put(slotId + "_time", System.currentTimeMillis());
            File tmp = new File(storeFile.getParentFile(), storeFile.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            if (!tmp.renameTo(storeFile)) {
                storeFile.delete();
                tmp.renameTo(storeFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public UsageInfo getSlotUsage(String slotId) {
        UsageInfo info = new UsageInfo();
        try {
            JSONObject root = readAll();
            info.dailyUsed = root.optLong(slotId + "_used", 0);
            info.dailyLimit = root.optLong(slotId + "_limit", 0);
            info.resetsAt = root.optString(slotId + "_reset", "");
            info.updateTime = root.optLong(slotId + "_time", 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return info;
    }

    private JSONObject readAll() {
        try {
            if (storeFile.exists()) {
                return new JSONObject(new String(Files.readAllBytes(storeFile.toPath()), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
        return new JSONObject();
    }
}
