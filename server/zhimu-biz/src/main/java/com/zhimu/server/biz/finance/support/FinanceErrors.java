package com.zhimu.server.biz.finance.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.List;
import java.util.Map;

/** 模块10 参数错误：40001 + data.field_errors（字段名用请求 JSON 的 snake_case）。 */
public final class FinanceErrors {

    private FinanceErrors() {
    }

    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    public static void require(boolean condition, String field, String message) {
        if (!condition) {
            throw field(field, message);
        }
    }

    public static void require(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BizException(errorCode, message);
        }
    }

    public static void require(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BizException(errorCode);
        }
    }
}
