package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * L02 负责栏舍卡片。
 */
public class MyPenItemVO {

    private long penId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private int capacity;
    private int stockCount;
    private int taskDone;
    private int taskTotal;
    private boolean isEmpty;
    private String unitText;
    private List<MyPenBatchVO> batches;
    private List<TodoBriefVO> tasks;

    public long getPenId() {
        return penId;
    }

    public String getPenCode() {
        return penCode;
    }

    public String getPenName() {
        return penName;
    }

    /** PEN_TYPE 字典编码 */
    public String getPenType() {
        return penType;
    }

    @Nullable
    public String getPenTypeText() {
        return penTypeText;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getStockCount() {
        return stockCount;
    }

    public int getTaskDone() {
        return taskDone;
    }

    public int getTaskTotal() {
        return taskTotal;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    @Nullable
    public String getUnitText() {
        return unitText;
    }

    public List<MyPenBatchVO> getBatches() {
        return batches == null ? Collections.emptyList() : batches;
    }

    /** 未完成的作业类待办 */
    public List<TodoBriefVO> getTasks() {
        return tasks == null ? Collections.emptyList() : tasks;
    }
}
