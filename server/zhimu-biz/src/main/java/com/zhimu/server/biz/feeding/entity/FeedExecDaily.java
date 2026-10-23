package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 饲喂执行汇总日聚合（t_feed_exec_daily）：执行情况统计的读模型。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_exec_daily")
public class FeedExecDaily extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private LocalDate statDate;
    private Integer plannedMeals;
    private Integer doneMeals;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private Integer abnormalCount;
    private LocalDateTime refreshedAt;
}
