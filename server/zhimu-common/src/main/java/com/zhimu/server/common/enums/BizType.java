package com.zhimu.server.common.enums;

import lombok.Getter;

/**
 * 业务记录类型（05 号文档 6.3 节）：t_biz_media、t_change_request、t_operation_log、t_todo、t_message、
 * t_batch_timeline、t_stock_flow 的 biz_type 统一使用这组编码。
 */
@Getter
public enum BizType implements CodeEnum {
    // 账号与养殖场
    USER("账号"),
    ACCOUNT_RECOVERY("账号找回"),
    FARM("养殖场"),
    FARM_AUDIT("注册审核"),
    FARM_CHANGE("资料变更"),
    FARM_MEMBER("成员"),
    FARM_INVITE("邀请"),
    FARM_TRANSFER("所有权转让"),
    // 栏舍与存栏
    PEN("栏舍"),
    BATCH("批次"),
    BATCH_ENTRY("入栏"),
    BATCH_TRANSFER("转栏"),
    BATCH_DEATH("死淘"),
    // 繁育
    BREEDING_ANIMAL("种畜档案"),
    MATING("配种"),
    PREG_CHECK("妊检"),
    FARROWING("产仔"),
    WEANING("断奶"),
    BREEDING_EXIT("种畜死亡淘汰"),
    INCUBATION("孵化"),
    // 饲喂与称重
    FEED_PLAN("饲喂方案"),
    FEED_TASK("饲喂任务"),
    FEED_RECORD("投喂"),
    WEIGH_RECORD("称重"),
    WEIGH_CYCLE("称重周期"),
    // 巡检与诊断
    INSPECTION_SETTING("巡检设置"),
    INSPECTION("巡检"),
    ABNORMAL_REPORT("异常上报"),
    DIAGNOSIS_HANDLE("处理结果"),
    // 免疫、用药与消毒
    IMMUNE_PLAN("免疫计划"),
    IMMUNE_RECORD("接种"),
    MEDICATION_RECORD("用药"),
    DISINFECT_PLAN("消毒计划"),
    DISINFECT_TASK("消毒任务"),
    DISINFECT_RECORD("消毒"),
    // 物资库存
    FEED("饲料档案"),
    DRUG("药品档案"),
    DRUG_LOT("药品批号"),
    INBOUND("入库单"),
    OUTBOUND("出库单"),
    STOCKTAKE("盘点"),
    LOSS("报损报废"),
    STOCK_SETTING("库存设置"),
    // 经营
    FINANCE_RECORD("收支"),
    SALE_ORDER("出栏单"),
    SALE_RECEIPT("收款"),
    BATCH_SETTLEMENT("批次核算"),
    SALE_ADVICE("出栏建议"),
    BIZ_REPORT("经营报表"),
    // AI 与内容
    AI_SESSION("AI 会话"),
    AI_MESSAGE("AI 问答"),
    COURSE("课程"),
    KNOWLEDGE("知识条目"),
    NOTICE("平台通知"),
    MARKET_PRICE("行情"),
    // 平台审核
    CHANGE_REQUEST("修改申请"),
    DRUG_LOT_RECALL("批号停用"),
    // 基础数据与平台管理
    CATEGORY("品类"),
    BREED("品种"),
    STD_WEIGHT("标准体重"),
    FEED_STD("饲喂标准"),
    IMMUNE_TEMPLATE("免疫模板"),
    INSPECT_TEMPLATE("巡检模板"),
    DRUG_CATALOG("兽药疫苗目录"),
    BANNED_DRUG("禁用药"),
    DICT_ITEM("字典项"),
    FINANCE_CATEGORY("收支分类"),
    ADMIN("管理员"),
    ADMIN_ROLE("管理员角色");

    private final String text;

    BizType(String text) {
        this.text = text;
    }
}
