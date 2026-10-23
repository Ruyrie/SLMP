package com.zhimu.slmp.ai.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;
import com.zhimu.slmp.ai.util.StreamTextBuffer;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import java.util.ArrayList;
import java.util.List;

/**
 * K01 一轮问答在 ViewModel 中的可变状态（只在主线程读写）：提问内容、服务端轮次、本地阶段与已生成的文字。
 * 页面看到的是由它生成的不可变 {@link ChatItem}。
 */
final class ChatRound {

    enum Phase {
        /** 已完成（message 为完整轮次） */
        ANSWERED,
        /** 等待附图上传或首个片段（“正在思考…”） */
        WAITING,
        /** 逐段追加 */
        STREAMING,
        /** 用户点了“停止” */
        STOPPED,
        INTERRUPTED,
        TIMEOUT,
        FAILED,
        /** 开流前网络失败：提问下方“重试” */
        SEND_FAILED,
        /** 打开会话时另一台设备正在生成（ANSWERING），轮询 API-K08 */
        REMOTE_ANSWERING
    }

    final long localId;
    @NonNull
    AskInput input;
    @Nullable
    AiMessageVO message;
    @NonNull
    Phase phase;
    /** 服务端轮次 ID（meta 或已载入）；测试直连为负数；0 表示还没有 */
    long messageId;
    @NonNull
    final StreamTextBuffer buffer = new StreamTextBuffer();
    /** 状态行文案（error 事件的 message） */
    @Nullable
    String statusText;
    /** SEND_FAILED：发送时没有网络 */
    boolean noNetwork;
    @NonNull
    final List<CitationBriefVO> pendingCitations = new ArrayList<>();
    /** 提问时间（本地组装或服务端 asked_at） */
    @Nullable
    String askedAt;
    /** 每次变化加 1（ChatItem 比较用） */
    int version;
    /** 40901 同键重发次数 */
    int duplicateRetries;
    /** 从服务端载入的历史轮次（相邻两轮 batch_no 不同时插换批次提示） */
    boolean loaded;
    /** 服务端附图（载入的历史轮次） */
    @NonNull
    List<AskInput.Attach> remoteImages = new ArrayList<>();
    /** 发送时的附图管理：开流（meta）后释放；开流前撤回时换回输入栏 */
    @Nullable
    UploadManager uploads;
    @Nullable
    String uploadsKey;
    @Nullable
    Observer<List<UploadItem>> uploadObserver;

    ChatRound(long localId, @NonNull AskInput input, @NonNull Phase phase) {
        this.localId = localId;
        this.input = input;
        this.phase = phase;
    }

    /** 由服务端轮次生成（载入会话、K08 补拉） */
    @NonNull
    static ChatRound fromMessage(long localId, @NonNull AiMessageVO m, @NonNull AskInput input) {
        ChatRound r = new ChatRound(localId, input, phaseOf(m.getStatus()));
        r.apply(m);
        return r;
    }

    void apply(@NonNull AiMessageVO m) {
        message = m;
        messageId = m.getId();
        askedAt = m.getAskedAt();
        phase = phaseOf(m.getStatus());
        buffer.clear();
        if (m.getAnswerText() != null && phase != Phase.ANSWERED) {
            buffer.append(m.getAnswerText());
        }
        statusText = phase == Phase.ANSWERED ? null : m.getErrorMessage();
        version++;
    }

    @NonNull
    static Phase phaseOf(@Nullable String status) {
        if (AiCodes.STATUS_ANSWERED.equals(status)) {
            return Phase.ANSWERED;
        }
        if (AiCodes.STATUS_ANSWERING.equals(status)) {
            return Phase.REMOTE_ANSWERING;
        }
        if (AiCodes.STATUS_TIMEOUT.equals(status)) {
            return Phase.TIMEOUT;
        }
        if (AiCodes.STATUS_FAILED.equals(status)) {
            return Phase.FAILED;
        }
        return Phase.INTERRUPTED;
    }

    boolean isGenerating() {
        return phase == Phase.WAITING || phase == Phase.STREAMING;
    }

    /** 可以重新生成（停止、中断、超时、不可用） */
    boolean canRegenerate() {
        return messageId != 0 && (phase == Phase.STOPPED || phase == Phase.INTERRUPTED || phase == Phase.TIMEOUT
                || phase == Phase.FAILED);
    }

    void touch() {
        version++;
    }
}
