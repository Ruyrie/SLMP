package com.zhimu.slmp.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;
import com.zhimu.slmp.ai.model.vo.StreamCitationVO;
import com.zhimu.slmp.ai.model.vo.StreamDeltaVO;
import com.zhimu.slmp.ai.model.vo.StreamDoneVO;
import com.zhimu.slmp.ai.model.vo.StreamErrorVO;
import com.zhimu.slmp.ai.model.vo.StreamMetaVO;
import com.zhimu.slmp.common.ai.AiSseEvent;

import java.util.Collections;
import java.util.List;

/**
 * K01 流式回答的一个事件（API-K06 / K07 的 SSE，或测试直连千问时由客户端按同一格式生成）：
 * meta、delta、citation、done、error；心跳注释行与推理中的片段为 HEARTBEAT（只用于重置 45 秒看门狗）。
 */
public final class AiStreamEvent {

    public enum Type {
        META,
        DELTA,
        CITATION,
        DONE,
        ERROR,
        HEARTBEAT
    }

    @NonNull
    private final Type type;
    @Nullable
    private final StreamMetaVO meta;
    @Nullable
    private final String text;
    @Nullable
    private final List<CitationBriefVO> citations;
    @Nullable
    private final StreamDoneVO done;
    @Nullable
    private final StreamErrorVO error;

    private AiStreamEvent(@NonNull Type type, @Nullable StreamMetaVO meta, @Nullable String text,
                          @Nullable List<CitationBriefVO> citations, @Nullable StreamDoneVO done,
                          @Nullable StreamErrorVO error) {
        this.type = type;
        this.meta = meta;
        this.text = text;
        this.citations = citations;
        this.done = done;
        this.error = error;
    }

    @NonNull
    public static AiStreamEvent meta(@NonNull StreamMetaVO meta) {
        return new AiStreamEvent(Type.META, meta, null, null, null, null);
    }

    @NonNull
    public static AiStreamEvent delta(@NonNull String text) {
        return new AiStreamEvent(Type.DELTA, null, text, null, null, null);
    }

    @NonNull
    public static AiStreamEvent citation(@NonNull List<CitationBriefVO> citations) {
        return new AiStreamEvent(Type.CITATION, null, null, citations, null, null);
    }

    @NonNull
    public static AiStreamEvent done(@NonNull StreamDoneVO done) {
        return new AiStreamEvent(Type.DONE, null, null, null, done, null);
    }

    @NonNull
    public static AiStreamEvent error(@NonNull StreamErrorVO error) {
        return new AiStreamEvent(Type.ERROR, null, null, null, null, error);
    }

    @NonNull
    public static AiStreamEvent error(int code, @NonNull String message, @NonNull String status) {
        StreamErrorVO e = new StreamErrorVO();
        e.setCode(code);
        e.setMessage(message);
        e.setStatus(status);
        return error(e);
    }

    @NonNull
    public static AiStreamEvent heartbeat() {
        return new AiStreamEvent(Type.HEARTBEAT, null, null, null, null, null);
    }

    /**
     * 服务端 SSE 事件转换（data 为 JSON）；注释行为 HEARTBEAT；未知事件名返回 null（忽略）。
     *
     * @throws com.google.gson.JsonParseException data 不是合法 JSON
     */
    @Nullable
    public static AiStreamEvent parse(@NonNull Gson gson, @NonNull AiSseEvent e) {
        if (e.isComment()) {
            return heartbeat();
        }
        String name = e.getEvent();
        if (name == null) {
            return null;
        }
        switch (name) {
            case AiCodes.SSE_META:
                return meta(gson.fromJson(e.getData(), StreamMetaVO.class));
            case AiCodes.SSE_DELTA: {
                StreamDeltaVO d = gson.fromJson(e.getData(), StreamDeltaVO.class);
                return delta(d == null || d.getText() == null ? "" : d.getText());
            }
            case AiCodes.SSE_CITATION: {
                StreamCitationVO c = gson.fromJson(e.getData(), StreamCitationVO.class);
                return citation(c == null ? Collections.emptyList() : c.getCitations());
            }
            case AiCodes.SSE_DONE:
                return done(gson.fromJson(e.getData(), StreamDoneVO.class));
            case AiCodes.SSE_ERROR:
                return error(gson.fromJson(e.getData(), StreamErrorVO.class));
            default:
                return null;
        }
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @Nullable
    public StreamMetaVO getMeta() {
        return meta;
    }

    @NonNull
    public String getText() {
        return text == null ? "" : text;
    }

    @NonNull
    public List<CitationBriefVO> getCitations() {
        return citations == null ? Collections.emptyList() : citations;
    }

    @Nullable
    public StreamDoneVO getDone() {
        return done;
    }

    @Nullable
    public StreamErrorVO getError() {
        return error;
    }

    @NonNull
    @Override
    public String toString() {
        return "AiStreamEvent(" + type + ")";
    }
}
