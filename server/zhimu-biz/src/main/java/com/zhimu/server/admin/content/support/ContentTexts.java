package com.zhimu.server.admin.content.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** 管理端-02 内容运营的枚举中文名与接收范围文字。 */
public final class ContentTexts {

    public static final Map<String, String> PROVINCES = provinces();

    private ContentTexts() {
    }

    public static String noticeLevel(String code) {
        return switch (code == null ? "" : code) {
            case "IMPORTANT" -> "重要";
            case "NORMAL" -> "普通";
            default -> code;
        };
    }

    public static String noticeStatus(String code) {
        return switch (code == null ? "" : code) {
            case "DRAFT" -> "草稿";
            case "SCHEDULED" -> "待发送";
            case "SENT" -> "已发送";
            case "REVOKED" -> "已撤回";
            case "CANCELED" -> "已取消";
            default -> code;
        };
    }

    public static String noticeScope(String code) {
        return switch (code == null ? "" : code) {
            case "ALL" -> "全部用户";
            case "CONDITION" -> "按条件筛选";
            case "FARM" -> "指定养殖场";
            default -> code;
        };
    }

    public static String noticeRole(String code) {
        return switch (code == null ? "" : code) {
            case "OWNER" -> "所有者";
            case "MANAGER" -> "场长";
            case "STAFF" -> "员工";
            case "NONE" -> "未加入养殖场";
            default -> code;
        };
    }

    public static String priceType(String code) {
        return switch (code == null ? "" : code) {
            case "SLAUGHTER" -> "出栏价";
            case "SEEDLING" -> "种苗价";
            default -> code;
        };
    }

    public static String priceUnit(String code) {
        return switch (code == null ? "" : code) {
            case "YUAN_PER_KG" -> "元/千克";
            case "YUAN_PER_HEAD" -> "元/头";
            case "YUAN_PER_BIRD" -> "元/只";
            default -> code;
        };
    }

    public static String priceStatus(String code) {
        return switch (code == null ? "" : code) {
            case "PUBLISHED" -> "已发布";
            case "PENDING_REVIEW" -> "待复核";
            case "RETURNED" -> "已退回";
            case "OFFLINE" -> "已撤销";
            default -> code;
        };
    }

    public static String rowStatus(String code) {
        return switch (code == null ? "" : code) {
            case "PUBLISHED" -> "已发布";
            case "PENDING_REVIEW" -> "待复核";
            case "UNPUBLISHED" -> "未发布";
            default -> code;
        };
    }

    public static String reviewResult(String code) {
        return switch (code == null ? "" : code) {
            case "PASS" -> "通过并发布";
            case "RETURN" -> "退回修改";
            default -> code;
        };
    }

    public static String correctionStatus(String code) {
        return switch (code == null ? "" : code) {
            case "APPLIED" -> "已生效";
            case "PENDING_REVIEW" -> "待复核";
            case "RETURNED" -> "已退回";
            default -> code;
        };
    }

    public static String answerType(String code) {
        return switch (code == null ? "" : code) {
            case "NORMAL" -> "普通";
            case "NO_BASIS" -> "未找到依据";
            case "OFF_TOPIC" -> "拒答";
            case "BANNED_DRUG" -> "禁用药";
            case "EPIDEMIC" -> "疑似重大疫病";
            default -> code;
        };
    }

    public static String rating(String code) {
        return switch (code == null ? "" : code) {
            case "USEFUL" -> "有用";
            case "USELESS" -> "没用";
            default -> code;
        };
    }

    public static String handleStatus(String code) {
        return switch (code == null ? "" : code) {
            case "PENDING" -> "待处理";
            case "CORRECTED" -> "已更正";
            case "TASKED" -> "已转任务";
            case "NO_ACTION" -> "无需处理";
            default -> code;
        };
    }

    public static String sourceType(String code) {
        return switch (code == null ? "" : code) {
            case "PLATFORM" -> "平台知识库";
            case "DISEASE" -> "常见病症";
            case "DRUG_CATALOG" -> "兽药目录";
            default -> code;
        };
    }

    public static String provinceName(String code) {
        return code == null ? null : PROVINCES.getOrDefault(code, code);
    }

    public static String provinceCode(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : PROVINCES.entrySet()) {
            if (entry.getValue().equals(name) || entry.getValue().startsWith(name) || name.startsWith(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, String> provinces() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("110000", "北京");
        map.put("120000", "天津");
        map.put("130000", "河北");
        map.put("140000", "山西");
        map.put("150000", "内蒙古");
        map.put("210000", "辽宁");
        map.put("220000", "吉林");
        map.put("230000", "黑龙江");
        map.put("310000", "上海");
        map.put("320000", "江苏");
        map.put("330000", "浙江");
        map.put("340000", "安徽");
        map.put("350000", "福建");
        map.put("360000", "江西");
        map.put("370000", "山东");
        map.put("410000", "河南");
        map.put("420000", "湖北");
        map.put("430000", "湖南");
        map.put("440000", "广东");
        map.put("450000", "广西");
        map.put("460000", "海南");
        map.put("500000", "重庆");
        map.put("510000", "四川");
        map.put("520000", "贵州");
        map.put("530000", "云南");
        map.put("540000", "西藏");
        map.put("610000", "陕西");
        map.put("620000", "甘肃");
        map.put("630000", "青海");
        map.put("640000", "宁夏");
        map.put("650000", "新疆");
        map.put("710000", "台湾");
        map.put("810000", "香港");
        map.put("820000", "澳门");
        return map;
    }
}
