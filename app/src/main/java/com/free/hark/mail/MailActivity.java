package com.free.hark.mail;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * 自有邮箱收发 (MailActivity) —— 对标 HarkMulti 的邮件功能,但使用用户自己的邮箱。
 * IMAP 收件 + SMTP 发件,凭据仅保存在本机应用私有目录。
 */
public class MailActivity extends AppCompatActivity {

    private LinearLayout container;
    private final HandlerLike ui = new HandlerLike();

    // 配置缓存
    private String imapHost, smtpHost, user, pass;
    private int imapPort = 993, smtpPort = 465;
    private File configFile;

    private class HandlerLike {
        void post(Runnable r) { runOnUiThread(r); }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configFile = new File(getFilesDir(), "mail_config.properties");
        loadConfig();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0B0C);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(container);
        setContentView(scroll);

        if (user.isEmpty() || pass.isEmpty() || imapHost.isEmpty()) {
            showSettings();
        } else {
            showInbox();
        }
    }

    // ---------- 配置 ----------

    private void loadConfig() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(configFile)) {
            p.load(in);
        } catch (Exception ignored) {}
        imapHost = p.getProperty("imap_host", "");
        smtpHost = p.getProperty("smtp_host", "");
        user = p.getProperty("user", "");
        pass = p.getProperty("pass", "");
        try { imapPort = Integer.parseInt(p.getProperty("imap_port", "993")); } catch (Exception ignored) {}
        try { smtpPort = Integer.parseInt(p.getProperty("smtp_port", "465")); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.put("imap_host", imapHost);
        p.put("smtp_host", smtpHost);
        p.put("imap_port", String.valueOf(imapPort));
        p.put("smtp_port", String.valueOf(smtpPort));
        p.put("user", user);
        p.put("pass", pass);
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            p.store(out, "HarkFree mail account (local only)");
        } catch (Exception ignored) {}
    }

    // ---------- UI 构件 ----------

    private TextView title(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF3DDC84);
        tv.setTextSize(17);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(14), 0, dp(6));
        return tv;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value);
        et.setTextColor(0xFFF4F4F5);
        et.setHintTextColor(0xFF6A6A72);
        et.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2A30));
        et.setTextSize(14);
        et.setSingleLine(true);
        if (password) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return et;
    }

    private Button button(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView line(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFC9C9CE);
        tv.setTextSize(14);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private TextView muted(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF8A8A92);
        tv.setTextSize(12);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    // ---------- 设置页 ----------

    private void showSettings() {
        container.removeAllViews();
        container.addView(title("邮箱设置"));
        container.addView(muted("凭据仅保存在本机应用私有目录,不会上传。常见配置:QQ邮箱 imap.qq.com:993 / smtp.qq.com:465(密码填授权码);Gmail imap.gmail.com:993 / smtp.gmail.com:465(密码填应用专用密码)。"));

        EditText etImapHost = field("IMAP 服务器(如 imap.qq.com)", imapHost, false);
        EditText etImapPort = field("IMAP 端口(默认 993)", String.valueOf(imapPort), false);
        EditText etSmtpHost = field("SMTP 服务器(如 smtp.qq.com)", smtpHost, false);
        EditText etSmtpPort = field("SMTP 端口(默认 465)", String.valueOf(smtpPort), false);
        EditText etUser = field("邮箱账号", user, false);
        EditText etPass = field("密码 / 授权码", pass, true);

        container.addView(etImapHost);
        container.addView(etImapPort);
        container.addView(etSmtpHost);
        container.addView(etSmtpPort);
        container.addView(etUser);
        container.addView(etPass);

        container.addView(button("保存并收件", 0xFF07C160, v -> {
            try {
                imapHost = etImapHost.getText().toString().trim();
                smtpHost = etSmtpHost.getText().toString().trim();
                user = etUser.getText().toString().trim();
                pass = etPass.getText().toString();
                imapPort = Integer.parseInt(etImapPort.getText().toString().trim());
                smtpPort = Integer.parseInt(etSmtpPort.getText().toString().trim());
                if (user.isEmpty() || pass.isEmpty() || imapHost.isEmpty()) {
                    toast("请填写完整");
                    return;
                }
                saveConfig();
                showInbox();
            } catch (Exception e) {
                toast("端口格式错误");
            }
        }));
    }

    // ---------- 收件箱 ----------

    private void showInbox() {
        container.removeAllViews();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button bBack = button("←", 0xFF2A2A30, v -> showSettings());
        bBack.setPadding(dp(10), 0, dp(10), 0);
        Button bWrite = button("✉ 写邮件", 0xFF07C160, v -> showCompose(null));
        Button bRefresh = button("刷新", 0xFF2A2A30, v -> showInbox());
        bar.addView(bBack);
        bar.addView(bWrite);
        bar.addView(bRefresh);
        container.addView(bar);

        container.addView(title("收件箱 · " + user));
        TextView status = muted("正在连接 " + imapHost + ":" + imapPort + " ...");
        container.addView(status);

        new Thread(() -> {
            try {
                Properties p = new Properties();
                p.put("mail.store.protocol", "imaps");
                p.put("mail.imaps.ssl.trust", "*");
                Session session = Session.getInstance(p);
                Store store = session.getStore("imaps");
                store.connect(imapHost, imapPort, user, pass);
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);
                Message[] msgs = inbox.getMessages();
                int start = Math.max(0, msgs.length - 20);

                StringBuilder sb = new StringBuilder();
                for (int i = msgs.length - 1; i >= start; i--) {
                    Message m = msgs[i];
                    String from = m.getFrom() != null ? m.getFrom()[0].toString() : "(未知发件人)";
                    String subject = safeSubject(m);
                    sb.append("## ").append(i + 1).append(". ").append(subject).append("\n");
                    sb.append("- 发件人: ").append(from).append("\n");
                    sb.append("- 时间: ").append(m.getSentDate() == null ? "未知" : m.getSentDate().toString()).append("\n\n");
                }
                if (msgs.length == 0) sb.append("(收件箱为空)");
                int total = msgs.length;
                inbox.close(false);
                store.close();

                ui.post(() -> {
                    status.setVisibility(View.GONE);
                    ScrollView body = new ScrollView(MailActivity.this);
                    TextView tv = new TextView(MailActivity.this);
                    tv.setTextColor(0xFFC9C9CE);
                    tv.setTextSize(13);
                    tv.setText(formatInbox(total, sb.toString()));
                    body.addView(tv);
                    container.addView(body);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    status.setText("收件失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private String formatInbox(int total, String body) {
        return "共 " + total + " 封\n\n" + body;
    }

    private static String safeSubject(Message m) {
        try {
            String s = m.getSubject();
            return s == null || s.isEmpty() ? "(无主题)" : s;
        } catch (Exception e) {
            return "(主题解析失败)";
        }
    }

    // ---------- 写邮件 ----------

    private void showCompose(String presetText) {
        container.removeAllViews();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(button("← 返回收件箱", 0xFF2A2A30, v -> showInbox()));
        container.addView(bar);

        container.addView(title("写邮件 · " + user));
        EditText etTo = field("收件人(多个用逗号分隔)", "", false);
        EditText etSubject = field("主题", "", false);
        EditText etBody = new EditText(this);
        etBody.setHint("正文");
        etBody.setTextColor(0xFFF4F4F5);
        etBody.setHintTextColor(0xFF6A6A72);
        etBody.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2A30));
        etBody.setMinLines(8);
        etBody.setGravity(Gravity.TOP);
        if (presetText != null) etBody.setText(presetText);

        container.addView(etTo);
        container.addView(etSubject);
        container.addView(etBody);
        TextView status = muted("");
        container.addView(status);

        container.addView(button("发送", 0xFF07C160, v -> {
            String to = etTo.getText().toString().trim();
            String subject = etSubject.getText().toString().trim();
            String body = etBody.getText().toString();
            if (to.isEmpty() || body.isEmpty()) {
                toast("收件人和正文不能为空");
                return;
            }
            status.setText("发送中...");
            new Thread(() -> {
                try {
                    Properties p = new Properties();
                    p.put("mail.transport.protocol", "smtp");
                    p.put("mail.smtp.host", smtpHost);
                    p.put("mail.smtp.port", String.valueOf(smtpPort));
                    p.put("mail.smtp.auth", "true");
                    p.put("mail.smtp.ssl.enable", String.valueOf(smtpPort == 465));
                    p.put("mail.smtp.starttls.enable", String.valueOf(smtpPort == 587));
                    p.put("mail.smtp.ssl.trust", smtpHost);
                    Session session = Session.getInstance(p);

                    MimeMessage msg = new MimeMessage(session);
                    msg.setFrom(new InternetAddress(user));
                    msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                    msg.setSubject(subject.isEmpty() ? "(无主题)" : subject, "UTF-8");
                    msg.setText(body, "UTF-8");
                    Transport.send(msg, user, pass);

                    ui.post(() -> {
                        toast("发送成功");
                        showInbox();
                    });
                } catch (Exception e) {
                    ui.post(() -> status.setText("发送失败: " + e.getMessage()));
                }
            }).start();
        }));
    }

    // ---------- 正文提取 ----------

    private static String extractText(Message m) {
        try {
            Object c = m.getContent();
            if (c instanceof String) return (String) c;
            if (c instanceof MimeMultipart) {
                MimeMultipart mp = (MimeMultipart) c;
                for (int i = 0; i < mp.getCount(); i++) {
                    if (mp.getBodyPart(i).isMimeType("text/plain")) {
                        return (String) mp.getBodyPart(i).getContent();
                    }
                }
                for (int i = 0; i < mp.getCount(); i++) {
                    if (mp.getBodyPart(i).isMimeType("text/html")) {
                        String html = (String) mp.getBodyPart(i).getContent();
                        return html.replaceAll("(?s)<style.*?</style>", "").replaceAll("<[^>]+>", " ").replaceAll("\\s{2,}", "\n");
                    }
                }
            }
            return String.valueOf(c);
        } catch (Exception e) {
            return "(正文解析失败: " + e.getMessage() + ")";
        }
    }
}
