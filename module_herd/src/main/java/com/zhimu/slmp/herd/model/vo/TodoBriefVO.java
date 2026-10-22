package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * 待办摘要（L02 今日任务行）。文档列为公共类型、由模块03 定义，但 lib_common 尚未提供，先在本组件内定义，
 * 字段待与模块03 对齐（交付说明“待确认”）。
 */
public class TodoBriefVO {

    private long todoId;
    /** 待办类型：FEED 饲喂、IMMUNE 免疫、DISINFECT 消毒、WEIGH 称重、INSPECT 巡检 */
    private String todoType;
    private String title;
    /** 时间文字：“17:00”“18:00 前” */
    private String timeText;
    /** 补充说明，例如消毒药品“聚维酮碘溶液” */
    private String subtitle;
    private String status;
    private String statusText;
    /** 登记页编号：G02、D03、D08、G05、I02 */
    private String route;
    private Long taskId;
    private Long batchId;
    private Long penId;

    public long getTodoId() {
        return todoId;
    }

    public String getTodoType() {
        return todoType;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getTimeText() {
        return timeText;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getRoute() {
        return route;
    }

    @Nullable
    public Long getTaskId() {
        return taskId;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public Long getPenId() {
        return penId;
    }
}
