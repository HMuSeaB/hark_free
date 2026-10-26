package com.free.hark.ssh;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SSH 服务器管理 (SshActivity)
 * 添加/删除用户自己的 SSH 主机,支持连通性测试。凭据仅保存在本机。
 * 桥接开启后,Agent 可通过 /api/servers、/api/ssh、/api/sput、/api/sget 经手机操作这些主机。
 */
public class SshActivity extends AppCompatActivity {

    private LinearLayout container;
    private SshManager sshManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sshManager = new SshManager(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0B0C);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(container);
        setContentView(scroll);

        showList();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView title(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF3DDC84);
        tv.setTextSize(17);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(14), 0, dp(6));
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

    private void showList() {
        container.removeAllViews();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button bBack = button("←", 0xFF2A2A30, v -> finish());
        bBack.setPadding(dp(10), 0, dp(10), 0);
        Button bAdd = button("＋ 添加服务器", 0xFF07C160, v -> showAdd());
        bar.addView(bBack);
        bar.addView(bAdd);
        container.addView(bar);

        container.addView(title("SSH 服务器"));
        container.addView(muted("桥接开启后,Agent 可经手机在这些主机上执行命令、传取文件(/api/ssh、/api/sput、/api/sget)。凭据仅存本机。"));

        JSONArray servers = sshManager.list();
        if (servers.length() == 0) {
            container.addView(muted("还没有添加服务器。"));
            return;
        }
        for (int i = 0; i < servers.length(); i++) {
            JSONObject s = servers.optJSONObject(i);
            if (s == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(0xFF18181C);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView name = new TextView(this);
            name.setText(s.optString("name") + "  (" + s.optString("user") + "@" + s.optString("host") + ":" + s.optInt("port", 22) + ")");
            name.setTextColor(0xFFF4F4F5);
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            row.addView(name);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            String id = s.optString("id");
            actions.addView(button("测试", 0xFF2A2A30, v -> {
                Toast.makeText(this, "测试中...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        String out = sshManager.exec(id, "uname -a", 15).trim();
                        ui(() -> Toast.makeText(this, out.isEmpty() ? "连接成功(无输出)" : out, Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        ui(() -> Toast.makeText(this, "失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }));
            actions.addView(button("删除", 0xFFE5484D, v -> {
                sshManager.remove(id);
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                showList();
            }));
            row.addView(actions);
            container.addView(row);
        }
    }

    private void ui(Runnable r) {
        runOnUiThread(r);
    }

    private void showAdd() {
        container.removeAllViews();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(button("← 返回", 0xFF2A2A30, v -> showList()));
        container.addView(bar);

        container.addView(title("添加 SSH 服务器"));
        EditText etName = field("名称(如:我的电脑)", "", false);
        EditText etHost = field("主机 / IP", "", false);
        EditText etPort = field("端口(默认 22)", "22", false);
        EditText etUser = field("用户名", "", false);
        EditText etPass = field("密码", "", true);

        container.addView(etName);
        container.addView(etHost);
        container.addView(etPort);
        container.addView(etUser);
        container.addView(etPass);
        TextView status = muted("");
        container.addView(status);

        container.addView(button("保存并测试", 0xFF07C160, v -> {
            try {
                String name = etName.getText().toString().trim();
                String host = etHost.getText().toString().trim();
                int port = Integer.parseInt(etPort.getText().toString().trim());
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                if (name.isEmpty() || host.isEmpty() || user.isEmpty()) {
                    toast("请填写完整");
                    return;
                }
                status.setText("保存中...");
                String id = sshManager.add(name, host, port, user, pass);
                if (id == null) {
                    status.setText("保存失败");
                    return;
                }
                toast("已保存");
                new Thread(() -> {
                    try {
                        String out = sshManager.exec(id, "uname -a", 15).trim();
                        ui(() -> {
                            Toast.makeText(this, out.isEmpty() ? "已保存,连接成功(无输出)" : "已保存,连接成功: " + out, Toast.LENGTH_LONG).show();
                            showList();
                        });
                    } catch (Exception e) {
                        ui(() -> {
                            status.setText("已保存,但连接失败: " + e.getMessage());
                            showList();
                        });
                    }
                }).start();
            } catch (Exception e) {
                toast("端口格式错误");
            }
        }));
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
