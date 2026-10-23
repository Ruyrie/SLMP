package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okio.Buffer;
import okio.BufferedSource;

/**
 * 按行读取 text/event-stream（04 号 9.4 节、OpenAI 兼容流式接口共用）：
 * <ul>
 *     <li>Okio 按字节缓冲到换行再按 UTF-8 解码，网络分块落在中文字符中间也不会截断；\r\n 与 \n 都按行尾处理；</li>
 *     <li>event 行记事件名，data 行累加（多行以 \n 连接），空行派发一个事件；id、retry 等字段忽略；</li>
 *     <li>冒号开头的注释行（服务端心跳）在两个事件之间时单独派发为 {@link AiSseEvent#isComment()}；</li>
 *     <li>流结束时还有未派发的 data（最后一个事件后没有空行）也派发一次，之后返回 null。</li>
 * </ul>
 * 只在 io 线程调用；读取阻塞，取消请求（Call.cancel）后抛出 IOException。
 */
public final class AiSseReader {

    @NonNull
    private final BufferedSource source;

    public AiSseReader(@NonNull BufferedSource source) {
        this.source = source;
    }

    /** 下一个事件；流已结束返回 null */
    @WorkerThread
    @Nullable
    public AiSseEvent next() throws IOException {
        String event = null;
        StringBuilder data = null;
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                return data != null || event != null
                        ? new AiSseEvent(event, data == null ? "" : data.toString(), false) : null;
            }
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                if (data != null || event != null) {
                    return new AiSseEvent(event, data == null ? "" : data.toString(), false);
                }
                continue;
            }
            if (line.charAt(0) == ':') {
                if (data == null && event == null) {
                    return new AiSseEvent(null, line.substring(1).trim(), true);
                }
                continue;
            }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if ("event".equals(field)) {
                event = value;
            } else if ("data".equals(field)) {
                if (data == null) {
                    data = new StringBuilder(value.length());
                } else {
                    data.append('\n');
                }
                data.append(value);
            }
        }
    }

    /** 一次读完整段文本（单元测试与 Mock 回放用） */
    @NonNull
    public static List<AiSseEvent> readAll(@NonNull String text) throws IOException {
        Buffer buffer = new Buffer().writeUtf8(text);
        AiSseReader reader = new AiSseReader(buffer);
        List<AiSseEvent> list = new ArrayList<>();
        AiSseEvent e;
        while ((e = reader.next()) != null) {
            list.add(e);
        }
        return list;
    }
}
