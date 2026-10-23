package com.zhimu.server.biz.finance.support;

/**
 * 模块10 常量与文案（App Mock 与模块10 服务端文档 2.5/2.7 节）。
 * 分类编码、状态、来源、收款方式等在各表以 VARCHAR 保存，避免新增全局枚举。
 */
public final class FinanceConst {

    public static final String EXPENSE = "EXPENSE";
    public static final String INCOME = "INCOME";
    public static final String ALL = "ALL";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_FEED = "FEED_INBOUND";
    public static final String SOURCE_DRUG = "DRUG_INBOUND";
    public static final String SOURCE_SALE = "SALE_RECEIPT";

    public static final String RECORD_NORMAL = "NORMAL";
    public static final String RECORD_VOID = "VOID";

    public static final String PAY_UNPAID = "UNPAID";
    public static final String PAY_PARTIAL = "PARTIAL";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_VOIDED = "VOIDED";

    public static final String PRICE_WEIGHT = "BY_WEIGHT";
    public static final String PRICE_HEAD = "BY_HEAD";

    public static final String SETTLE_RUNNING = "RUNNING";
    public static final String SETTLE_LOCKED = "LOCKED";

    public static final String SUPPLEMENT_PENDING = "PENDING";
    public static final String SUPPLEMENT_CONFIRMED = "CONFIRMED";
    public static final String SUPPLEMENT_CANCELED = "CANCELED";

    public static final String ADVICE_GENERATING = "GENERATING";
    public static final String ADVICE_SUCCESS = "SUCCESS";
    public static final String ADVICE_FAILED = "FAILED";

    public static final String CONCL_SELL = "SELL_SOON";
    public static final String CONCL_KEEP = "KEEP_FEEDING";
    public static final String CONCL_NOT = "NOT_SELLABLE";

    public static final String REPORT_WEEKLY = "WEEKLY";
    public static final String REPORT_MONTHLY = "MONTHLY";
    public static final String REPORT_TO_DATE = "TO_DATE";

    public static final String SCOPE_ALL = "ALL";
    public static final String RANGE_FARM = "FARM";
    public static final String RANGE_CATEGORY = "CATEGORY";
    public static final String RANGE_BATCH = "BATCH";

    public static final String MARKET_SLAUGHTER = "SLAUGHTER";
    public static final String MARKET_SEEDLING = "SEEDLING";
    public static final String MARKET_PUBLISHED = "PUBLISHED";

    public static final String COST_FEED = "FEED";
    public static final String COST_INTRO = "INTRO";
    public static final String COST_DRUG = "DRUG";
    public static final String COST_OTHER = "OTHER";

    public static final String KIND_FINANCE_RECORD = "FINANCE_RECORD";
    public static final String KIND_SALE_ORDER = "SALE_ORDER";
    public static final String KIND_SALE_RECEIPT = "SALE_RECEIPT";

    private FinanceConst() {
    }

    public static String recordTypeText(String type) {
        return INCOME.equals(type) ? "收入" : EXPENSE.equals(type) ? "支出" : null;
    }

    public static String statusText(String status) {
        return RECORD_VOID.equals(status) ? "已作废" : RECORD_NORMAL.equals(status) ? "正常" : null;
    }

    /** 数据库来源 → App 的 source_type（INBOUND / DRUG_LOT / SALE_RECEIPT / MANUAL） */
    public static String sourceTypeApp(String sourceType) {
        return switch (sourceType == null ? "" : sourceType) {
            case SOURCE_FEED -> "INBOUND";
            case SOURCE_DRUG -> "DRUG_LOT";
            default -> sourceType;
        };
    }

    public static String sourceLabel(String sourceType) {
        return switch (sourceType == null ? "" : sourceType) {
            case SOURCE_FEED -> "来自饲料入库单";
            case SOURCE_DRUG -> "来自兽药疫苗入库单";
            case SOURCE_SALE -> "来自出栏收款";
            default -> null;
        };
    }

    /** 原单据跳转的 biz_type */
    public static String sourceTargetBizType(String sourceType) {
        return switch (sourceType == null ? "" : sourceType) {
            case SOURCE_FEED -> "INBOUND";
            case SOURCE_DRUG -> "DRUG_LOT";
            case SOURCE_SALE -> "SALE_ORDER";
            default -> null;
        };
    }

    public static String paymentStatusText(String status) {
        return switch (status == null ? "" : status) {
            case PAY_UNPAID -> "未收款";
            case PAY_PARTIAL -> "部分收款";
            case PAY_PAID -> "已收款";
            case PAY_VOIDED -> "已作废";
            default -> null;
        };
    }

    public static String pricingMethodText(String method) {
        return switch (method == null ? "" : method) {
            case PRICE_WEIGHT -> "按重量";
            case PRICE_HEAD -> "按头";
            default -> null;
        };
    }

    public static String paymentMethodText(String code) {
        return switch (code == null ? "" : code) {
            case "BANK_TRANSFER" -> "银行转账";
            case "WECHAT" -> "微信";
            case "ALIPAY" -> "支付宝";
            case "CASH" -> "现金";
            default -> code;
        };
    }

    public static String costGroupText(String group) {
        return switch (group == null ? "" : group) {
            case COST_FEED -> "饲料";
            case COST_INTRO -> "引种";
            case COST_DRUG -> "兽药疫苗";
            case COST_OTHER -> "其他费用";
            default -> group;
        };
    }

    public static String conclusionText(String conclusion) {
        return switch (conclusion == null ? "" : conclusion) {
            case CONCL_SELL -> "建议近期出栏";
            case CONCL_KEEP -> "建议继续饲养";
            case CONCL_NOT -> "暂不可出栏";
            default -> null;
        };
    }

    public static String reminderRuleText() {
        return "约定收款日当天提醒你和场长，逾期后从第 3 天起每 3 天提醒一次。没有填写约定收款日期的，按出栏后第 7 天计算。";
    }

    public static String marketPriceUnitText(String priceType) {
        return MARKET_SEEDLING.equals(priceType) ? "元/头" : "元/千克";
    }
}
