package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 待办列表项（API-T02、T04、T06 列表项，API-T05 items）。显示文字均由后端生成。 */
@Getter
@Setter
public class TodoItemVO {

    private Long todoId;
    private Long farmId;
    private String farmName;
    /** TODO_TYPE，例如 FEEDING */
    private String todoType;
    private String todoTypeText;
    /** WORK / WARNING / BUSINESS */
    private String category;
    private String categoryText;
    /** PENDING / OVERDUE / DONE / IGNORED */
    private String displayStatus;
    private String displayStatusText;
    private String title;
    private String subText;
    private String tailText;
    private String dueDate;
    private String dueTime;
    /** AT_TIME / BEFORE_TIME / ALL_DAY */
    private String timeType;
    private String timeLabel;
    private Integer overdueDays;
    private Long penId;
    private String penName;
    private Long batchId;
    private String batchNo;
    /** 员工不返回 */
    private String assigneeText;
    /** 只有待收款有值，员工不返回 */
    private BigDecimal amount;
    private String completedText;
    private Boolean overdueDone;
    private String ignoreReason;
    private String ignoreText;
    private String actionText;
    private LinkVO listLink;
    private LinkVO registerLink;
    private Integer version;
}
