package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.StreamErrorVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 写出封装：事件名 + JSON data，每 15 秒发送注释行心跳；客户端断开后静默停止。
 * 事件格式与 App 的 AiSseReader 对齐：event 行、data 行、空行分隔；结束标记不做 [DONE]（模块11 用 done 事件）。
 */
@Slf4j
public final class SseWriter {

    private static final JsonMapper JSON = ZhimuJackson.newHttpMapper();
    private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "zmt-ai-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private final SseEmitter emitter;
    private volatile boolean closed;
    private ScheduledFuture<?> heartbeat;

    public SseWriter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void startHeartbeat() {
        heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                stopHeartbeat();
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized void send(String event, Object data) {
        if (closed) {
            return;
        }
        try {
            String json = JSON.writeValueAsString(data);
            // 用 text/plain 写出 JSON 文本，避免 Jackson 把 String 再序列化成带引号的字符串
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            closed = true;
            stopHeartbeat();
            log.debug("SSE 发送失败，客户端可能已断开 event={}", event);
        }
    }

    public synchronized void error(int code, String message, String status) {
        StreamErrorVO vo = new StreamErrorVO();
        vo.setCode(code);
        vo.setMessage(message);
        vo.setStatus(status);
        send(AiCodes.SSE_ERROR, vo);
        complete();
    }

    public synchronized void complete() {
        closed = true;
        stopHeartbeat();
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 已结束或客户端已断开
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> current = heartbeat;
        if (current != null) {
            current.cancel(false);
            heartbeat = null;
        }
    }
}
