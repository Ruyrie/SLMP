package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 员工版首页指标（API-T01 staff_metrics，仅员工）。
 */
public class StaffMetricsVO {

    private int penCount;
    private int todayTodoCount;
    private int overdueCount;
    private int todayDoneCount;
    @Nullable
    private List<String> penNames;
    @Nullable
    private List<String> todayDoneNames;
    @Nullable
    private String overdueFirstText;

    public int getPenCount() {
        return penCount;
    }

    public int getTodayTodoCount() {
        return todayTodoCount;
    }

    public int getOverdueCount() {
        return overdueCount;
    }

    public int getTodayDoneCount() {
        return todayDoneCount;
    }

    @Nullable
    public List<String> getPenNames() {
        return penNames;
    }

    @Nullable
    public List<String> getTodayDoneNames() {
        return todayDoneNames;
    }

    @Nullable
    public String getOverdueFirstText() {
        return overdueFirstText;
    }

    /** 登记回流：今日待办减 1、今日已完成加 1（已逾期同时减逾期数），返回新对象 */
    @androidx.annotation.NonNull
    public StaffMetricsVO minusTodo(boolean overdue) {
        StaffMetricsVO c = new StaffMetricsVO();
        c.penCount = penCount;
        c.todayTodoCount = Math.max(0, todayTodoCount - 1);
        c.overdueCount = overdue ? Math.max(0, overdueCount - 1) : overdueCount;
        c.todayDoneCount = todayDoneCount + 1;
        c.penNames = penNames;
        c.todayDoneNames = todayDoneNames;
        c.overdueFirstText = overdueFirstText;
        return c;
    }
}
