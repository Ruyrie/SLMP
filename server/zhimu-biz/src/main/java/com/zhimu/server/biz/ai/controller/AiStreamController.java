package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.request.AiAskRequest;
import com.zhimu.server.biz.ai.service.AiAskService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.SseWriter;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import com.zhimu.server.framework.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * API-K06、K07：提问与重新生成（SSE）。
 * <p>
 * 这两个接口的任何错误（参数、限流、养殖场状态、消息状态、服务异常）都写成现有的 SSE error 事件
 * （HTTP 200，event:error，data 为 {code, message, status}），不经过全局 JSON 异常处理：
 * 客户端 Accept 为 text/event-stream，JSON 错误体协商失败会变成 500。
 * 登录失效类（401xx）与强制步骤（40309）仍按真实 HTTP 状态返回 JSON，保证客户端的续期与跳转逻辑生效。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@RequireFarm
public class AiStreamController {

    private static final JsonMapper JSON = ZhimuJackson.newHttpMapper();
    private static final String MSG_BAD_FORMAT = "请求格式不正确";

    private final AiAskService askService;

    /** K06 提问（SSE） */
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

    /** 本控制器（含拦截器、限流切面）抛出的错误统一写成 SSE error 事件 */
    @ExceptionHandler(Exception.class)
    public void handleStreamError(Exception e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (e instanceof AsyncRequestNotUsableException) {
            log.debug("客户端已断开 {}", e.getMessage());
            return;
        }
        int code;
        String message;
        if (e instanceof BizException biz) {
            code = biz.getCode();
            message = biz.getMessage();
            if (biz.getHttpStatus() >= 500) {
                log.warn("AI 流式接口业务异常 {} {}", code, message);
            } else {
                log.debug("AI 流式接口业务异常 {} {}", code, message);
            }
        } else if (e instanceof HttpMessageNotReadableException || e instanceof HttpMediaTypeNotSupportedException
                || e instanceof ServletRequestBindingException || e instanceof MethodArgumentTypeMismatchException) {
            code = ErrorCode.PARAM_INVALID.getCode();
            message = MSG_BAD_FORMAT;
        } else if (e instanceof AsyncRequestTimeoutException) {
            code = ErrorCode.AI_TIMEOUT.getCode();
            message = "回答超时，点按重新生成";
        } else {
            log.error("AI 流式接口异常 trace_id={} {} {}", TraceContext.get(), request.getMethod(),
                    request.getRequestURI(), e);
            code = ErrorCode.SERVER_ERROR.getCode();
            message = "回答生成失败，点按重新生成";
        }
        if (response.isCommitted()) {
            // 已开流：错误事件由生成线程写出，这里不再追加
            return;
        }
        response.resetBuffer();
        if (passThroughAsJson(code)) {
            response.setStatus(ErrorCode.httpStatusOf(code));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JSON.writeValueAsString(ApiResponse.fail(code, message,
                    e instanceof BizException biz ? biz.getData() : null)));
            response.flushBuffer();
            return;
        }
        String status = code == ErrorCode.AI_TIMEOUT.getCode() ? AiCodes.STATUS_TIMEOUT : AiCodes.STATUS_FAILED;
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.getWriter().write(SseWriter.errorEventText(code, message, status));
        response.flushBuffer();
    }

    /** 登录失效与强制步骤由客户端网络层统一处理（续期、跳转），保持 HTTP 状态 + JSON */
    static boolean passThroughAsJson(int code) {
        return ErrorCode.httpStatusOf(code) == 401 || code == ErrorCode.FORCED_STEP_REQUIRED.getCode();
    }
}
