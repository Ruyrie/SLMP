package com.zhimu.server.admin.base.support;

import com.zhimu.server.admin.base.convert.AdminBaseConvert;

/** 管理端-03 基础数据文案（DRUG_CATEGORY、ADMIN_ROUTE 等；与前端契约的 xxx_text 一致）。 */
public final class BaseAdminTexts {

    private BaseAdminTexts() {
    }

    public static String drugCategory(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "VET_DRUG" -> "兽药";
            case "VACCINE" -> "疫苗";
            case "DISINFECTANT" -> "消毒剂";
            default -> code;
        };
    }

    public static String scheduleType(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "BY_DAY_AGE" -> "按日龄";
            case "BY_DATE" -> "按日期";
            default -> code;
        };
    }

    public static String measureUnit(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "G" -> "克";
            case "ML" -> "毫升";
            case "L" -> "升";
            case "DOSE" -> "头份";
            case "KG" -> "千克";
            default -> code;
        };
    }

    public static String vaccineDoseUnit(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "DOSE_PER_HEAD" -> "头份 / 头";
            case "ML_PER_HEAD" -> "毫升 / 头";
            case "DOSE_PER_BIRD" -> "羽份 / 只";
            default -> drugDoseUnit(code);
        };
    }

    public static String drugDoseUnit(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "G_PER_T_FEED" -> "克/吨料";
            case "G_PER_L_WATER" -> "克/升水";
            case "ML_PER_10KG" -> "毫升/10千克体重";
            case "MG_PER_KG" -> "毫克/千克体重";
            case "ML_PER_HEAD" -> "毫升/头";
            case "G_PER_HEAD" -> "克/头";
            case "DOSE_PER_HEAD" -> "头份/头";
            case "DOSE_PER_BIRD" -> "羽份/只";
            default -> code;
        };
    }

    public static String adminRoute(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "MIX_FEED" -> "混饲";
            case "MIX_WATER" -> "混饮";
            case "IM" -> "肌肉注射";
            case "SC" -> "皮下注射";
            case "IV" -> "静脉注射";
            case "ORAL" -> "口服";
            case "EXTERNAL" -> "外用";
            default -> code;
        };
    }

    public static String bannedClass(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "BETA_AGONIST" -> "β-受体激动剂";
            case "ANTIBACTERIAL" -> "抗菌药";
            case "NITROFURAN" -> "抗菌药（硝基呋喃类）";
            case "HORMONE" -> "激素类";
            case "SEDATIVE" -> "镇静类";
            case "PESTICIDE" -> "杀虫剂类";
            case "OTHER" -> "其他";
            default -> code;
        };
    }

    public static String scrapStatus(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "UNSCRAPPED" -> "未报废";
            case "PARTIAL" -> "部分报废";
            case "SCRAPPED" -> "已报废";
            default -> code;
        };
    }

    public static String hitSource(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "SEARCH" -> "搜索";
            case "ARCHIVE" -> "建档";
            case "STOCK_IN" -> "入库";
            case "AI_QA" -> "AI 问答";
            default -> code;
        };
    }

    public static String changeType(String code) {
        return AdminBaseConvert.changeTypeText(code);
    }

    public static String versionStatus(String code) {
        return AdminBaseConvert.versionStatusText(code);
    }

    public static String species(String code) {
        return AdminBaseConvert.speciesText(code);
    }
}
