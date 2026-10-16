package com.free.hark.token;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 官方 Token 额度追踪与持久化管理器 (TokenTracker)
 * 实时监控各账号官方剩余配额与重置时间，杜绝虚假宣传。
 */
public class TokenTracker {

    private static final String PREF_NAME = "hark_tokens";
    private final SharedPreferences prefs;

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
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSlotUsage(String slotId, long used, long limit, String resetsAt) {
        prefs.edit()
                .putLong(slotId + "_used", used)
                .putLong(slotId + "_limit", limit)
                .putString(slotId + "_reset", resetsAt)
                .putLong(slotId + "_time", System.currentTimeMillis())
                .apply();
    }

    public UsageInfo getSlotUsage(String slotId) {
        UsageInfo info = new UsageInfo();
        info.dailyUsed = prefs.getLong(slotId + "_used", 0);
        info.dailyLimit = prefs.getLong(slotId + "_limit", 0);
        info.resetsAt = prefs.getString(slotId + "_reset", "");
        info.updateTime = prefs.getLong(slotId + "_time", 0);
        return info;
    }
}
