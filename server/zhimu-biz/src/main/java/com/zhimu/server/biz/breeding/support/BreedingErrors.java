package com.zhimu.server.biz.breeding.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.List;
import java.util.Map;

/** 模块05 的错误与字段校验文案（模块05 文档 2.5、2.7 节）。 */
public final class BreedingErrors {

    private BreedingErrors() {
    }

    /** 40001：字段错误，field 用请求 JSON 的 snake_case 键 */
    public static BizException invalid(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND);
    }

    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT);
    }

    /** 40903：状态已变化；message 需带耳号 / 具体场景 */
    public static BizException stateChanged(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    public static BizException earTagDuplicate(String earTag) {
        return new BizException(ErrorCode.NAME_DUPLICATE, "本场已有耳号 " + earTag + " 的母猪");
    }

    /** 40307：需提交修改申请 */
    public static BizException changeRequired(String message) {
        return new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, message);
    }

    /** 40303：员工无权 */
    public static BizException roleForbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message);
    }

    public static BizException frozen() {
        return new BizException(ErrorCode.FARM_FROZEN);
    }
}
