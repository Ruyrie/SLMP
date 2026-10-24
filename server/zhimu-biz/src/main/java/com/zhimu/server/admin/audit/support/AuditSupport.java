package com.zhimu.server.admin.audit.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/** 审核写操作的入参归一化与文案校验。 */
public final class AuditSupport {

    private AuditSupport() {
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String requireReason(String raw, String emptyMessage) {
        String value = trim(raw);
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, emptyMessage);
        }
        if (value.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原因不能超过 200 字");
        }
        return value;
    }

    public static String requireOpinion(String raw, String emptyMessage) {
        String value = trim(raw);
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, emptyMessage);
        }
        if (value.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核意见不能超过 200 字");
        }
        return value;
    }

    /** 6 位省编码取前 2 位；为空返回 null；不是数字返回 40001。 */
    public static String provincePrefix(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return null;
        }
        String value = provinceCode.trim();
        if (!value.matches("\\d{2,6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "省份编码不正确");
        }
        return value.substring(0, 2);
    }
}
