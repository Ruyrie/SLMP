package com.zhimu.server.biz.today.vo;

import com.zhimu.server.common.vo.BatchBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 员工首页“我负责的栏舍”（API-T01 my_pens）。 */
@Getter
@Setter
public class MyPenVO {

    /** 在栏批次：BatchBriefVO 加阶段文字 */
    @Getter
    @Setter
    public static class Batch extends BatchBriefVO {

        private String stageText;
    }

    private Long penId;
    private String penName;
    private String penTypeText;
    private Integer capacity;
    private Integer todoCount;
    private Integer todayTaskDone;
    private Integer todayTaskTotal;
    private List<Batch> batches;
    private List<String> todoNames;
    /** 休药中 */
    private Boolean inWithdrawal;
    private Integer withdrawalDaysLeft;
    private LinkVO link;
}
