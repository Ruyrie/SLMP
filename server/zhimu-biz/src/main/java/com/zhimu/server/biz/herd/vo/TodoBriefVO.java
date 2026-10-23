package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** 待办摘要（L02 今日任务行）。字段与模块03 的 TodoBriefVO 对齐，待模块03接入后由今天模块提供。 */
@Data
public class TodoBriefVO {

    private Long todoId;
    private String todoType;
    private String title;
    private String timeText;
    private String subtitle;
    private String status;
    private String statusText;
    private String route;
    private Long taskId;
    private Long batchId;
    private Long penId;
}
