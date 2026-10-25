package com.free.hark.server;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机本地静态站点管理器 (SiteManager)
 * 允许手机直接托管 AI 生成的前端项目 (HTML/JS/CSS/多媒体资源)。
 */
public class SiteManager {

    private final File sitesRoot;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("mjs", "application/javascript; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("md", "text/markdown; charset=utf-8");
    }

    public SiteManager(Context context) {
        // 与 LocalHttpServer 使用同一应用专属目录,免权限
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        this.sitesRoot = new File(base, "HarkFree/sites");
        if (!sitesRoot.exists()) {
            sitesRoot.mkdirs();
        }
    }

    public File getSitesRoot() {
        return sitesRoot;
    }

    /**
     * 获取所有已部署的站点名称列表
     */
    public List<String> listSites() {
        List<String> list = new ArrayList<>();
        File[] files = sitesRoot.listFiles(File::isDirectory);
        if (files != null) {
            for (File f : files) {
                list.add(f.getName());
            }
        }
        return list;
    }

    /**
     * 根据站点名和相对路径获取文件
     */
    public File resolveSiteFile(String siteName, String subPath) {
        File siteDir = new File(sitesRoot, siteName);
        if (!siteDir.exists() || !siteDir.isDirectory()) {
            return null;
        }

        if (subPath == null || subPath.isEmpty() || subPath.equals("/")) {
            subPath = "index.html";
        } else if (subPath.startsWith("/")) {
            subPath = subPath.substring(1);
        }

        File target = new File(siteDir, subPath);
        // 防路径穿越安全检查
        try {
            if (!target.getCanonicalPath().startsWith(siteDir.getCanonicalPath())) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        return target.exists() ? target : null;
    }

    public static String getMimeType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }
}
