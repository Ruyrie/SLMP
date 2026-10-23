package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;

import java.util.List;

/**
 * K01 回答来源：服务端（API-K06 / K07，{@link ServerAnswerEngine}）或测试直连千问（{@link DirectAnswerEngine}，
 * debug 包配置了 Key 时使用，2026-10-22 用户确认）。两者都输出同一格式的 {@link AiStreamEvent}，ViewModel 不区分来源。
 * <p>
 * 开流前的错误：服务端为 ApiException（42901、40001 等）；网络失败为 ApiException.network。
 */
public interface AnswerEngine {

    /** 测试直连（轮次只在本机，评价与重新生成不调服务端） */
    boolean isDirect();

    /**
     * 提问。
     *
     * @param history 本会话参与上下文的已完成轮次（按时间顺序，最多 10 轮；服务端模式忽略，由服务端组装）
     */
    @NonNull
    io.reactivex.rxjava3.core.Flowable<AiStreamEvent> ask(@NonNull AskInput input, @NonNull List<AiMessageVO> history);

    /** 重新生成（服务端 API-K07；直连用原问题重新请求） */
    @NonNull
    io.reactivex.rxjava3.core.Flowable<AiStreamEvent> regenerate(long messageId, @NonNull AskInput input,
                                                                 @NonNull List<AiMessageVO> history);
}
