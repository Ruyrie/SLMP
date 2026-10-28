package com.zhimu.server.admin.audit.support;

import com.zhimu.server.biz.account.enums.RecoveryVerifyResult;
import com.zhimu.server.biz.account.enums.SmsSendStatus;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeStatus;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.enums.SubmitChannel;

/** 审核中心编码到中文的映射；未知编码原样返回，避免新增类型时丢信息。 */
public final class AuditTexts {

    private AuditTexts() {
    }

    public static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELED" -> "已撤销";
            default -> status;
        };
    }

    public static String roleText(String role) {
        FarmRole value = CodeEnum.of(FarmRole.class, role);
        return value == null ? role : value.getText();
    }

    public static String sourceModuleText(String module) {
        SourceModule value = CodeEnum.of(SourceModule.class, module);
        return value == null ? module : value.getText();
    }

    public static String changeTypeText(String type) {
        ChangeType value = CodeEnum.of(ChangeType.class, type);
        return value == null ? type : value.getText();
    }

    public static String changeStatusText(String status) {
        ChangeStatus value = CodeEnum.of(ChangeStatus.class, status);
        return value == null ? statusText(status) : value.getText();
    }

    public static String impactText(String code) {
        ChangeImpact value = CodeEnum.of(ChangeImpact.class, code);
        return value == null ? code : value.getText();
    }

    public static String submitChannelText(String channel) {
        SubmitChannel value = CodeEnum.of(SubmitChannel.class, channel);
        if (value != null) {
            return value.getText();
        }
        return switch (channel == null ? "" : channel) {
            case "APP_ANDROID" -> "App";
            case "WEB" -> "网页版";
            case "CS_PROXY" -> "客服代登记";
            default -> channel;
        };
    }

    public static String farmStatusText(String auditStatus, Boolean disabled, Boolean frozen) {
        String status = farmStatus(auditStatus, disabled, frozen);
        FarmStatus value = CodeEnum.of(FarmStatus.class, status);
        return value == null ? status : value.getText();
    }

    /** frozen 优先，其次审核状态，最后 disabled */
    public static String farmStatus(String auditStatus, Boolean disabled, Boolean frozen) {
        if (Boolean.TRUE.equals(frozen)) {
            return FarmStatus.FROZEN.name();
        }
        if ("PENDING".equals(auditStatus)) {
            return FarmStatus.PENDING.name();
        }
        if ("REJECTED".equals(auditStatus)) {
            return FarmStatus.REJECTED.name();
        }
        if (Boolean.TRUE.equals(disabled)) {
            return FarmStatus.DISABLED.name();
        }
        return FarmStatus.NORMAL.name();
    }

    public static String recoveryResultText(String result) {
        if (result == null) {
            return null;
        }
        try {
            return RecoveryVerifyResult.valueOf(result).getText();
        } catch (IllegalArgumentException e) {
            return result;
        }
    }

    public static String smsStatusText(String status) {
        if (status == null) {
            return null;
        }
        // 平台只有验证码短信模板：找回结果不发短信，按实际处理方式显示
        switch (status) {
            case "MANUAL":
                return "未发短信，临时密码由管理员线下告知";
            case "NOT_SENT":
                return "未发短信，结果已发到账号消息中心";
            case "PENDING":
                return "未发送（平台未开通通知短信）";
            default:
                break;
        }
        try {
            return SmsSendStatus.valueOf(status).getText();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }

    public static String verifyMethodText(String method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case "CALLBACK_NEW_PHONE" -> "回拨新手机号，本人确认";
            case "CONTACT_FARM_OWNER" -> "联系养殖场所有者确认身份";
            case "OTHER" -> "其他方式";
            default -> method;
        };
    }

    public static String loginClientText(String client) {
        if (client == null) {
            return null;
        }
        return switch (client) {
            case "APP_ANDROID" -> "App";
            case "WEB" -> "网页版";
            default -> client;
        };
    }
}
