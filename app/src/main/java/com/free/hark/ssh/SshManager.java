package com.free.hark.ssh;

import android.content.Context;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSH 服务器管理器 (SshManager)
 * 保存用户自己的 SSH 主机(凭据仅存本机),支持经手机在远端执行命令与传文件。
 * 桥接端点 /api/servers、/api/ssh、/api/sput、/api/sget 的后端。
 */
public class SshManager {

    private final File storeFile;

    public SshManager(Context context) {
        this.storeFile = new File(context.getFilesDir(), "ssh_servers.json");
    }

    public synchronized JSONArray list() {
        try {
            return new JSONObject(read()).getJSONArray("servers");
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public synchronized JSONObject get(String id) {
        JSONArray arr = list();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    public synchronized String add(String name, String host, int port, String user, String pass, String key) {
        try {
            JSONObject root = readRoot();
            JSONArray arr = root.optJSONArray("servers");
            if (arr == null) arr = new JSONArray();
            String id = "s" + System.currentTimeMillis();
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("host", host);
            o.put("port", port);
            o.put("user", user);
            o.put("pass", pass);
            o.put("key", key == null ? "" : key);
            arr.put(o);
            root.put("servers", arr);
            write(root);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void remove(String id) {
        try {
            JSONObject root = readRoot();
            JSONArray arr = root.optJSONArray("servers");
            JSONArray out = new JSONArray();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null && !id.equals(o.optString("id"))) out.put(o);
                }
            }
            root.put("servers", out);
            write(root);
        } catch (Exception ignored) {}
    }

    private String read() {
        try (FileInputStream in = new FileInputStream(storeFile)) {
            byte[] b = new byte[(int) storeFile.length()];
            int done = 0;
            while (done < b.length) {
                int r = in.read(b, done, b.length - done);
                if (r == -1) break;
                done += r;
            }
            return new String(b, 0, done, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JSONObject readRoot() {
        try {
            return new JSONObject(read());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void write(JSONObject root) {
        try (FileOutputStream out = new FileOutputStream(storeFile)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    // ---------- SSH 操作 ----------

    private Session connect(JSONObject s, int timeoutSeconds) throws Exception {
        JSch jsch = new JSch();
        String key = s.optString("key", "");
        if (!key.isEmpty()) {
            // 密钥认证(可选填 OpenSSH 私钥;留空则用密码)
            jsch.addIdentity("harkfree-key", key.getBytes(StandardCharsets.UTF_8), null, null);
        }
        Session session = jsch.getSession(s.optString("user"), s.optString("host"), s.optInt("port", 22));
        session.setPassword(s.optString("pass"));
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(timeoutSeconds * 1000);
        session.connect(timeoutSeconds * 1000);
        return session;
    }

    /** 在远端执行命令,返回合并输出(stdout+stderr) */
    public String exec(String id, String command, int timeoutSeconds) throws Exception {
        JSONObject s = get(id);
        if (s == null) throw new Exception("服务器不存在");
        Session session = connect(s, timeoutSeconds);
        try {
            ChannelExec ch = (ChannelExec) session.openChannel("exec");
            ch.setCommand(command);
            ch.setErrStream(null);
            InputStream in = ch.getInputStream();
            InputStream err = ch.getErrStream();
            ch.connect(timeoutSeconds * 1000);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int r;
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while ((System.currentTimeMillis() < deadline) && (ch.isConnected() || in.available() > 0 || err.available() > 0)) {
                while ((r = in.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
                while ((r = err.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
                if (ch.isClosed() && in.available() == 0 && err.available() == 0) break;
                Thread.sleep(100);
            }
            while ((r = in.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
            while ((r = err.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
            ch.disconnect();
            return sb.toString();
        } finally {
            session.disconnect();
        }
    }

    /** 上传文件内容到远端路径(SFTP) */
    public void sput(String id, String remotePath, byte[] data) throws Exception {
        JSONObject s = get(id);
        if (s == null) throw new Exception("服务器不存在");
        Session session = connect(s, 30);
        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);
            try (InputStream in = new java.io.ByteArrayInputStream(data)) {
                sftp.put(in, remotePath);
            }
            sftp.disconnect();
        } finally {
            session.disconnect();
        }
    }

    /** 从远端下载文件内容(SFTP) */
    public byte[] sget(String id, String remotePath) throws Exception {
        JSONObject s = get(id);
        if (s == null) throw new Exception("服务器不存在");
        Session session = connect(s, 30);
        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = sftp.get(remotePath)) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            sftp.disconnect();
            return out.toByteArray();
        } finally {
            session.disconnect();
        }
    }
}
