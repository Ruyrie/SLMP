package com.zhimu.server.admin.base.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.List;
import java.util.Map;

/** 管理端基础数据常用业务异常（文案按管理端-03 2.5.3 / 2.5.7）。 */
public final class BaseErrors {

    public static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_DRAFT_GONE = "草稿已被其他管理员处理，请刷新";
    public static final String MSG_NO_DRAFT = "没有未发布的修改";
    public static final String MSG_REASON_DISABLE = "请填写停用原因";
    public static final String MSG_REASON_CHANGE = "请填写修改原因";
    public static final String MSG_REASON_PUBLISH = "请填写更新说明";
    public static final String MSG_REASON_ROLLBACK = "请填写回退原因";

    private BaseErrors() {
    }

    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException reasonEmpty(String message) {
        return new BizException(ErrorCode.REASON_EMPTY, message);
    }

    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
    }

    public static BizException stateChanged(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException duplicate(String message) {
        return new BizException(ErrorCode.NAME_DUPLICATE, message);
    }

    public static BizException notAllowed(String message) {
        return new BizException(ErrorCode.STATE_NOT_ALLOWED, message);
    }
}
