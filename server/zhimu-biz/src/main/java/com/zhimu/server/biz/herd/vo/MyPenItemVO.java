package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.util.List;

/** L02 负责栏舍卡片。 */
@Data
public class MyPenItemVO {

    private Long penId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private Integer capacity;
    private Integer stockCount;
    /** 待模块03接入：今日已完成作业数 */
    private Integer taskDone;
    /** 待模块03接入：今日作业总数（含已完成） */
    private Integer taskTotal;
    private Boolean isEmpty;
    private String unitText;
    private List<MyPenBatchVO> batches;
    /** 待模块03接入：未完成的作业类待办 */
    private List<TodoBriefVO> tasks;
}
