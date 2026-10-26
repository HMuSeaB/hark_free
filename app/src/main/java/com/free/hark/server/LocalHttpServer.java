package com.free.hark.server;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 手机本地微型 HTTP 服务器 (LocalHttpServer)
 * 提供电脑-手机文件极速互传、在线 Shell 调试控制台与静态网站宿主。
 */
public class LocalHttpServer {

    private final int port;
    private final File storageDir;
    private final SiteManager siteManager;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final ExecutorService threadPool = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    // 访问令牌:每次服务启动随机生成,所有 /api 与 /f 请求必须携带 ?t=<token>
    private final String accessToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final com.free.hark.linux.LinuxManager linuxManager;
    private final com.free.hark.token.TokenTracker tokenTracker;
    // 正在安装的发行版(防重复触发)
    private final java.util.Set<String> installing = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public String getAccessToken() {
        return accessToken;
    }

    public LocalHttpServer(Context context, int port) {
        this.port = port;
        // 应用专属外部目录:API 26~34 全版本免权限可写,规避分区存储限制
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        this.storageDir = new File(base, "HarkFree");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.siteManager = new SiteManager(context);
        this.linuxManager = new com.free.hark.linux.LinuxManager(context);
        this.tokenTracker = new com.free.hark.token.TokenTracker(context);
        this.appContext = context;
    }
    private final Context appContext;

    public synchronized void start() throws Exception {
        if (isRunning) return;
        serverSocket = new ServerSocket(port);
        isRunning = true;

        threadPool.execute(() -> {
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    threadPool.execute(() -> handleClient(client));
                } catch (Exception e) {
                    if (!isRunning) break;
                }
            }
        });
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isEmpty()) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];

            // 访问令牌校验:除静态站点外的所有端点必须携带 ?t=<token>
            if (!uri.startsWith("/sites/") && !hasValidToken(uri)) {
                sendResponse(out, "403 Forbidden", "text/plain", "403 Forbidden (missing/invalid token)".getBytes());
                return;
            }

            // 头部读取
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // 路由派发
            if (uri.equals("/") || uri.startsWith("/?")) {
                serveConsoleHtml(out);
            } else if (uri.equals("/api/info")) {
                serveApiInfo(out);
            } else if (uri.equals("/api/ls")) {
                serveApiLs(out);
            } else if (uri.startsWith("/api/upload")) {
                if (!method.equalsIgnoreCase("POST")) { sendResponse(out, "405 Method Not Allowed", "text/plain", "POST only".getBytes()); return; }
                handleUpload(uri, in, contentLength, out);
            } else if (uri.startsWith("/api/rm")) {
                if (!method.equalsIgnoreCase("POST")) { sendResponse(out, "405 Method Not Allowed", "text/plain", "POST only".getBytes()); return; }
                handleDelete(uri, out);
            } else if (uri.startsWith("/api/linux/run")) {
                if (!method.equalsIgnoreCase("POST")) { sendResponse(out, "405 Method Not Allowed", "text/plain", "POST only".getBytes()); return; }
                handleLinuxRun(uri, reader, contentLength, out);
            } else if (uri.startsWith("/api/linux")) {
                handleLinux(uri, out);
            } else if (uri.equals("/api/apps")) {
                serveApiApps(out);
            } else if (uri.startsWith("/api/open")) {
                handleOpen(uri, out);
            } else if (uri.equals("/api/usage")) {
                serveApiUsage(out);
            } else if (uri.startsWith("/api/site/put")) {
                if (!method.equalsIgnoreCase("POST")) { sendResponse(out, "405 Method Not Allowed", "text/plain", "POST only".getBytes()); return; }
                sitePut(uri, in, contentLength, out);
            } else if (uri.equals("/api/sites")) {
                siteList(out);
            } else if (uri.startsWith("/api/site/files")) {
                siteFiles(uri, out);
            } else if (uri.startsWith("/api/site/rm")) {
                if (!method.equalsIgnoreCase("POST")) { sendResponse(out, "405 Method Not Allowed", "text/plain", "POST only".getBytes()); return; }
                siteRm(uri, out);
            } else if (uri.startsWith("/api/site/start")) {
                String name = queryParam(uri, "name");
                sendResponse(out, "200 OK", "application/json",
                        ("{\"ok\":true,\"url\":\"/sites/" + (name == null ? "" : name) + "/\"}").getBytes());
            } else if (uri.equals("/api/sh") && method.equalsIgnoreCase("POST")) {
                handleShellExec(uri, reader, contentLength, out);
            } else if (uri.startsWith("/f/")) {
                serveDownloadFile(uri.substring(3), out);
            } else if (uri.startsWith("/sites/")) {
                serveStaticSite(uri.substring(7), out);
            } else {
                sendResponse(out, "404 Not Found", "text/plain", "404 Not Found".getBytes());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void serveConsoleHtml(OutputStream out) throws Exception {
        String html = "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<title>HarkFree · 互传与极客控制台</title>" +
                "<style>" +
                "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif;background:#0B0B0C;color:#F4F4F5;padding:20px}" +
                ".w{max-width:880px;margin:0 auto}h1{font-size:24px;font-weight:700;color:#3DDC84}h2{font-size:16px;color:#9A9AA3;margin-top:24px}" +
                ".card{background:#18181C;border:1px solid #2A2A30;border-radius:14px;padding:20px;margin-top:12px}" +
                "#drop{border:2px dashed #3DDC84;border-radius:12px;padding:30px;text-align:center;cursor:pointer;background:rgba(61,220,132,0.05)}" +
                ".item{display:flex;align-items:center;padding:10px 0;border-bottom:1px solid #232327}" +
                ".item a{color:#3DDC84;text-decoration:none;flex:1}.item span{color:#888;font-size:12px;margin:0 10px}" +
                "textarea{width:100%;box-sizing:border-box;background:#0E0E10;border:1px solid #2A2A30;color:#F4F4F5;padding:10px;border-radius:8px;font-family:monospace}" +
                "button{background:#07C160;color:#fff;border:0;border-radius:6px;padding:8px 20px;font-weight:600;cursor:pointer;margin-top:8px}" +
                "pre{background:#0A0A0C;padding:12px;border-radius:8px;border:1px solid #222;max-height:300px;overflow:auto;font-size:13px;color:#7CF0B1}" +
                "</style></head><body><div class='w'>" +
                "<h1>HarkFree · 局域网控制台</h1>" +
                "<div class='card'><h2>发送文件到手机 (Download/HarkFree)</h2>" +
                "<div id='drop'>点击或拖拽文件到此处即可立即发送至手机<input type='file' id='fi' multiple hidden></div>" +
                "<div id='pg' style='font-size:13px;color:#3DDC84;margin-top:8px'></div></div>" +
                "<div class='card'><h2>手机文件列表 (点击下载到电脑)</h2><div id='ls'>加载中...</div></div>" +
                "<div class='card'><h2>执行手机 Shell 命令</h2>" +
                "<textarea id='cmd' rows='3' placeholder='输入命令，例如: uname -a 或 ls -la'></textarea>" +
                "<button onclick='runCmd()'>执行命令</button>" +
                "<pre id='out'>（命令输出会显示在这里）</pre></div>" +
                "</div>" +
                "<script>" +
                "const T=new URLSearchParams(location.search).get('t')||'';const q=u=>u+(u.includes('?')?'&':'?')+'t='+T;" +
                "async function loadLs(){let res=await (await fetch(q('/api/ls'))).json();let h='';" +
                "res.forEach(f=>{h+=`<div class='item'><a href='/f/${encodeURIComponent(f.name)}'>${f.name}</a><span>${(f.size/1024).toFixed(1)} KB</span><a href='#' onclick='rm(\"${encodeURIComponent(f.name)}\")' style='color:#E5484D;flex:none'>删除</a></div>`;});" +
                "document.getElementById('ls').innerHTML=h||'<div style=\"color:#888\">暂无文件</div>';}" +
                "async function rm(n){if(!confirm('确定删除?'))return;await fetch(q('/api/rm?name='+n),{method:'POST'});loadLs();}" +
                "const fi=document.getElementById('fi');const drop=document.getElementById('drop');" +
                "drop.onclick=()=>fi.click();fi.onchange=()=>up(fi.files);" +
                "drop.ondragover=e=>e.preventDefault();drop.ondrop=e=>{e.preventDefault();up(e.dataTransfer.files);};" +
                "async function up(fs){for(let f of fs){document.getElementById('pg').textContent='正在发送: '+f.name;" +
                "let x=new XMLHttpRequest();x.open('POST',q('/api/upload?name='+encodeURIComponent(f.name)));" +
                "await new Promise(r=>{x.onload=x.onerror=r;x.send(f);});}document.getElementById('pg').textContent='传输完成！';loadLs();}" +
                "async function runCmd(){document.getElementById('out').textContent='执行中...';" +
                "let r=await fetch(q('/api/sh'),{method:'POST',body:document.getElementById('cmd').value});" +
                "document.getElementById('out').textContent=await r.text()||'（无输出）';}" +
                "loadLs();" +
                "</script></body></html>";

        sendResponse(out, "200 OK", "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void serveApiInfo(OutputStream out) throws Exception {
        JSONObject json = new JSONObject();
        json.put("device", Build.MODEL);
        json.put("android", Build.VERSION.RELEASE);
        json.put("port", port);
        json.put("storage", storageDir.getAbsolutePath());
        sendResponse(out, "200 OK", "application/json", json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void serveApiLs(OutputStream out) throws Exception {
        JSONArray arr = new JSONArray();
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", f.getName());
                    obj.put("size", f.length());
                    obj.put("time", f.lastModified());
                    arr.put(obj);
                }
            }
        }
        sendResponse(out, "200 OK", "application/json", arr.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasValidToken(String uri) {
        int t = uri.indexOf("t=");
        if (t < 0) return false;
        String token = uri.substring(t + 2);
        int amp = token.indexOf('&');
        if (amp >= 0) token = token.substring(0, amp);
        return accessToken.equals(token);
    }

    /** 防路径穿越:目标必须位于存储目录内部 */
    private boolean insideStorage(File f) {
        try {
            return f.getCanonicalPath().startsWith(storageDir.getCanonicalPath() + File.separator)
                    || f.getCanonicalPath().equals(storageDir.getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    private void handleUpload(String uri, InputStream in, int len, OutputStream out) throws Exception {
        String name = "upload_" + System.currentTimeMillis();
        int idx = uri.indexOf("name=");
        if (idx > 0) {
            name = URLDecoder.decode(uri.substring(idx + 5), "UTF-8");
        }
        name = new File(name).getName();

        File target = new File(storageDir, name);
        if (!insideStorage(target)) {
            sendResponse(out, "403 Forbidden", "text/plain", "Invalid path".getBytes());
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int read, remain = len;
            while (remain > 0 && (read = in.read(buf, 0, Math.min(buf.length, remain))) != -1) {
                fos.write(buf, 0, read);
                remain -= read;
            }
        }
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes(StandardCharsets.UTF_8));
    }

    private void handleDelete(String uri, OutputStream out) throws Exception {
        int idx = uri.indexOf("name=");
        if (idx > 0) {
            String name = URLDecoder.decode(uri.substring(idx + 5), "UTF-8");
            File file = new File(storageDir, new File(name).getName());
            if (insideStorage(file) && file.exists()) {
                file.delete();
            }
        }
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes(StandardCharsets.UTF_8));
    }

    private void handleShellExec(String uri, BufferedReader reader, int len, OutputStream out) throws Exception {
        int timeout = parseTimeout(uri, 60);
        char[] buf = new char[len];
        int read, done = 0;
        while (done < len && (read = reader.read(buf, done, len - done)) != -1) {
            done += read;
        }
        String cmd = new String(buf, 0, done).trim();

        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = procReader.readLine()) != null) {
                sb.append(l).append("\n");
            }
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                sb.append("(超时 ").append(timeout).append("s 已终止)");
            }
        } catch (Exception e) {
            sb.append("执行异常: ").append(e.getMessage());
        }
        sendResponse(out, "200 OK", "text/plain; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void serveDownloadFile(String rawName, OutputStream out) throws Exception {
        String name = URLDecoder.decode(rawName, "UTF-8");
        File file = new File(storageDir, new File(name).getName());
        if (!insideStorage(file) || !file.exists() || !file.isFile()) {
            sendResponse(out, "404 Not Found", "text/plain", "File not found".getBytes());
            return;
        }

        String mime = SiteManager.getMimeType(name);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = ("HTTP/1.1 200 OK\r\nContent-Type: " + mime +
                    "\r\nContent-Length: " + file.length() +
                    "\r\nContent-Disposition: attachment; filename=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            out.write(header);
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }

    private void serveStaticSite(String path, OutputStream out) throws Exception {
        int slash = path.indexOf('/');
        String siteName = slash > 0 ? path.substring(0, slash) : path;
        String subPath = slash > 0 ? path.substring(slash + 1) : "index.html";

        File file = siteManager.resolveSiteFile(siteName, subPath);
        if (file == null) {
            sendResponse(out, "404 Not Found", "text/plain", "Site file not found".getBytes());
            return;
        }

        String mime = SiteManager.getMimeType(file.getName());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = ("HTTP/1.1 200 OK\r\nContent-Type: " + mime +
                    "\r\nContent-Length: " + file.length() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            out.write(header);
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }

    // ===== 桥接端点(对标 HarkMulti teach 契约)=====

    private static String queryParam(String uri, String key) {
        int idx = uri.indexOf(key + "=");
        if (idx < 0) return null;
        String v = uri.substring(idx + key.length() + 1);
        int amp = v.indexOf('&');
        if (amp >= 0) v = v.substring(0, amp);
        try {
            return URLDecoder.decode(v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }

    private static int parseTimeout(String uri, int def) {
        String t = queryParam(uri, "timeout");
        if (t == null) return def;
        try {
            return Math.max(5, Math.min(600, Integer.parseInt(t.trim())));
        } catch (Exception e) {
            return def;
        }
    }

    private static com.free.hark.linux.LinuxManager.Distro distroById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (com.free.hark.linux.LinuxManager.Distro d : com.free.hark.linux.LinuxManager.Distro.values()) {
            if (d.name().equalsIgnoreCase(id)) return d;
        }
        return null;
    }

    private void handleLinux(String uri, OutputStream out) throws Exception {
        if (uri.contains("install=1")) {
            String id = queryParam(uri, "id");
            com.free.hark.linux.LinuxManager.Distro d = distroById(id);
            if (d == null) {
                sendResponse(out, "400 Bad Request", "text/plain", "unknown distro id (alpine/debian/ubuntu)".getBytes());
                return;
            }
            JSONObject root = new JSONObject();
            root.put("id", d.name().toLowerCase());
            if (linuxManager.isInstalled(d)) {
                root.put("installed", true);
            } else if (installing.add(d.name())) {
                linuxManager.installDistro(d, new com.free.hark.linux.LinuxManager.ProgressCallback() {
                    @Override public void onProgress(String message, int percentage) {}
                    @Override public void onSuccess() { installing.remove(d.name()); }
                    @Override public void onError(String error) { installing.remove(d.name()); }
                });
                root.put("installStarted", true);
            } else {
                root.put("installStarted", true);
                root.put("note", "already installing");
            }
            sendResponse(out, "200 OK", "application/json", root.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        for (com.free.hark.linux.LinuxManager.Distro d : com.free.hark.linux.LinuxManager.Distro.values()) {
            JSONObject o = new JSONObject();
            o.put("id", d.name().toLowerCase());
            o.put("title", d.title);
            o.put("installed", linuxManager.isInstalled(d));
            o.put("installing", installing.contains(d.name()));
            arr.put(o);
        }
        root.put("distros", arr);
        com.free.hark.linux.LinuxManager.Distro active = linuxManager.getActiveDistro();
        root.put("active", active == null ? "" : active.name().toLowerCase());
        sendResponse(out, "200 OK", "application/json", root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void handleLinuxRun(String uri, BufferedReader reader, int len, OutputStream out) throws Exception {
        String id = queryParam(uri, "id");
        com.free.hark.linux.LinuxManager.Distro d = distroById(id);
        if (d == null) d = linuxManager.getActiveDistro();
        if (d == null || !linuxManager.isInstalled(d)) {
            sendResponse(out, "400 Bad Request", "text/plain",
                    "没装这个系统(id=debian/ubuntu/alpine,先 POST /api/linux?id=..&install=1)".getBytes());
            return;
        }
        int timeout = parseTimeout(uri, 120);
        char[] buf = new char[len];
        int done = 0;
        while (done < len) {
            int r = reader.read(buf, done, len - done);
            if (r == -1) break;
            done += r;
        }
        String cmd = new String(buf, 0, done).trim();
        if (cmd.isEmpty()) {
            sendResponse(out, "400 Bad Request", "text/plain", "empty command".getBytes());
            return;
        }
        StringBuilder sb = new StringBuilder();
        try {
            Process p = linuxManager.buildPRootProcessFor(d, "/bin/sh", "-c", cmd)
                    .redirectErrorStream(true).start();
            BufferedReader pr = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = pr.readLine()) != null) sb.append(l).append("\n");
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                sb.append("(超时 ").append(timeout).append("s 已终止)");
            }
        } catch (Exception e) {
            sb.append("执行异常: ").append(e.getMessage());
        }
        sendResponse(out, "200 OK", "text/plain; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void serveApiApps(OutputStream out) throws Exception {
        android.content.pm.PackageManager pm = appContext.getPackageManager();
        JSONArray arr = new JSONArray();
        for (android.content.pm.PackageInfo pi : pm.getInstalledPackages(0)) {
            JSONObject o = new JSONObject();
            o.put("name", pi.applicationInfo.loadLabel(pm).toString());
            o.put("pkg", pi.packageName);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("apps", arr);
        sendResponse(out, "200 OK", "application/json", root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void handleOpen(String uri, OutputStream out) throws Exception {
        String name = queryParam(uri, "name");
        String pkg = queryParam(uri, "pkg");
        String url = queryParam(uri, "url");
        android.content.pm.PackageManager pm = appContext.getPackageManager();
        Intent intent = null;
        if (pkg != null && !pkg.isEmpty()) {
            intent = pm.getLaunchIntentForPackage(pkg);
        } else if (name != null && !name.isEmpty()) {
            for (android.content.pm.PackageInfo pi : pm.getInstalledPackages(0)) {
                if (pi.applicationInfo.loadLabel(pm).toString().contains(name)) {
                    intent = pm.getLaunchIntentForPackage(pi.packageName);
                    if (intent != null) break;
                }
            }
        } else if (url != null && !url.isEmpty()) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }
        if (intent == null) {
            sendResponse(out, "404 Not Found", "text/plain", "app not found".getBytes());
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes());
    }

    private void serveApiUsage(OutputStream out) throws Exception {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < 4; i++) {
            com.free.hark.token.TokenTracker.UsageInfo info = tokenTracker.getSlotUsage("slot" + i);
            JSONObject o = new JSONObject();
            o.put("slot", "slot" + i);
            o.put("used", info.dailyUsed);
            o.put("limit", info.dailyLimit);
            o.put("percentage", info.getPercentage());
            o.put("reset", info.resetsAt);
            arr.put(o);
        }
        sendResponse(out, "200 OK", "application/json", arr.toString().getBytes(StandardCharsets.UTF_8));
    }

    private File siteDir(String uri) {
        String name = queryParam(uri, "name");
        if (name == null || name.isEmpty()) return null;
        File dir = new File(siteManager.getSitesRoot(), new File(name).getName());
        try {
            if (!dir.getCanonicalPath().startsWith(siteManager.getSitesRoot().getCanonicalPath())) return null;
        } catch (Exception e) {
            return null;
        }
        return dir;
    }

    private void sitePut(String uri, InputStream in, int len, OutputStream out) throws Exception {
        String path = queryParam(uri, "path");
        File dir = siteDir(uri);
        if (dir == null || path == null || path.isEmpty()) {
            sendResponse(out, "400 Bad Request", "text/plain", "need name & path".getBytes());
            return;
        }
        File f = new File(dir, path);
        if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) {
            sendResponse(out, "403 Forbidden", "text/plain", "invalid path".getBytes());
            return;
        }
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int r, remain = len;
            while (remain > 0 && (r = in.read(buf, 0, Math.min(buf.length, remain))) != -1) {
                fos.write(buf, 0, r);
                remain -= r;
            }
        }
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes());
    }

    private void siteList(OutputStream out) throws Exception {
        JSONArray arr = new JSONArray();
        for (String s : siteManager.listSites()) arr.put(s);
        sendResponse(out, "200 OK", "application/json", arr.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void siteWalk(File dir, JSONArray arr) throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) siteWalk(f, arr);
            else {
                JSONObject o = new JSONObject();
                o.put("path", dir.toPath().relativize(f.toPath()).toString().replace('\\', '/'));
                o.put("size", f.length());
                arr.put(o);
            }
        }
    }

    private void siteFiles(String uri, OutputStream out) throws Exception {
        File dir = siteDir(uri);
        JSONArray arr = new JSONArray();
        if (dir != null && dir.exists()) siteWalk(dir, arr);
        sendResponse(out, "200 OK", "application/json", arr.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void deleteRecursive(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteRecursive(c);
        f.delete();
    }

    private void siteRm(String uri, OutputStream out) throws Exception {
        File dir = siteDir(uri);
        if (dir != null && dir.exists()) deleteRecursive(dir);
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes());
    }

    private void sendResponse(OutputStream out, String status, String mime, byte[] data) throws Exception {
        String header = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + mime + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }
}
