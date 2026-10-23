package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.vo.SpeechResultVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.ai.AiException;
import com.zhimu.slmp.common.ai.AiSpeechClient;

import java.io.File;

import io.reactivex.rxjava3.core.Single;

/**
 * K01 语音转文字：测试直连阿里云百炼录音识别（{@link AiSpeechClient}，debug 包配置了 Key 时），否则走服务端 API-K10。
 * 直连的失败统一转为与服务端相同的错误码：网络 -1、服务不可用 50201，由 ViewModel 按码给提示。
 */
public interface SpeechEngine {

    @NonNull
    Single<SpeechResultVO> recognize(@NonNull File wav, int durationMs);

    /** 按配置选择 */
    @NonNull
    static SpeechEngine create(@NonNull AiRepository repository, @NonNull AiSpeechClient client) {
        if (!client.isEnabled()) {
            return repository::recognize;
        }
        return (wav, durationMs) -> client.recognize(wav)
                .map(r -> {
                    SpeechResultVO vo = new SpeechResultVO();
                    vo.setRecognized(r.isRecognized());
                    vo.setText(r.getText());
                    vo.setDurationMs(durationMs);
                    return vo;
                })
                .onErrorResumeNext(e -> {
                    if (e instanceof AiException && ((AiException) e).getKind() == AiException.Kind.NETWORK) {
                        return Single.error(ApiException.network(e));
                    }
                    return Single.error(new ApiException(ErrorCodes.AI_UNAVAILABLE, "语音识别暂不可用，请稍后再试"));
                });
    }
}
