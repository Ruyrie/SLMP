package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 饲喂方案（t_feed_plan）：每批次一份。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_plan")
public class FeedPlan extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private Long breedId;
    private Long stdVersionId;
    private LocalDateTime stdAppliedAt;
    private Long stdAppliedBy;
    private BigDecimal abnormalThreshold;

    @Version
    private Integer version;
}
