package com.zhimu.slmp.finance.model;

/**
 * 模块10 枚举字符串常量（技术准备文档 2.7 节：枚举用 String 常量，避免 R8 重命名枚举常量致 Gson 解析失败）。
 */
public final class FinanceConst {

    private FinanceConst() {
    }

    // ===== 收支 =====
    public static final String EXPENSE = "EXPENSE";
    public static final String INCOME = "INCOME";
    /** 收支流水类型筛选 */
    public static final String TYPE_ALL = "ALL";
    public static final String TYPE_AUTO = "AUTO";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_VOID = "VOID";
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 合计视角 */
    public static final String SCOPE_ALL = "ALL";
    /** 分类 code 为这三种时检查疑似重复 */
    public static final String CODE_FEED = "FEED";
    public static final String CODE_DRUG = "DRUG";
    public static final String CODE_SALE = "SALE";
    public static final String CODE_SEEDLING = "SEEDLING";
    /** 原单据类型 */
    public static final String BIZ_INBOUND = "INBOUND";
    public static final String BIZ_DRUG_LOT = "DRUG_LOT";
    public static final String BIZ_SALE_ORDER = "SALE_ORDER";
    public static final String BIZ_BATCH = "BATCH";
    /** U14 修改记录的记录类型 */
    public static final String LOG_FINANCE_RECORD = "FINANCE_RECORD";
    public static final String LOG_SALE_ORDER = "SALE_ORDER";

    // ===== 出栏单 =====
    public static final String BY_WEIGHT = "BY_WEIGHT";
    public static final String BY_HEAD = "BY_HEAD";
    public static final String PAY_UNPAID = "UNPAID";
    public static final String PAY_PARTIAL = "PARTIAL";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_VOIDED = "VOIDED";
    public static final String DUE_OVERDUE = "OVERDUE";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String DUE_LATER = "DUE_LATER";
    public static final String DUE_SETTLED = "SETTLED";
    /** E09 筛选 */
    public static final String DUE_FILTER_ALL = "ALL";
    public static final String DUE_FILTER_OVERDUE = "OVERDUE";
    public static final String DUE_FILTER_7D = "DUE_7D";
    /** 收款方式字典（U10）与默认值 */
    public static final String DICT_PAYMENT_METHOD = "PAYMENT_METHOD";
    public static final String PAY_BANK = "BANK_TRANSFER";
    public static final String PAY_WECHAT = "WECHAT";
    public static final String PAY_ALIPAY = "ALIPAY";
    public static final String PAY_CASH = "CASH";
    /** 提醒对象 */
    public static final String REMIND_YOU_OWNER_OTHER = "YOU_OWNER_OTHER_MANAGERS";
    /** 修改申请（U02） */
    public static final String CHANGE_UPDATE = "UPDATE";
    public static final String CHANGE_VOID = "VOID";
    public static final String FIELD_UNIT_PRICE = "unit_price";
    public static final String FIELD_QUANTITY = "quantity";
    /** E08 界面常量 */
    public static final String MODE_UNIT_PRICE = "UNIT_PRICE";
    public static final String MODE_QUANTITY = "QUANTITY";
    public static final String MODE_VOID = "VOID";

    // ===== 核算 =====
    public static final String SETTLEMENT_RUNNING = "RUNNING";
    public static final String SETTLEMENT_LOCKED = "LOCKED";
    public static final String COST_FEED = "FEED";
    public static final String COST_SEEDLING = "SEEDLING";
    public static final String COST_DRUG = "DRUG";
    public static final String COST_OTHER = "OTHER";
    public static final String BASIS_SOLD = "SOLD";
    public static final String BATCH_ACTIVE = "ACTIVE";

    // ===== 行情与建议 =====
    public static final String PRICE_SLAUGHTER = "SLAUGHTER";
    public static final String PRICE_SEEDLING = "SEEDLING";
    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String FLAT = "FLAT";
    public static final String GEN_GENERATING = "GENERATING";
    public static final String GEN_SUCCESS = "SUCCESS";
    public static final String GEN_FAILED = "FAILED";
    public static final String SELL_SOON = "SELL_SOON";
    public static final String KEEP_FEEDING = "KEEP_FEEDING";
    public static final String NOT_SELLABLE = "NOT_SELLABLE";

    // ===== 报表 =====
    public static final String REPORT_WEEKLY = "WEEKLY";
    public static final String REPORT_MONTHLY = "MONTHLY";
    public static final String REPORT_TO_DATE = "TO_DATE";
    public static final String RANGE_FARM = "FARM";
    public static final String RANGE_CATEGORY = "CATEGORY";
    public static final String RANGE_BATCH = "BATCH";
    public static final String METRIC_STOCK = "STOCK";
    public static final String METRIC_MORTALITY = "MORTALITY";
    public static final String METRIC_FEED = "FEED";
    public static final String METRIC_BALANCE = "BALANCE";
    public static final String FORMAT_XLSX = "XLSX";
    public static final String EVENT_IN = "IN";
    public static final String EVENT_OUT = "OUT";

    /** 收款方式文字（字典加载失败时的兜底） */
    public static String paymentMethodText(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case PAY_BANK:
                return "银行转账";
            case PAY_WECHAT:
                return "微信";
            case PAY_ALIPAY:
                return "支付宝";
            case PAY_CASH:
                return "现金";
            default:
                return code;
        }
    }

    /** AI 出栏建议结论文字（只有三种） */
    public static String conclusionText(String code) {
        if (SELL_SOON.equals(code)) {
            return "建议近期出栏";
        }
        if (NOT_SELLABLE.equals(code)) {
            return "暂不可出栏";
        }
        return "建议继续饲养";
    }
}
