package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.vo.SpeechResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * API-K10 语音转文字。
 */
public interface AiSpeechService {

    SpeechResultVO recognize(Long userId, MultipartFile audio, Integer durationMs);
}
