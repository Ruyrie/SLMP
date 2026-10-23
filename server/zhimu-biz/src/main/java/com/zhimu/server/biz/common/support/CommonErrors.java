package com.zhimu.server.biz.common.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.List;

/**
 * 公共能力的参数错误：40001 的 data.field_errors 结构。
 */
public final class CommonErrors {

    private CommonErrors() {
    }

    public static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }

    public static BizException fieldErrors(List<FieldErrorVO> errors) {
        String message = errors.isEmpty() ? ErrorCode.PARAM_INVALID.getDefaultMessage() : errors.get(0).getMessage();
        return new BizException(ErrorCode.PARAM_INVALID, message).with("field_errors", errors);
    }

    /** 文案过长时截断到数据库列宽，避免留痕写库失败 */
    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
