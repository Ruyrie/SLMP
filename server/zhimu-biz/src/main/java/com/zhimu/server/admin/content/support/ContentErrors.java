package com.zhimu.server.admin.content.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 管理端-02 常用业务异常（文案按服务端技术准备文档）。 */
public final class ContentErrors {

    public static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_NOT_FOUND = "记录不存在或已删除";
    public static final String MSG_NOTICE_DELETED = "该记录已被删除";
    public static final String MSG_REASON_OFF_SHELF = "请填写下架原因";
    public static final String MSG_REASON_PUBLISH = "请填写修改说明";
    public static final String MSG_REASON_ROLLBACK = "请填写回退原因";
    public static final String MSG_REASON_CLOSE = "请填写关闭原因";

    private ContentErrors() {
    }

    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    /** 一次性返回多个字段错误（批量发布逐行校验） */
    public static BizException fields(List<FieldErrorVO> errors) {
        String message = errors.isEmpty() ? "参数校验失败" : errors.getFirst().getMessage();
        List<FieldErrorVO> safe = new ArrayList<>(errors);
        return new BizException(ErrorCode.PARAM_INVALID, message, Map.of("field_errors", safe));
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
        return new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
    }

    public static BizException noticeDeleted() {
        return new BizException(ErrorCode.RECORD_DELETED, MSG_NOTICE_DELETED);
    }

    public static BizException duplicate(String message) {
        return new BizException(ErrorCode.NAME_DUPLICATE, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message);
    }
    public static BizException deleted() {
        return new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除");
    }

    public static BizException fileInvalid(String message) {
        return new BizException(ErrorCode.FILE_INVALID, message);
    }}
