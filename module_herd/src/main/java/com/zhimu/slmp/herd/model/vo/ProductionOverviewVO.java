package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-L01 生产页概况（L01）。养殖场未通过审核时只返回 farm_id、farm_name、farm_status 与 today。
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

    public int getTodayTodoCount() {
        return todayTodoCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    /** 生产提醒总数 = 休药中批次 + 预警项（L01“预警”卡，待定事项 2） */
    public int getReminderTotal() {
        return reminderTotal;
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
