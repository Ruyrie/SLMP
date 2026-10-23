package com.zhimu.server.biz.account.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 收集多个字段错误，一次性返回 40001。 */
public final class Errors {

    private final List<FieldErrorVO> fieldErrors = new ArrayList<>();
    private String reason;

    public Errors add(String field, String message) {
        if (message != null) {
            fieldErrors.add(new FieldErrorVO(field, message));
        }
        return this;
    }

    public Errors reason(String reason) {
        this.reason = reason;
        return this;
    }

    public boolean hasAny() {
        return !fieldErrors.isEmpty();
    }

    public List<FieldErrorVO> list() {
        return fieldErrors;
    }

    /** 没有错误时不做任何事 */
    public void throwIfAny() {
        if (fieldErrors.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (reason != null) {
            data.put("reason", reason);
        }
        data.put("field_errors", fieldErrors);
        throw new BizException(ErrorCode.PARAM_INVALID, fieldErrors.get(0).getMessage(), data);
    }

    public static BizException fail(String field, String message) {
        return new Errors().add(field, message).toException();
    }

    public BizException toException() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (reason != null) {
            data.put("reason", reason);
        }
        data.put("field_errors", fieldErrors);
        String message = fieldErrors.isEmpty() ? ErrorCode.PARAM_INVALID.getDefaultMessage() : fieldErrors.get(0).getMessage();
        return new BizException(ErrorCode.PARAM_INVALID, message, data);
    }
}
