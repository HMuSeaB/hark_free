/**
 * HarkFree 会话无损导出引擎 (纯净版)
 * 提取当前会话或全部会话的历史记录，并生成高保真 Markdown 文本，交由 Android 原生保存。
 */
(async function() {
    var Bridge = window.HarkNativeBridge;
    function log(msg) {
        if (Bridge && Bridge.onExportProgress) {
            Bridge.onExportProgress(msg);
        }
    }

    async function apiJson(url) {
        var lastErr = null;
        // Cloudflare 对 WebView 偶发 403/503 质询,自动重试一次
        for (var attempt = 0; attempt < 2; attempt++) {
            try {
                var resp = await fetch(url, { credentials: 'include' });
                if (resp.status === 403 || resp.status === 503) {
                    throw new Error('HTTP ' + resp.status + ' (Cloudflare 质询,自动重试中) 当请求: ' + url.split('?')[0]);
                }
                if (!resp.ok) {
                    throw new Error('HTTP ' + resp.status + ' 当请求: ' + url.split('?')[0]);
                }
                return await resp.json();
            } catch (err) {
                lastErr = err;
                if (attempt === 0) {
                    await new Promise(function(r) { setTimeout(r, 1500); });
                }
            }
        }
        throw lastErr;
    }

    function formatTime(t) {
        var d = new Date(t || Date.now());
        var pad = function(n) { return (n < 10 ? '0' : '') + n; };
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
               pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    async function fetchHistory(convId, title) {
        var messages = [];
        var seen = {};
        var cursor = null;
        var page = 0;

        while (page < 300) {
            var url = '/api/messages/history?conversationId=' + encodeURIComponent(convId) + '&v=2';
            if (cursor) {
                url += '&before=' + encodeURIComponent(cursor.id) + '&beforeCreatedAt=' + cursor.createdAt;
            }

            // 失败必须上抛:静默中断会让用户只看到"已读取 0 条"
            var data = await apiJson(url);

            page++;
            var list = Array.isArray(data.messages) ? data.messages : Object.values(data.messages || {});
            list.forEach(function(m) {
                if (!seen[m.id]) {
                    seen[m.id] = true;
                    messages.push(m);
                }
            });

            log((title || '会话') + '：已读取 ' + messages.length + ' 条消息');

            var older = data.older || { cursor: data.nextCursor, hasMore: data.hasMore };
            if (!older || !older.hasMore || !older.cursor || (cursor && older.cursor.id === cursor.id)) {
                break;
            }
            cursor = older.cursor;
        }

        // 按时间从早到晚排序
        return messages.sort(function(a, b) {
            return (a.timestamp || 0) - (b.timestamp || 0);
        });
    }

    // 过滤规则与 HarkMulti 已验证实现一致:跳过流式占位/任务态消息
    function keep(m) {
        if (m.role === 'user') return true;
        if (m.role !== 'assistant') return false;
        if (m.jobStatus || m.isStreaming) return false;
        return !!(m.content && m.content.trim()) || (m.attachments && m.attachments.length);
    }

    function buildMarkdown(title, convId, msgs) {
        var lines = [
            '# ' + (title || 'Hark 对话记录'),
            '',
            '> 会话 ID：`' + convId + '`  ',
            '> 导出时间：' + formatTime(Date.now()) + '  ',
            '> 消息数量：共 ' + msgs.length + ' 条',
            '',
            '---',
            ''
        ];

        msgs.forEach(function(m) {
            var role = (m.role === 'user') ? '🧑 我' : '✦ Hark AI';
            var time = formatTime(m.timestamp || m.createdAt);

            lines.push('### ' + role + ' · ' + time);
            lines.push('');
            if (m.content && m.content.trim()) {
                lines.push(m.content.trim());
                lines.push('');
            }

            if (m.attachments && m.attachments.length) {
                m.attachments.forEach(function(att) {
                    var name = att.name || att.filename || '附件';
                    var url = att.downloadUrl || att.url || '';
                    lines.push('📎 **附件**: [' + name + '](' + url + ')');
                });
                lines.push('');
            }

            lines.push('---');
            lines.push('');
        });

        return lines.join('\n');
    }

    try {
        log('正在检测当前会话信息...');
        var path = location.pathname;
        var match = path.match(/\/project\/([^\/]+)/);
        var targetConvId = match ? match[1] : null;

        var mainConv = await apiJson('/api/conversations/main?timezone=' + encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone));
        var mainId = mainConv.conversationId || (mainConv.conversation && mainConv.conversation.id);

        if (!targetConvId) {
            targetConvId = mainId;
        }

        var convListResp = await apiJson('/api/conversations');
        var convs = convListResp.conversations || [];
        var matched = convs.find(function(c) { return c.id === targetConvId; });
        var title = matched ? (matched.title || 'Hark会话') : (targetConvId === mainId ? '主聊天' : 'Hark会话');

        var msgs = await fetchHistory(targetConvId, title);
        msgs = msgs.filter(keep);
        if (!msgs.length) {
            throw new Error('未读取到任何有效消息');
        }

        var mdContent = buildMarkdown(title, targetConvId, msgs);
        var safeFilename = (title + '_' + formatTime(Date.now()).replace(/[: ]/g, '-'))
            .replace(/[\\/:*?"<>|]/g, '_') + '.md';

        if (Bridge && Bridge.onExportSuccess) {
            Bridge.onExportSuccess(safeFilename, mdContent, msgs.length);
        }
    } catch (e) {
        if (Bridge && Bridge.onExportFailed) {
            Bridge.onExportFailed(String(e && e.message || e));
        }
    }
})();
