package com.zhimu.server.integration.ai;

import com.zhimu.server.integration.support.HttpClients;

/**
 * 一次上游调用失败（网关内部使用）。retryable 决定能否换备用服务商。message 不含 Key 与请求内容。
 */
final class UpstreamException extends RuntimeException {

    final String provider;
    final String model;
    /** HTTP 状态码；网络错误为 -1 */
    final int httpStatus;
    final boolean retryable;
    final boolean timeout;

    private UpstreamException(Target target, int httpStatus, boolean retryable, boolean timeout, String reason,
                              Throwable cause) {
        super(reason, cause, false, false);
        this.provider = target.name();
        this.model = target.model();
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.timeout = timeout;
    }

    /** 5xx、429、408 可换备用；其余 4xx（Key 无效、参数错误、内容审核等）不换 */
    static UpstreamException http(Target target, int status, String summary) {
        boolean retryable = status >= 500 || status == 429 || status == 408;
        return new UpstreamException(target, status, retryable, false, "HTTP " + status + " " + summary, null);
    }

    static UpstreamException network(Target target, Throwable cause) {
        boolean timeout = HttpClients.isTimeout(cause);
        String reason = (timeout ? "超时：" : "网络错误：") + cause.getClass().getSimpleName();
        return new UpstreamException(target, -1, true, timeout, reason, cause);
    }

    static UpstreamException emptyAnswer(Target target, String finishReason) {
        return new UpstreamException(target, 200, true, false, "回答为空（finish_reason=" + finishReason + "）", null);
    }

    static UpstreamException badResponse(Target target, int status) {
        return new UpstreamException(target, status, true, false, "响应无法解析", null);
    }

    static UpstreamException streamError(Target target, String code) {
        return new UpstreamException(target, 200, true, false, "流中途出错：" + code, null);
    }

    /** 一个服务商与本次使用的模型 */
    record Target(AiProperties.Provider provider, String model, boolean fallback) {

        String name() {
            return provider.getName();
        }

        String url() {
            return provider.chatCompletionsUrl();
        }
    }
}
