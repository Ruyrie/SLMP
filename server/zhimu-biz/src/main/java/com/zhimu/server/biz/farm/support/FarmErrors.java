package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/** 模块02 常用业务异常（文案按模块02 文档 2.5.2） */
public final class FarmErrors {

    public static final String MSG_STAFF_MEMBER_VIEW = "你没有%s成员管理的查看权限";
    public static final String MSG_NO_PERMISSION = "你没有这个操作的权限";
    public static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_OWNER_ONLY_QUIT = "所有者不能退出养殖场，请先转让所有权";

    private FarmErrors() {
    }

    public static BizException notMember(String farmName) {
        return new BizException(ErrorCode.NOT_FARM_MEMBER,
                "你已不是" + (farmName == null ? "该养殖场" : farmName) + "的成员");
    }

    public static BizException roleForbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message == null ? MSG_NO_PERMISSION : message);
    }

    public static BizException memberViewForbidden(String farmName) {
        return roleForbidden(MSG_STAFF_MEMBER_VIEW.formatted(farmName));
    }

    public static BizException statusForbidden(String message) {
        return new BizException(ErrorCode.FARM_STATUS_FORBIDDEN, message);
    }

    public static BizException frozen(String farmName) {
        return new BizException(ErrorCode.FARM_FROZEN,
                farmName + "已被平台冻结，暂时不能使用。原因见消息中心。");
    }

    public static BizException deleted(String farmName) {
        return new BizException(ErrorCode.RECORD_DELETED, farmName + "已被删除");
    }

    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
    }

    public static BizException stateChanged(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message == null ? ErrorCode.NOT_FOUND.getDefaultMessage() : message);
    }

    public static void requireOwner(Farm farm, Long userId) {
        if (!userId.equals(farm.getOwnerUserId())) {
            throw roleForbidden(MSG_NO_PERMISSION);
        }
    }
}
