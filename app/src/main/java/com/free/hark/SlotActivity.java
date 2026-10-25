package com.free.hark;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 槽位独立进程 WebView 容器 (SlotActivity)
 * 每个槽位拥有独立的进程，原生物理隔离 Cookie 与存储，杜绝串号。
 */
public abstract class SlotActivity extends AppCompatActivity {

    public static class Slot0 extends SlotActivity {}
    public static class Slot1 extends SlotActivity {}
    public static class Slot2 extends SlotActivity {}
    public static class Slot3 extends SlotActivity {}

    private WebView webView;
    private ProgressBar progressBar;
    private MaterialButton btnToggleStyle;
    private boolean isWeChatStyleActive = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // API 28+ 多进程 WebView 必须给每个进程独立数据目录后缀,否则抛异常/串号
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            String pn = android.app.Application.getProcessName();
            if (pn != null && pn.contains(":slot")) {
                android.webkit.WebView.setDataDirectorySuffix(pn);
            }
        }
        setContentView(R.layout.activity_slot);

        ImageButton btnBack = findViewById(R.id.btn_back);
        TextView tvSlotTitle = findViewById(R.id.tv_slot_title);
        btnToggleStyle = findViewById(R.id.btn_toggle_style);
        MaterialButton btnExportMd = findViewById(R.id.btn_export_md);
        progressBar = findViewById(R.id.web_progress);
        webView = findViewById(R.id.slot_webview);

        String slotName = getIntent().getStringExtra("SLOT_NAME");
        if (slotName == null || slotName.isEmpty()) {
            slotName = "Hark 独立会话";
        }
        tvSlotTitle.setText(slotName);

        String slotId = getIntent().getStringExtra("SLOT_ID");
        if (slotId == null || slotId.isEmpty()) {
            slotId = "slot0";
        }
        final String currentSlotId = slotId;

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
        });

        // 切换微信绿排版
        btnToggleStyle.setOnClickListener(v -> toggleWeChatStyle());

        // 执行 Markdown 导出引擎
        btnExportMd.setOnClickListener(v -> runExportEngine());

        initWebView(currentSlotId);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(String currentSlotId) {
        // WebView 远程调试:USB 连接电脑后 chrome://inspect 可直接查看真实 DOM(样式/导出契约取证)
        android.webkit.WebView.setWebContentsDebuggingEnabled(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUserAgentString(settings.getUserAgentString() + " HarkFree/1.0");

        // 注册原生通信接口
        webView.addJavascriptInterface(new HarkNativeBridge(this), "HarkNativeBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成时注入气泡归属检测脚本与Token同步脚本
                injectBubbleObserver();
                injectTokenSync(currentSlotId);
                injectCleanLogin();
                if (isWeChatStyleActive) {
                    injectWeChatCss();
                }
            }
        });

        // 加载 Hark 官方网址
        webView.loadUrl("https://hark.com/");
    }

    /** 隐藏 hark.com 官方 App 推广横幅,让登录页更干净 */
    private void injectCleanLogin() {
        String script = "(function() {" +
                "try {" +
                "  document.querySelectorAll('a[href*=\"play.google.com\"]').forEach(function(a) {" +
                "    var el = a;" +
                "    for (var i = 0; i < 6 && el; i++) {" +
                "      var s = getComputedStyle(el);" +
                "      if (s.position === 'fixed' || s.position === 'sticky') { el.style.display = 'none'; break; }" +
                "      el = el.parentElement;" +
                "    }" +
                "  });" +
                "} catch(e) {}" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    private void injectTokenSync(String slotId) {
        String script = "(async function() {" +
                "  try {" +
                "    var resp = await fetch('/api/users/me', {credentials: 'include'});" +
                "    if(!resp.ok) return;" +
                "    var data = await resp.json();" +
                "    var meters = data.meters || {};" +
                "    var tokens = meters.harkTokens || {};" +
                "    var limit = tokens.dailyLimit || 0;" +
                "    var used = tokens.dailyUsed || 0;" +
                "    var reset = tokens.dailyResetsAt || '';" +
                "    if(window.HarkNativeBridge && window.HarkNativeBridge.onTokenUsageSync) {" +
                "      window.HarkNativeBridge.onTokenUsageSync('" + slotId + "', used, limit, reset);" +
                "    }" +
                "  } catch(e) {}" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    /**
     * 注入气泡定位脚本:API 文本匹配优先(准确),几何回退(兜底)
     */
    private void injectBubbleObserver() {
        String script = "(function() {" +
                "var HM = window.__harkFree = window.__harkFree || {};" +
                "function norm(s){return (s||'').replace(/\\s+/g,' ').trim().slice(0,120);}" +
                "function tagBubbles() {" +
                "  var W = window.innerWidth;" +
                "  var bubbles = document.querySelectorAll('[data-testid=message-bubble]');" +
                "  var map = HM.roleMap || {};" +
                "  for(var i=0; i<bubbles.length; i++) {" +
                "    var b = bubbles[i];" +
                "    var t = b.querySelector('[data-testid=message-text]') || b;" +
                "    var side = map[norm(t.innerText)];" +
                "    if(!side) {" +
                "      var rect = t.getBoundingClientRect();" +
                "      side = (rect.left > W * 0.25 && rect.right > W - 40) ? 'me' : 'ai';" +
                "    }" +
                "    b.setAttribute('data-hm-side', side);" +
                "  }" +
                "}" +
                "tagBubbles();" +
                "if(!HM.observer) { HM.observer = new MutationObserver(tagBubbles); HM.observer.observe(document.documentElement, {childList: true, subtree: true}); }" +
                "})();";
        webView.evaluateJavascript(script, null);
        fetchRoleMap();
    }

    /**
     * 拉取最近消息建立"文本→角色"映射:气泡左右不再靠几何猜拳
     */
    private void fetchRoleMap() {
        String script = "(async function() {" +
                "try {" +
                "  var HM = window.__harkFree = window.__harkFree || {};" +
                "  function norm(s){return (s||'').replace(/\\s+/g,' ').trim().slice(0,120);}" +
                "  var m = location.pathname.match(/^\\/project\\/([^\\/]+)/);" +
                "  var cid = m ? m[1] : null;" +
                "  if(!cid) { var r0 = await fetch('/api/conversations/main?timezone=' + encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone), {credentials:'include'}); if(r0.ok) cid = (await r0.json()).conversationId; }" +
                "  if(!cid) return;" +
                "  var r = await fetch('/api/messages/history?conversationId=' + encodeURIComponent(cid) + '&v=2', {credentials:'include'});" +
                "  if(!r.ok) return;" +
                "  var d = await r.json();" +
                "  var list = Array.isArray(d.messages) ? d.messages : Object.values(d.messages || {});" +
                "  var map = {};" +
                "  list.forEach(function(x){ if(x && x.content) map[norm(x.content)] = (x.role === 'user' ? 'me' : 'ai'); });" +
                "  HM.roleMap = map;" +
                "} catch(e) {}" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    /**
     * 切换微信绿排版样式
     */
    private void toggleWeChatStyle() {
        isWeChatStyleActive = !isWeChatStyleActive;
        btnToggleStyle.setText(isWeChatStyleActive ? "默认风格" : "微信绿");

        if (isWeChatStyleActive) {
            injectWeChatCss();
            Toast.makeText(this, "已开启微信沉浸式排版", Toast.LENGTH_SHORT).show();
        } else {
            webView.evaluateJavascript("document.documentElement.classList.remove('hm-wx');", null);
            Toast.makeText(this, "已恢复官方原生样式", Toast.LENGTH_SHORT).show();
        }
    }

    private void injectWeChatCss() {
        try {
            InputStream is = getAssets().open("wechat_style.css");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String css = new String(buffer, StandardCharsets.UTF_8);
            String encodedCss = android.util.Base64.encodeToString(css.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);

            String script = "(function() {" +
                    "document.documentElement.classList.add('hm-wx');" +
                    "var style = document.getElementById('hark-free-wx-style');" +
                    "if(!style) {" +
                    "  style = document.createElement('style');" +
                    "  style.id = 'hark-free-wx-style';" +
                    "  style.textContent = window.atob('" + encodedCss + "');" +
                    "  document.head.appendChild(style);" +
                    "}" +
                    "})();";
            webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行无损 Markdown 导出引擎
     */
    private void runExportEngine() {
        try {
            InputStream is = getAssets().open("export_engine.js");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String script = new String(buffer, StandardCharsets.UTF_8);
            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "正在抓取完整会话记录...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载导出引擎失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
