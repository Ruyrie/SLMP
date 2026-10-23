package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.common.enums.FarmStatus;

/**
 * 养殖场派生状态与中文名：FROZEN → DISABLED → PENDING → REJECTED → NORMAL。
 * 与模块02 文档 2.7 的派生规则一致（任务书把已停用排在待审核之前，以任务书为准）。
 */
public final class FarmStatuses {

    public static final String MSG_PENDING_RESTRICT =
            "审核通过前只能修改注册信息。栏舍、批次、库存和邀请成员等功能，要等审核通过后才能使用。";
    public static final String MSG_DISABLED_RESTRICT = "%s已停用。数据只能查看，不能登记新的记录。";
    public static final String MSG_FROZEN_RESTRICT = "%s已被平台冻结，暂时不能使用。原因见消息中心。";

    private FarmStatuses() {
    }

    public static FarmStatus of(Farm farm) {
        if (farm == null) {
            return null;
        }
        if (Boolean.TRUE.equals(farm.getFrozen())) {
            return FarmStatus.FROZEN;
        }
        if (Boolean.TRUE.equals(farm.getDisabled())) {
            return FarmStatus.DISABLED;
        }
        if ("PENDING".equals(farm.getAuditStatus())) {
            return FarmStatus.PENDING;
        }
        if ("REJECTED".equals(farm.getAuditStatus())) {
            return FarmStatus.REJECTED;
        }
        return FarmStatus.NORMAL;
    }

    /** FARM_STATUS 的中文名（App 的 status_text） */
    public static String text(FarmStatus status) {
        return status == null ? null : status.getText();
    }

    public static String restrictTip(Farm farm, FarmStatus status) {
        if (status == null) {
            return null;
        }
        String name = farm == null || farm.getName() == null ? "养殖场" : farm.getName();
        return switch (status) {
            case PENDING, REJECTED -> MSG_PENDING_RESTRICT;
            case DISABLED -> MSG_DISABLED_RESTRICT.formatted(name);
            case FROZEN -> MSG_FROZEN_RESTRICT.formatted(name);
            case NORMAL -> null;
        };
    }

    /** 已通过审核（正常、停用、冻结） */
    public static boolean approved(FarmStatus status) {
        return status == FarmStatus.NORMAL || status == FarmStatus.DISABLED || status == FarmStatus.FROZEN;
    }

    public static boolean readable(FarmStatus status) {
        return status == FarmStatus.NORMAL || status == FarmStatus.DISABLED;
    }

    public static boolean writable(FarmStatus status) {
        return status == FarmStatus.NORMAL;
    }
}
