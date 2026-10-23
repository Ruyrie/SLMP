package com.zhimu.server.common.error;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常：由全局异常处理转为 {code, message, data, trace_id}，HTTP 状态取错误码前三位。
 * <p>
 * data 用于带结构化信息的错误码，例如 40307 的 reason、42206 的 need_confirm、42901 的 cooldown_seconds、
 * 40309 的 next_step、40104 的 reason。键名用 snake_case。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object data;

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message, null);
    }

    public BizException(ErrorCode errorCode, String message, Object data) {
        this(errorCode.getCode(), message, data);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /** 返回一个带 data 的新异常，data 为键值对（键用 snake_case），例如 with("reason", "EXPIRED") */
    public BizException with(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (data instanceof Map<?, ?> old) {
            old.forEach((k, v) -> map.put(String.valueOf(k), v));
        }
        map.put(key, value);
        return new BizException(code, getMessage(), map);
    }

    public int getHttpStatus() {
        return ErrorCode.httpStatusOf(code);
    }
}
