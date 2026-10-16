package com.free.hark;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 原生安全通信桥 (HarkNativeBridge)
 * 处理来自 WebView 的文件导出与操作通知，安全保存至系统 Download 目录。
 */
public class HarkNativeBridge {

    private final Activity activity;

    public HarkNativeBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void onExportProgress(String message) {
        activity.runOnUiThread(() -> {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        });
    }

    @JavascriptInterface
    public void onExportSuccess(String filename, String content, int count) {
        boolean saved = saveToDownloads(filename, content);
        activity.runOnUiThread(() -> {
            if (saved) {
                Toast.makeText(activity, "导出成功！已保存至 Download/HarkFree/" + filename, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "保存文件失败，请检查存储权限", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void onExportFailed(String error) {
        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "导出失败: " + error, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * 将 Markdown 文本安全保存到系统 Download 目录
     */
    private boolean saveToDownloads(String filename, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/markdown");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HarkFree");

                Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }

                try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(content.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        return true;
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HarkFree");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
