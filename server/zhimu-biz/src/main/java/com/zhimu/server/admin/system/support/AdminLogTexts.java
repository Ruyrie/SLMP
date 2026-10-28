package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.enums.AdminMsgType;
import com.zhimu.server.admin.system.enums.AdminOpAction;
import com.zhimu.server.admin.system.enums.AdminOpType;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.OperatorType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端日志与消息的编码 → 中文名映射，以及操作日志详情的页面跳转、终端与浏览器推断。
 * 未收录的编码原样返回，避免新增类型时丢信息。
 */
public final class AdminLogTexts {

    private AdminLogTexts() {
    }

    /** 基础数据各页面写日志时用的 op_type（筛选“基础数据”时一起查） */
    public static final List<String> BASE_DATA_OP_TYPES = List.of("BASE_DATA", "BASE_CATEGORY", "BASE_BREED",
            "BASE_STD_WEIGHT", "BASE_FEED_STD", "BASE_DICT", "BASE_DRUG_CATALOG", "BASE_BANNED_DRUG",
            "BASE_LOT_RECALL", "BASE_IMMUNE_TEMPLATE", "BASE_INSPECT_TEMPLATE");
    /** 养殖场冻结 / 解冻由模块02 以 op_type = FARM 写入 */
    public static final List<String> FARM_FREEZE_OP_TYPES = List.of("FARM_FREEZE", "FARM");
    /** 用户账号页的停用 / 启用与查看完整手机号 */
    public static final List<String> USER_OP_TYPES = List.of("USER_STATUS", "USER");

    /** 筛选下拉的类别 → 实际写入的 op_type 列表；空表示不过滤 */
    public static List<String> expandOpType(String opType) {
        if (opType == null || opType.isBlank()) {
            return null;
        }
        return switch (opType) {
            case "BASE_DATA" -> BASE_DATA_OP_TYPES;
            case "FARM_FREEZE" -> FARM_FREEZE_OP_TYPES;
            case "USER_STATUS" -> USER_OP_TYPES;
            default -> List.of(opType);
        };
    }

    public static String opTypeText(String code) {
        String text = CodeEnum.textOf(AdminOpType.class, code);
        if (text != null || code == null) {
            return text != null ? text : code;
        }
        if (code.startsWith("BASE_")) {
            return AdminOpType.BASE_DATA.getText();
        }
        if ("FARM".equals(code)) {
            return AdminOpType.FARM_FREEZE.getText();
        }
        if ("USER".equals(code)) {
            return "用户账号";
        }
        return code;
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

    /**
     * 操作对象对应的管理端页面编号，用于日志详情跳转。FARM 日志的 biz_id 是养殖场 ID，定位到养殖场管理（WA-S04）；
     * 没有页面能按 ID 定位的类型（例如 FILE、FARM_MEMBER）返回 null，前端不显示“打开对应记录”。
     */
    public static String pageCodeOfBizType(String bizType) {
        if (bizType == null) {
            return null;
        }
        return switch (bizType) {
            case "FARM_AUDIT" -> "WA-R01";
            case "FARM_CHANGE" -> "WA-R02";
            case "CHANGE_REQUEST" -> "WA-R03";
            case "ACCOUNT_RECOVERY" -> "WA-R04";
            case "FARM" -> "WA-S04";
            case "NOTICE" -> "WA-C01";
            case "MARKET_PRICE" -> "WA-C02";
            case "COURSE" -> "WA-C03";
            case "KNOWLEDGE", "KNOWLEDGE_TASK" -> "WA-C04";
            case "AI_MESSAGE" -> "WA-C05";
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
