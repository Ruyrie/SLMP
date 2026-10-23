package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

/**
 * 服务端回答（API-K06 提问带 Idempotency-Key、API-K07 重新生成）；release 包与未配置 Key 的 debug 包使用（debug 由 Mock 回放）。
 */
public class ServerAnswerEngine implements AnswerEngine {

    private final AiRepository repository;

    public ServerAnswerEngine(@NonNull AiRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @NonNull
    @Override
    public Flowable<AiStreamEvent> ask(@NonNull AskInput input, @NonNull List<AiMessageVO> history) {
        return repository.ask(input.toRequest(), input.getIdemKey());
    }

    @NonNull
    @Override
    public Flowable<AiStreamEvent> regenerate(long messageId, @NonNull AskInput input,
                                              @NonNull List<AiMessageVO> history) {
        return repository.retry(messageId);
    }
}
