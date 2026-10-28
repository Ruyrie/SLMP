package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.util.List;

/**
 * API-L01 生产页概况。养殖场未通过审核时只返回 farm_id、farm_name、farm_status、today。
 * 依赖模块03/05–10 的指标先返回 0 或空（待模块03/08 接入），字段仍按 App 契约输出。
 */
@Data
public class ProductionOverviewVO {

    private Long farmId;
    private String farmName;
    /** FARM_STATUS：NORMAL / PENDING / REJECTED / DISABLED */
    private String farmStatus;
    /** 北京日期 yyyy-MM-dd */
    private String today;
    private Boolean hasPen;
    private Integer stockTotal;
    /** 在养批次数；员工只算自己可见的批次（与批次档案列表同口径） */
    private Integer activeBatchCount;
    /** 启用的栏舍数；员工只算自己负责的栏舍 */
    private Integer penCount;
    /** 上面栏舍里有在养批次的栏舍数 */
    private Integer occupiedPenCount;
    /** 老版本 App 兼容保留：今日待办数（今天 + 逾期）；新 App 不再使用 */
    private Integer todayTodoCount;
    /** 老版本 App 兼容保留：当前成员可见的未解除 WARNING 待办数，与预警合集一致；新 App 不再使用 */
    private Integer warningCount;
    /** 生产提醒总数 = reminders 条数（休药中、未处理异常上报、其余预警、今日巡检汇总）。 */
    private Integer reminderTotal;
    private String unit;
    private String unitText;
    private List<ProductionReminderItemVO> reminders;
    private ProductionBadges badges;

    /** L01 生产提醒项（休药中、未处理异常上报、库存不足等） */
    @Data
    public static class ProductionReminderItemVO {
        private String reminderType;
        private Boolean isWarning;
        private String title;
        private String subtitle;
        private String tagText;
        private Long batchId;
        private Long todoId;
        private String route;
        /** 仅 ABNORMAL_REPORT 有值：异常上报 ID，App 进 I06 诊断详情 */
        private Long reportId;
    }

    /** L01 功能入口小圆点：用药与休药、饲料库存 */
    @Data
    public static class ProductionBadges {
        private Boolean withdrawal;
        private Boolean feedLowStock;
    }
}
