package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.ai.service.AiSpeechService;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.vo.SpeechResultVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.integration.ai.AiGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * API-K10：校验录音并调用 AiGateway.recognizeSpeech。
 */
@Service
@RequiredArgsConstructor
public class AiSpeechServiceImpl implements AiSpeechService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final AiGateway aiGateway;

    @Override
    public SpeechResultVO recognize(Long userId, MultipartFile audio, Integer durationMs) {
        if (audio == null || audio.isEmpty()) {
            throw AiErrors.fieldError("audio", "请录制语音后再试");
        }
        if (durationMs == null || durationMs < 1000) {
            throw AiErrors.fieldError("duration_ms", "说话时间太短");
        }
        if (durationMs > 61000) {
            throw AiErrors.fieldError("duration_ms", "只支持 m4a 或 wav 录音，不超过 60 秒");
        }
        if (audio.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 m4a 或 wav 录音，不超过 60 秒");
        }
        String mime = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase(Locale.ROOT);
        String name = audio.getOriginalFilename() == null ? "" : audio.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean supported = mime.contains("wav") || mime.contains("mp4") || mime.contains("m4a")
                || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".mp4");
        if (!supported) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 m4a 或 wav 录音，不超过 60 秒");
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_INVALID, "录音读取失败，请重新录制");
        }
        if (!aiGateway.isEnabled()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "语音识别暂不可用，请稍后再试");
        }
        String text;
        try {
            text = aiGateway.recognizeSpeech(bytes, mime.isBlank() ? "audio/wav" : mime);
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.AI_TIMEOUT.getCode()) {
                throw new BizException(ErrorCode.AI_TIMEOUT, "没有听清，请再说一遍");
            }
            if (e.getCode() == ErrorCode.AI_UNAVAILABLE.getCode()) {
                throw new BizException(ErrorCode.AI_UNAVAILABLE, "语音识别暂不可用，请稍后再试");
            }
            throw e;
        }
        String normalized = text == null ? "" : text.trim();
        SpeechResultVO vo = new SpeechResultVO();
        vo.setRecognized(!normalized.isEmpty());
        vo.setText(normalized.isEmpty() ? null : normalized);
        vo.setDurationMs(durationMs);
        return vo;
    }
}
