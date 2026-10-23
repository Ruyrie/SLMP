package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.enums.AdminMsgType;
import com.zhimu.server.admin.system.enums.AdminOpAction;
import com.zhimu.server.admin.system.enums.AdminOpType;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.OperatorType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端日志与消息的编码 → 中文名映射，以及操作日志详情的页面跳转、终端与浏览器推断。
 * 未收录的编码原样返回，避免新增类型时丢信息。
 */
public final class AdminLogTexts {

    private AdminLogTexts() {
    }

    public static String opTypeText(String code) {
        String text = CodeEnum.textOf(AdminOpType.class, code);
        return text != null ? text : code;
    }

    public static String opActionText(String code) {
        String text = CodeEnum.textOf(AdminOpAction.class, code);
        return text != null ? text : code;
    }

    public static String msgTypeText(String code) {
        String text = CodeEnum.textOf(AdminMsgType.class, code);
        return text != null ? text : code;
    }

    public static String resultText(String result) {
        if ("SUCCESS".equals(result)) {
            return "成功";
        }
        if ("FAIL".equals(result)) {
            return "失败";
        }
        return result;
    }

    /** 管理员角色快照可能是逗号分隔的多个编码；也兼容用户端养殖场角色 */
    public static String roleText(String codes) {
        if (codes == null || codes.isBlank()) {
            return null;
        }
        Set<String> texts = new LinkedHashSet<>();
        for (String code : codes.split(",")) {
            String trimmed = code.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String text = CodeEnum.textOf(AdminRoleCode.class, trimmed);
            if (text == null) {
                text = CodeEnum.textOf(FarmRole.class, trimmed);
            }
            texts.add(text != null ? text : trimmed);
        }
        return texts.isEmpty() ? null : String.join("、", texts);
    }

    public static String clientTypeText(String clientType) {
        if (clientType == null) {
            return null;
        }
        return switch (clientType) {
            case "ADMIN_WEB" -> "网页版";
            case "WEB" -> "网页版";
            case "APP_ANDROID" -> "App";
            default -> clientType;
        };
    }

    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) {
            return "Edge";
        }
        if (ua.contains("chrome/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return "其他";
    }

    public static String sourceText(String operatorType) {
        String text = CodeEnum.textOf(OperatorType.class, operatorType);
        return text != null ? text : operatorType;
    }

    /** 操作对象对应的管理端页面编号，用于日志详情跳转 */
    public static String pageCodeOfBizType(String bizType) {
        if (bizType == null) {
            return null;
        }
        return switch (bizType) {
            case "FARM_AUDIT" -> "WA-R01";
            case "FARM_CHANGE", "FARM" -> "WA-R02";
            case "CHANGE_REQUEST" -> "WA-R03";
            case "ACCOUNT_RECOVERY" -> "WA-R04";
            case "NOTICE" -> "WA-C01";
            case "MARKET_PRICE" -> "WA-C02";
            case "COURSE" -> "WA-C03";
            case "KNOWLEDGE" -> "WA-C04";
            case "AI_SESSION", "AI_MESSAGE" -> "WA-C05";
            case "CATEGORY", "BREED", "STD_WEIGHT", "FEED_STD" -> "WA-D01";
            case "IMMUNE_TEMPLATE" -> "WA-D02";
            case "DRUG_CATALOG", "BANNED_DRUG", "DRUG_LOT_RECALL" -> "WA-D03";
            case "INSPECT_TEMPLATE" -> "WA-D04";
            case "DICT_ITEM", "FINANCE_CATEGORY" -> "WA-D05";
            case "USER" -> "WA-S03";
            case "ADMIN", "ADMIN_ROLE" -> "WA-S05";
            default -> null;
        };
    }

    /** 菜单角标键：四个待审核队列 */
    public static String badgeKeyOfPage(String pageCode) {
        if (pageCode == null) {
            return null;
        }
        return switch (pageCode) {
            case "WA-R01" -> "farm_audit";
            case "WA-R02" -> "farm_change";
            case "WA-R03" -> "change_request";
            case "WA-R04" -> "account_recovery";
            default -> null;
        };
    }
}
