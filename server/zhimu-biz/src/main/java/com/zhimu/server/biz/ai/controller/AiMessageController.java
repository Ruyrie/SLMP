package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.request.AiAskRequest;
import com.zhimu.server.biz.ai.request.AiRatingRequest;
import com.zhimu.server.biz.ai.service.AiAskService;
import com.zhimu.server.biz.ai.service.AiMessageService;
import com.zhimu.server.biz.ai.service.AiSpeechService;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.RatingResultVO;
import com.zhimu.server.biz.ai.vo.SpeechResultVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import com.zhimu.server.framework.web.WebHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API-K06–K10：提问（SSE）、重新生成（SSE）、单轮问答、评价、语音转文字。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@RequireFarm
public class AiMessageController {

    private final AiAskService askService;
    private final AiMessageService messageService;
    private final AiSpeechService speechService;

    /** K06 提问（SSE）；开流前的错误返回普通 ApiResponse */
    @PostMapping(value = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "ai-ask", limit = 6)
    public SseEmitter ask(@RequestHeader(value = WebHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                          @RequestBody(required = false) AiAskRequest request) {
        return askService.ask(UserContext.requireUserId(), idempotencyKey,
                request == null ? new AiAskRequest() : request);
    }

    /** K07 重新生成（SSE） */
    @PostMapping(value = "/messages/{id:\\d+}/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "ai-ask", limit = 6)
    public SseEmitter retry(@PathVariable("id") Long id) {
        return askService.retry(UserContext.requireUserId(), id);
    }

    /** K08 单轮问答 */
    @GetMapping("/messages/{id:\\d+}")
    public ApiResponse<AiMessageVO> message(@PathVariable("id") Long id) {
        return ApiResponse.ok(messageService.getMessage(UserContext.requireUserId(), id));
    }

    /** K09 评价（null 为取消评价） */
    @PutMapping("/messages/{id:\\d+}/rating")
    public ApiResponse<RatingResultVO> rate(@PathVariable("id") Long id,
                                            @RequestBody(required = false) AiRatingRequest request) {
        return ApiResponse.ok(messageService.rate(UserContext.requireUserId(), id,
                request == null ? null : request.getRating()));
    }

    /** K10 语音转文字 */
    @PostMapping(value = "/speech-recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(key = "ai-asr", limit = 20)
    public ApiResponse<SpeechResultVO> recognize(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "duration_ms", required = false) Integer durationMs) {
        return ApiResponse.ok(speechService.recognize(UserContext.requireUserId(), audio, durationMs));
    }
}
