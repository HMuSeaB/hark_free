package com.free.hark.server;

import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public LocalHttpServer(int port) {
        this.port = port;
        this.storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HarkFree");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.siteManager = new SiteManager();
    }

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
                handleUpload(uri, in, contentLength, out);
            } else if (uri.startsWith("/api/rm")) {
                handleDelete(uri, out);
            } else if (uri.equals("/api/sh") && method.equalsIgnoreCase("POST")) {
                handleShellExec(reader, contentLength, out);
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
                "async function loadLs(){let res=await (await fetch('/api/ls')).json();let h='';" +
                "res.forEach(f=>{h+=`<div class='item'><a href='/f/${encodeURIComponent(f.name)}'>${f.name}</a><span>${(f.size/1024).toFixed(1)} KB</span><a href='#' onclick='rm(\"${encodeURIComponent(f.name)}\")' style='color:#E5484D;flex:none'>删除</a></div>`;});" +
                "document.getElementById('ls').innerHTML=h||'<div style=\"color:#888\">暂无文件</div>';}" +
                "async function rm(n){if(!confirm('确定删除?'))return;await fetch('/api/rm?name='+n,{method:'POST'});loadLs();}" +
                "const fi=document.getElementById('fi');const drop=document.getElementById('drop');" +
                "drop.onclick=()=>fi.click();fi.onchange=()=>up(fi.files);" +
                "drop.ondragover=e=>e.preventDefault();drop.ondrop=e=>{e.preventDefault();up(e.dataTransfer.files);};" +
                "async function up(fs){for(let f of fs){document.getElementById('pg').textContent='正在发送: '+f.name;" +
                "let x=new XMLHttpRequest();x.open('POST','/api/upload?name='+encodeURIComponent(f.name));" +
                "await new Promise(r=>{x.onload=x.onerror=r;x.send(f);});}document.getElementById('pg').textContent='传输完成！';loadLs();}" +
                "async function runCmd(){document.getElementById('out').textContent='执行中...';" +
                "let r=await fetch('/api/sh',{method:'POST',body:document.getElementById('cmd').value});" +
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

    private void handleUpload(String uri, InputStream in, int len, OutputStream out) throws Exception {
        String name = "upload_" + System.currentTimeMillis();
        int idx = uri.indexOf("name=");
        if (idx > 0) {
            name = URLDecoder.decode(uri.substring(idx + 5), "UTF-8");
        }

        File target = new File(storageDir, name);
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
            File file = new File(storageDir, name);
            if (file.exists()) {
                file.delete();
            }
        }
        sendResponse(out, "200 OK", "text/plain", "OK".getBytes(StandardCharsets.UTF_8));
    }

    private void handleShellExec(BufferedReader reader, int len, OutputStream out) throws Exception {
        char[] buf = new char[len];
        reader.read(buf, 0, len);
        String cmd = new String(buf).trim();

        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = procReader.readLine()) != null) {
                sb.append(l).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            sb.append("执行异常: ").append(e.getMessage());
        }
        sendResponse(out, "200 OK", "text/plain; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void serveDownloadFile(String rawName, OutputStream out) throws Exception {
        String name = URLDecoder.decode(rawName, "UTF-8");
        File file = new File(storageDir, name);
        if (!file.exists() || !file.isFile()) {
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
