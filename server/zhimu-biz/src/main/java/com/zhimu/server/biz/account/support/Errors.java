package com.zhimu.server.biz.account.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收集多个字段错误，一次性返回 40001。
 * <p>
 * 模块01 文档与《服务端开发约定》7.1 第 6 条：field_errors 只用页面上实际有的输入框字段键；
 * device_id、device_model、os_version、scene 等非输入框字段用 {@link #messageOnly(String)}，
 * 只把文案放进 message，不放进 field_errors。
 */
public final class Errors {

    private final List<FieldErrorVO> fieldErrors = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private String reason;

    public Errors add(String field, String message) {
        if (message != null) {
            fieldErrors.add(new FieldErrorVO(field, message));
        }
        return this;
    }

    /** 非输入框字段的错误：只进 message，不进 field_errors */
    public Errors messageOnly(String message) {
        if (message != null) {
            messages.add(message);
        }
        return this;
    }

    public Errors reason(String reason) {
        this.reason = reason;
        return this;
    }

    public boolean hasAny() {
        return !fieldErrors.isEmpty() || !messages.isEmpty();
    }

    public List<FieldErrorVO> list() {
        return fieldErrors;
    }

    /** 没有错误时不做任何事 */
    public void throwIfAny() {
        if (!hasAny()) {
            return;
        }
        throw toException();
    }

    public static BizException fail(String field, String message) {
        return new Errors().add(field, message).toException();
    }

    public BizException toException() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (reason != null) {
            data.put("reason", reason);
        }
        if (!fieldErrors.isEmpty()) {
            data.put("field_errors", fieldErrors);
        }
        String message;
        if (!fieldErrors.isEmpty()) {
            message = fieldErrors.get(0).getMessage();
        } else if (!messages.isEmpty()) {
            message = messages.get(0);
        } else {
            message = ErrorCode.PARAM_INVALID.getDefaultMessage();
        }
        return new BizException(ErrorCode.PARAM_INVALID, message, data.isEmpty() ? null : data);
    }
}
