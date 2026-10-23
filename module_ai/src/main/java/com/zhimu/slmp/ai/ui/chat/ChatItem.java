package com.zhimu.slmp.ai.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.common.model.vo.FileVO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * K01 对话列表项（不可变，AiMessageAdapter 用 DiffUtil 比较 id 与 version）：
 * 时间分隔、提问、回答、系统提示（换批次、关联失效）、10 轮分隔。
 */
public final class ChatItem {

    public enum Type {
        TIME,
        QUESTION,
        ANSWER,
        TIP,
        DIVIDER
    }

    /** 回答的显示阶段 */
    public enum AnswerState {
        DONE,
        THINKING,
        STREAMING,
        STOPPED,
        /** 中断、超时、不可用：error 色状态行，整行可点重新生成 */
        ERROR,
        /** 发送失败（网络）：提问下方“重试” */
        SEND_FAILED
    }

    @NonNull
    public final Type type;
    public final long id;
    public final long roundId;
    public final int version;
    /** TIME / TIP / DIVIDER 的文字；QUESTION 的问题文字 */
    @Nullable
    public final String text;
    /** QUESTION：本地附图（路径或 URI） */
    @NonNull
    public final List<String> localImages;
    /** QUESTION：服务端附图 */
    @NonNull
    public final List<FileVO> remoteImages;
    /** ANSWER */
    @Nullable
    public final AnswerState state;
    @Nullable
    public final AiMessageVO message;
    /** ANSWER：未完成时已生成的文字（去掉了 [^n]） */
    @Nullable
    public final String partialText;
    /** ANSWER：状态行文案 */
    @Nullable
    public final String statusText;
    /** 其他轮次正在生成时为 false（38% 透明度，点按无效） */
    public final boolean actionsEnabled;

    private ChatItem(@NonNull Type type, long id, long roundId, int version, @Nullable String text,
                     @Nullable List<String> localImages, @Nullable List<FileVO> remoteImages,
                     @Nullable AnswerState state, @Nullable AiMessageVO message, @Nullable String partialText,
                     @Nullable String statusText, boolean actionsEnabled) {
        this.type = type;
        this.id = id;
        this.roundId = roundId;
        this.version = version;
        this.text = text;
        this.localImages = localImages == null ? Collections.emptyList() : localImages;
        this.remoteImages = remoteImages == null ? Collections.emptyList() : remoteImages;
        this.state = state;
        this.message = message;
        this.partialText = partialText;
        this.statusText = statusText;
        this.actionsEnabled = actionsEnabled;
    }

    @NonNull
    static ChatItem time(long id, @NonNull String text) {
        return new ChatItem(Type.TIME, id, 0, 0, text, null, null, null, null, null, null, true);
    }

    @NonNull
    static ChatItem tip(long id, @NonNull String text) {
        return new ChatItem(Type.TIP, id, 0, 0, text, null, null, null, null, null, null, true);
    }

    @NonNull
    static ChatItem divider(long id) {
        return new ChatItem(Type.DIVIDER, id, 0, 0, com.zhimu.slmp.ai.util.AiText.CONTEXT_DIVIDER, null, null, null,
                null, null, null, true);
    }

    @NonNull
    static ChatItem question(long id, long roundId, int version, @Nullable String text, @NonNull List<String> local,
                             @NonNull List<FileVO> remote) {
        return new ChatItem(Type.QUESTION, id, roundId, version, text, local, remote, null, null, null, null, true);
    }

    @NonNull
    static ChatItem answer(long id, long roundId, int version, @NonNull AnswerState state,
                           @Nullable AiMessageVO message, @Nullable String partialText, @Nullable String statusText,
                           boolean actionsEnabled) {
        return new ChatItem(Type.ANSWER, id, roundId, version, null, null, null, state, message, partialText,
                statusText, actionsEnabled);
    }

    /** 内容是否相同（DiffUtil） */
    boolean sameContent(@NonNull ChatItem o) {
        return type == o.type && version == o.version && actionsEnabled == o.actionsEnabled
                && state == o.state && Objects.equals(text, o.text) && Objects.equals(statusText, o.statusText)
                && Objects.equals(partialText, o.partialText);
    }
}
