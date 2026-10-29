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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private volatile Runnable disconnectListener;
    private final AtomicBoolean disconnected = new AtomicBoolean();

    public SseWriter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** 客户端断开（写出失败、容器回调出错）时回调一次；用于取消上游生成并保留已生成部分 */
    public void onDisconnect(Runnable listener) {
        this.disconnectListener = listener;
    }

    public void startHeartbeat() {
        heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> {
            if (closed) {
                return;
            }
            try {
                synchronized (this) {
                    if (!closed) {
                        emitter.send(SseEmitter.event().comment("ping"));
                    }
                }
            } catch (Exception e) {
                markDisconnected();
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized void send(String event, Object data) {
        if (closed) {
            return;
        }
        String json;
        try {
            json = JSON.writeValueAsString(data);
        } catch (RuntimeException e) {
            log.warn("SSE 事件序列化失败 event={}", event, e);
            return;
        }
        try {
            // 用 text/plain 写出 JSON 文本，避免 Jackson 把 String 再序列化成带引号的字符串
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.debug("SSE 发送失败，客户端可能已断开 event={}", event);
            markDisconnected();
        }
    }

    /**
     * 客户端已断开：停止写出与心跳，回调断开监听（只回调一次）。
     * 监听里会取消上游生成并把消息记为“已停止生成”。
     */
    public void markDisconnected() {
        closed = true;
        stopHeartbeat();
        if (disconnected.compareAndSet(false, true)) {
            Runnable listener = disconnectListener;
            if (listener != null) {
                try {
                    listener.run();
                } catch (RuntimeException e) {
                    log.warn("SSE 断开回调失败", e);
                }
            }
        }
    }

    public synchronized void error(int code, String message, String status) {
        send(AiCodes.SSE_ERROR, errorOf(code, message, status));
        complete();
    }

    /** error 事件的 data（开流前的错误也按这个格式写出，见 AiStreamController） */
    public static StreamErrorVO errorOf(int code, String message, String status) {
        StreamErrorVO vo = new StreamErrorVO();
        vo.setCode(code);
        vo.setMessage(message);
        vo.setStatus(status);
        return vo;
    }

    /** 一条完整的 error 事件文本（event 行、data 行、空行），给开流前的错误直接写响应用 */
    public static String errorEventText(int code, String message, String status) {
        return "event:" + AiCodes.SSE_ERROR + "\ndata:" + JSON.writeValueAsString(errorOf(code, message, status))
                + "\n\n";
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
