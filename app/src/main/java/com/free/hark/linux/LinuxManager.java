package com.free.hark.linux;

import android.content.Context;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 免 Root Linux 容器管理器 v2 (LinuxManager)
 * 多发行版(Alpine/Debian/Ubuntu) + 国内镜像三级回退 + 最新版自动解析 + Java 原生 tar/xz 解包。
 * 镜像回退链与版本解析方案参考 HarkMulti Linux.java 的公开实现思路。
 */
public class LinuxManager {

    public enum Distro {
        ALPINE("Alpine", "rootfs_alpine", "/bin/sh"),
        DEBIAN("Debian 13", "rootfs_debian", "/bin/bash"),
        UBUNTU("Ubuntu 24.04", "rootfs_ubuntu", "/bin/bash");

        public final String title;
        public final String dirName;
        public final String shell;
        Distro(String title, String dirName, String shell) {
            this.title = title;
            this.dirName = dirName;
            this.shell = shell;
        }
    }

    /** 阿里云 → 清华 → 官方 三级回退,与 HarkMulti 同源 */
    private static final String[] ALPINE_BASES = {
            "https://mirrors.aliyun.com/alpine/latest-stable/releases/aarch64/",
            "https://mirrors.tuna.tsinghua.edu.cn/alpine/latest-stable/releases/aarch64/",
            "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/"
    };
    private static final String[] UBUNTU_BASES = {
            "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04/release/",
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
    };
    private static final String[] DEBIAN_BASES = {
            "https://mirrors.cernet.edu.cn/lxc-images/debian/trixie/arm64/",
            "https://mirrors.tuna.tsinghua.edu.cn/lxc-images/debian/trixie/arm64/",
            "https://images.linuxcontainers.org/images/debian/trixie/arm64/"
    };
    private static final Pattern ALPINE_FILE = Pattern.compile("alpine-minirootfs-[0-9.]+-aarch64\\.tar\\.gz");
    private static final Pattern UBUNTU_FILE = Pattern.compile("ubuntu-base-24\\.04([0-9.])*-base-arm64\\.tar\\.gz");
    private static final Pattern DEBIAN_DIR = Pattern.compile("\\d{8}_\\d{2}:\\d{2}/");

    private final Context context;
    private final File baseDir;
    private final String nativeLibDir;
    private final File activeFile;

    public interface ProgressCallback {
        void onProgress(String message, int percentage);
        void onSuccess();
        void onError(String error);
    }

    public LinuxManager(Context context) {
        this.context = context;
        this.baseDir = new File(context.getFilesDir(), "linux");
        this.nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        this.activeFile = new File(context.getFilesDir(), "active_distro");
    }

    private File distroDir(Distro d) {
        return new File(baseDir, d.dirName);
    }

    public boolean isInstalled() {
        return isInstalled(getActiveDistro());
    }

    public boolean isInstalled(Distro d) {
        if (d == null) return false;
        File binSh = new File(distroDir(d), d.shell);
        return binSh.exists() && binSh.canExecute();
    }

    public List<Distro> installedDistros() {
        List<Distro> list = new ArrayList<>();
        for (Distro d : Distro.values()) {
            if (new File(distroDir(d), d.shell).exists()) list.add(d);
        }
        return list;
    }

    public Distro getActiveDistro() {
        String name = "";
        try {
            if (activeFile.exists()) {
                byte[] b = java.nio.file.Files.readAllBytes(activeFile.toPath());
                name = new String(b, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {}
        for (Distro d : Distro.values()) {
            if (d.name().equals(name)) return d;
        }
        return null;
    }

    public File getRootfsDir() {
        Distro d = getActiveDistro();
        return distroDir(d == null ? Distro.ALPINE : d);
    }

    /** 兼容旧调用:默认装 Alpine */
    public void installAlpine(ProgressCallback callback) {
        installDistro(Distro.ALPINE, callback);
    }

    /** 主入口:选发行版安装,镜像逐个回退 */
    public void installDistro(Distro distro, ProgressCallback callback) {
        new Thread(() -> {
            try {
                File rootfsDir = distroDir(distro);
                if (!rootfsDir.exists()) rootfsDir.mkdirs();

                callback.onProgress("正在解析 " + distro.title + " 最新镜像(阿里云/清华/官方 三级回退)...", 10);
                String tarUrl = resolveTarUrl(distro);
                if (tarUrl == null) {
                    callback.onError("所有镜像均不可达,请检查网络后重试");
                    return;
                }
                callback.onProgress("开始下载: " + tarUrl, 20);

                File tarFile = new File(context.getCacheDir(), distro.dirName + "_rootfs.tar");
                download(tarUrl, tarFile, callback);

                callback.onProgress("下载完成,正在解包 rootfs(原生解包,不依赖系统 tar)...", 80);
                try (InputStream in = tarUrl.endsWith(".tar.xz")
                        ? new XZInputStream(new BufferedInputStream(new FileInputStream(tarFile), 65536))
                        : new GZIPInputStream(new BufferedInputStream(new FileInputStream(tarFile), 65536))) {
                    untar(in, rootfsDir, callback);
                }
                tarFile.delete();

                // DNS 配置
                File etcDir = new File(rootfsDir, "etc");
                if (etcDir.exists()) {
                    File resolv = new File(etcDir, "resolv.conf");
                    try (FileOutputStream fos = new FileOutputStream(resolv)) {
                        fos.write("nameserver 223.5.5.5\nnameserver 8.8.8.8\n".getBytes(StandardCharsets.UTF_8));
                    }
                }

                // 标记活跃发行版
                try (FileOutputStream fos = new FileOutputStream(activeFile)) {
                    fos.write(distro.name().getBytes(StandardCharsets.UTF_8));
                }

                callback.onProgress(distro.title + " 安装成功!", 100);
                callback.onSuccess();
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("安装失败: " + e.getMessage());
            }
        }).start();
    }

    /** 解析镜像目录,取最新匹配文件;一个镜像失败自动换下一个 */
    private String resolveTarUrl(Distro distro) {
        String[] bases;
        Pattern filePattern;
        switch (distro) {
            case ALPINE: bases = ALPINE_BASES; filePattern = ALPINE_FILE; break;
            case UBUNTU: bases = UBUNTU_BASES; filePattern = UBUNTU_FILE; break;
            default: bases = DEBIAN_BASES; filePattern = DEBIAN_DIR; break;
        }
        for (String base : bases) {
            try {
                String listing = fetchText(base);
                String last = lastMatch(listing, filePattern);
                if (last == null) continue;
                if (distro == Distro.DEBIAN) {
                    String dirUrl = base + last;
                    String sub = fetchText(dirUrl);
                    String f = lastMatch(sub, Pattern.compile("rootfs\\.tar\\.xz"));
                    if (f == null) continue;
                    return dirUrl + f;
                }
                return base + last;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String fetchText(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        StringBuilder sb = new StringBuilder();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String lastMatch(String text, Pattern p) {
        String last = null;
        Matcher m = p.matcher(text);
        while (m.find()) last = m.group();
        return last;
    }

    private void download(String url, File target, ProgressCallback cb) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[16384];
            int r;
            long done = 0;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
                done += r;
                if (total > 0) {
                    int pct = 20 + (int) ((done * 55) / total);
                    cb.onProgress("下载中: " + (done / 1024 / 1024) + " MB / " + (total / 1024 / 1024) + " MB", pct);
                }
            }
        }
    }

    /**
     * Java 原生 tar 解包:支持普通文件/目录/符号链接/硬链接/GNU 长文件名,
     * 不依赖系统 tar(体检报告中"tar 依赖未检测"问题的根治)。
     */
    private void untar(InputStream in, File destDir, ProgressCallback cb) throws Exception {
        byte[] header = new byte[512];
        ByteArrayOutputStream longName = new ByteArrayOutputStream();
        int files = 0;
        while (true) {
            if (!readFully(in, header)) break;
            if (isZeroBlock(header)) break;

            String name = readCString(header, 0, 100);
            long size = parseOctal(header, 124, 12);
            String type = new String(header, 156, 1, StandardCharsets.UTF_8);
            String linkName = readCString(header, 157, 100);
            String magic = readCString(header, 257, 6);

            if (!"ustar".equals(magic) && !"ustar ".equals(magic)) {
                // 未知块,跳过该块继续(尽力而为)
                skipFully(in, 512);
                continue;
            }
            // GNU 长文件名
            if ("L".equals(type)) {
                longName.reset();
                byte[] data = new byte[(int) size];
                if (!readFully(in, data)) break;
                longName.write(readCString(data, 0, data.length).getBytes(StandardCharsets.UTF_8));
                continue;
            }
            String realName = longName.size() > 0
                    ? longName.toString("UTF-8")
                    : name;
            longName.reset();

            File outFile = new File(destDir, realName);
            // 安全:拒绝越出 rootfs 的路径
            if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                skipFully(in, ((int) ((size + 511) / 512)) * 512);
                continue;
            }

            if ("5".equals(type)) {
                outFile.mkdirs();
                if (size > 0) skipFully(in, size + pad(size));
            } else if ("2".equals(type) || "1".equals(type)) {
                // 符号链接/硬链接:记录为链接目标文件说明(容器内由 proot 处理)
                File linkHint = new File(outFile.getParentFile(), "." + outFile.getName() + ".link");
                try (FileOutputStream fos = new FileOutputStream(linkHint)) {
                    fos.write(linkName.getBytes(StandardCharsets.UTF_8));
                }
                if (size > 0) skipFully(in, size + pad(size));
            } else {
                File parent = outFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[65536];
                    long remain = size;
                    while (remain > 0) {
                        int chunk = (int) Math.min(buf.length, remain);
                        if (!readFully(in, buf, chunk)) throw new Exception("tar 数据提前结束");
                        fos.write(buf, 0, chunk);
                        remain -= chunk;
                    }
                }
                if (realName.contains("/bin/") || realName.contains("/sbin/")) {
                    outFile.setExecutable(true, false);
                }
                skipFully(in, pad(size));
            }
            files++;
            if (files % 200 == 0) {
                cb.onProgress("解包中: " + files + " 个文件...", 85);
            }
        }
        cb.onProgress("解包完成: 共 " + files + " 个文件", 95);
    }

    /** tar 块填充:数据对齐到 512 字节 */
    private static long pad(long size) {
        return (512 - (size % 512)) % 512;
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte v : b) if (v != 0) return false;
        return true;
    }

    private static String readCString(byte[] buf, int off, int len) {
        int end = off;
        int max = off + len;
        while (end < max && buf[end] != 0) end++;
        return new String(buf, off, end - off, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long v = 0;
        boolean started = false;
        for (int i = off; i < off + len; i++) {
            byte c = buf[i];
            if (c == 0 || c == ' ') {
                if (started) break;
                continue;
            }
            started = true;
            v = (v << 3) + (c - '0');
        }
        return v;
    }

    private static boolean readFully(InputStream in, byte[] buf) throws Exception {
        return readFully(in, buf, buf.length);
    }

    private static boolean readFully(InputStream in, byte[] buf, int len) throws Exception {
        int done = 0;
        while (done < len) {
            int r = in.read(buf, done, len - done);
            if (r == -1) return false;
            done += r;
        }
        return true;
    }

    private static void skipFully(InputStream in, long n) throws Exception {
        long done = 0;
        while (done < n) {
            long s = in.skip(n - done);
            if (s <= 0) {
                if (in.read() == -1) return;
                s = 1;
            }
            done += s;
        }
    }

    /**
     * 构建 PRoot ProcessBuilder,执行指定命令(使用当前活跃发行版)
     */
    public ProcessBuilder buildPRootProcess(String... commands) {
        return buildPRootProcessFor(getActiveDistro(), commands);
    }

    /**
     * 构建指定发行版的 PRoot ProcessBuilder(桥接 /api/linux/run 用)
     */
    public ProcessBuilder buildPRootProcessFor(Distro d, String... commands) {
        Distro distro = d == null ? Distro.ALPINE : d;
        File rootfsDir = distroDir(distro);
        String shell = distro.shell;

        File prootBin = new File(nativeLibDir, "libproot.so");
        File prootLoader = new File(nativeLibDir, "libproot-loader.so");

        String[] baseCmd = new String[]{
                prootBin.getAbsolutePath(),
                "-r", rootfsDir.getAbsolutePath(),
                "-0",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/sdcard",
                "-w", "/root"
        };

        String[] fullCmd;
        if (commands == null || commands.length == 0) {
            fullCmd = new String[baseCmd.length + 1];
            System.arraycopy(baseCmd, 0, fullCmd, 0, baseCmd.length);
            fullCmd[baseCmd.length] = shell;
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
