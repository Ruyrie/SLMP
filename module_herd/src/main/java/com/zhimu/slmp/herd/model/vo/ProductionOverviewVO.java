package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-L01 生产页概况（L01）。养殖场未通过审核时只返回 farm_id、farm_name、farm_status 与 today。
 * 三张指标卡：存栏、在养批次（副标题栏舍占用）、生产提醒；today_todo_count、warning_count 仅为老版本 App 保留，新 App 不再使用。
 */
public class ProductionOverviewVO {

    private long farmId;
    private String farmName;
    private String farmStatus;
    private String today;
    private boolean hasPen;
    private int stockTotal;
    private int todayTodoCount;
    private int warningCount;
    private int reminderTotal;
    /** 在养批次数（员工按可见栏舍）；老服务端没有该字段时为 null */
    private Integer activeBatchCount;
    /** 启用的栏舍数（员工按可见栏舍）；老服务端没有该字段时为 null */
    private Integer penCount;
    /** 有在养批次的栏舍数；老服务端没有该字段时为 null */
    private Integer occupiedPenCount;
    private String unit;
    private String unitText;
    private List<ProductionReminderItemVO> reminders;
    private ProductionBadges badges;

    public long getFarmId() {
        return farmId;
    }

    public String getFarmName() {
        return farmName;
    }

    /** FARM_STATUS：NORMAL、PENDING、REJECTED 等 */
    public String getFarmStatus() {
        return farmStatus;
    }

    /** 北京日期 yyyy-MM-dd */
    public String getToday() {
        return today;
    }

    public boolean isHasPen() {
        return hasPen;
    }

    public int getStockTotal() {
        return stockTotal;
    }

    /** 老版本 App 兼容字段，生产页不再使用 */
    public int getTodayTodoCount() {
        return todayTodoCount;
    }

    /** 老版本 App 兼容字段，生产页不再使用 */
    public int getWarningCount() {
        return warningCount;
    }

    /** 生产提醒条数 = reminders 条数（L01“生产提醒 N 项”卡） */
    public int getReminderTotal() {
        return reminderTotal;
    }

    @Nullable
    public Integer getActiveBatchCount() {
        return activeBatchCount;
    }

    @Nullable
    public Integer getPenCount() {
        return penCount;
    }

    @Nullable
    public Integer getOccupiedPenCount() {
        return occupiedPenCount;
    }

    public String getUnit() {
        return unit;
    }

    @Nullable
    public String getUnitText() {
        return unitText;
    }

    public List<ProductionReminderItemVO> getReminders() {
        return reminders == null ? Collections.emptyList() : reminders;
    }

    @Nullable
    public ProductionBadges getBadges() {
        return badges;
    }
}
