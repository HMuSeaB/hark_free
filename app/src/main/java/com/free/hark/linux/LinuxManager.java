package com.free.hark.linux;

import android.content.Context;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 免 Root Linux 容器管理器 (LinuxManager)
 * 基于开源 PRoot 引擎，在 Android 沙盒内提供完整的 Alpine Linux 运行环境。
 */
public class LinuxManager {

    private final Context context;
    private final File rootfsDir;
    private final String nativeLibDir;

    // 清华大学开源镜像站 Alpine aarch64 极简 Rootfs (~3.5MB)
    private static final String ALPINE_MIRROR = "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz";

    public interface ProgressCallback {
        void onProgress(String message, int percentage);
        void onSuccess();
        void onError(String error);
    }

    public LinuxManager(Context context) {
        this.context = context;
        this.rootfsDir = new File(context.getFilesDir(), "rootfs_alpine");
        this.nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
    }

    public boolean isInstalled() {
        File binSh = new File(rootfsDir, "bin/sh");
        return binSh.exists() && binSh.canExecute();
    }

    public File getRootfsDir() {
        return rootfsDir;
    }

    /**
     * 一键下载并解压 Alpine Linux Rootfs
     */
    public void installAlpine(ProgressCallback callback) {
        new Thread(() -> {
            try {
                if (!rootfsDir.exists()) {
                    rootfsDir.mkdirs();
                }

                callback.onProgress("正在从清华大学镜像源下载 Alpine Linux...", 20);
                File tarFile = new File(context.getCacheDir(), "alpine-rootfs.tar.gz");

                URL url = new URL(ALPINE_MIRROR);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                long total = conn.getContentLengthLong();
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream out = new FileOutputStream(tarFile)) {
                    byte[] buf = new byte[8192];
                    int r;
                    long downloaded = 0;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                        downloaded += r;
                        if (total > 0) {
                            int pct = 20 + (int) ((downloaded * 60) / total);
                            callback.onProgress("下载中: " + (downloaded / 1024) + " KB", pct);
                        }
                    }
                }

                callback.onProgress("下载完成，正在解包 rootfs...", 85);
                // 解压 tar.gz
                Process p = Runtime.getRuntime().exec(new String[]{"tar", "-xzf", tarFile.getAbsolutePath(), "-C", rootfsDir.getAbsolutePath()});
                p.waitFor();

                tarFile.delete();

                // 配置 resolv.conf (DNS解析)
                File etcDir = new File(rootfsDir, "etc");
                if (etcDir.exists()) {
                    File resolv = new File(etcDir, "resolv.conf");
                    try (FileOutputStream fos = new FileOutputStream(resolv)) {
                        fos.write("nameserver 8.8.8.8\nnameserver 1.1.1.1\n".getBytes());
                    }
                }

                callback.onProgress("Alpine Linux 安装成功！", 100);
                callback.onSuccess();

            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("安装失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 构建 PRoot ProcessBuilder，执行指定命令
     */
    public ProcessBuilder buildPRootProcess(String... commands) {
        File prootBin = new File(nativeLibDir, "libproot.so");
        File prootLoader = new File(nativeLibDir, "libproot-loader.so");

        String[] baseCmd = new String[]{
                prootBin.getAbsolutePath(),
                "-r", rootfsDir.getAbsolutePath(),
                "-0",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", "/root"
        };

        String[] fullCmd;
        if (commands == null || commands.length == 0) {
            fullCmd = new String[baseCmd.length + 1];
            System.arraycopy(baseCmd, 0, fullCmd, 0, baseCmd.length);
            fullCmd[baseCmd.length] = "/bin/sh";
        } else {
            fullCmd = new String[baseCmd.length + commands.length];
            System.arraycopy(baseCmd, 0, fullCmd, 0, baseCmd.length);
            System.arraycopy(commands, 0, fullCmd, baseCmd.length, commands.length);
        }

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        Map<String, String> env = pb.environment();
        env.put("PROOT_LOADER", prootLoader.getAbsolutePath());
        env.put("PROOT_TMP_DIR", context.getCacheDir().getAbsolutePath());
        env.put("HOME", "/root");
        env.put("USER", "root");
        env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.put("TERM", "xterm-256color");

        return pb;
    }
}
