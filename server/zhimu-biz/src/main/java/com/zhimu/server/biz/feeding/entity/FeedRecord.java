package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 投喂记录（t_feed_record）：一餐只有一条有效记录（active_task_id 唯一）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_record")
public class FeedRecord extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    private Long taskId;
    private Long activeTaskId;
    private LocalDate feedDate;
    private Integer mealNo;
    private String mealName;
    private Long feedId;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private BigDecimal deviationRatio;
    private String abnormalType;
    private BigDecimal abnormalThreshold;
    private Long executorId;
    private Boolean isProxy;
    private Boolean isBackfill;
    private String remark;
    private Long stockFlowId;
    private String status;
    private Long voidBy;
    private LocalDateTime voidAt;
    private String requestId;

    @Version
    private Integer version;
}
