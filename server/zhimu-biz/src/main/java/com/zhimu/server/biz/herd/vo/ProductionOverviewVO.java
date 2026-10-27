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
    /** 待模块03接入：今日生产类待办数 */
    private Integer todayTodoCount;
    /** 当前成员可见的未解除 WARNING 待办数，与预警合集一致；不额外累计休药和巡检概况。 */
    private Integer warningCount;
    /** 生产提醒总数，包含休药中批次、预警项和巡检概况提示。 */
    private Integer reminderTotal;
    private String unit;
    private String unitText;
    private List<ProductionReminderItemVO> reminders;
    private ProductionBadges badges;

    /** L01 生产提醒项（休药中、库存不足等） */
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
    }

    /** L01 功能入口小圆点：用药与休药、饲料库存 */
    @Data
    public static class ProductionBadges {
        private Boolean withdrawal;
        private Boolean feedLowStock;
    }
}
