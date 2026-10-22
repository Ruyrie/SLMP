package com.zhimu.slmp.today.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.BatchBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 员工首页“我负责的栏舍”（API-T01 my_pens）。
 */
public class MyPenVO {

    /** 在栏批次：BatchBriefVO 加阶段文字 */
    public static class Batch extends BatchBriefVO {

        @Nullable
        private String stageText;

        @Nullable
        public String getStageText() {
            return stageText;
        }
    }

    private long penId;
    @Nullable
    private String penName;
    @Nullable
    private String penTypeText;
    private int capacity;
    private int todoCount;
    private int todayTaskDone;
    private int todayTaskTotal;
    @Nullable
    private List<Batch> batches;
    @Nullable
    private List<String> todoNames;
    /** 休药中 */
    private boolean inWithdrawal;
    @Nullable
    private Integer withdrawalDaysLeft;
    @Nullable
    private LinkVO link;

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getPenTypeText() {
        return penTypeText;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTodoCount() {
        return todoCount;
    }

    public int getTodayTaskDone() {
        return todayTaskDone;
    }

    public int getTodayTaskTotal() {
        return todayTaskTotal;
    }

    @NonNull
    public List<Batch> getBatches() {
        return batches == null ? Collections.emptyList() : batches;
    }

    @NonNull
    public List<String> getTodoNames() {
        return todoNames == null ? Collections.emptyList() : todoNames;
    }

    public boolean isInWithdrawal() {
        return inWithdrawal;
    }

    @Nullable
    public Integer getWithdrawalDaysLeft() {
        return withdrawalDaysLeft;
    }

    @Nullable
    public LinkVO getLink() {
        return link;
    }
}
