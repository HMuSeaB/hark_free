package com.free.hark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台保活服务 (KeepAliveService)
 * 互传/隧道运行期间持有一个前台通知,防止系统回收进程导致断线。
 */
public class KeepAliveService extends Service {

    private static final String CH_ID = "hark_keepalive";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CH_ID, "HarkFree 服务", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CH_ID)
                : new Notification.Builder(this);
        b.setContentTitle("HarkFree 互传运行中")
                .setContentText("前台服务保持连接,互传与桥接可用")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true);
        startForeground(1001, b.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
