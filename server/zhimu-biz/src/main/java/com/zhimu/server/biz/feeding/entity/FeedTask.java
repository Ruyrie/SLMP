package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 饲喂任务（t_feed_task）：按批次 + 栏舍 + 日期 + 餐次生成，一餐一行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_task")
public class FeedTask extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    private Long planId;
    private Long stageId;
    private LocalDate taskDate;
    private Integer mealNo;
    private String mealCode;
    private String mealName;
    private LocalTime feedTime;
    private LocalDateTime feedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime backfillEndAt;
    private Long feedId;
    private Integer headCount;
    private BigDecimal dailyQtyPerHead;
    private Integer mealsPerDay;
    private BigDecimal planQty;
    private Long assigneeId;
    private String status;
    private Long recordId;
    private Long todoId;
    private Boolean overdueNotified;
    private String closeReason;
    private Long handledBy;
    private LocalDateTime handledAt;

    @Version
    private Integer version;
}
