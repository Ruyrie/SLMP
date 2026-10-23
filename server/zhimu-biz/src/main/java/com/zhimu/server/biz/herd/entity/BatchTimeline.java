package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次时间线索引（t_batch_timeline）：本模块写自己的事件，其他模块通过 BatchTimelineService 写入。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_timeline")
public class BatchTimeline extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    /** TIMELINE_RECORD_TYPE：ENTRY / TRANSFER / DEATH / SALE / FEED / WEIGH / IMMUNE / DRUG / INSPECT */
    private String recordType;
    private String bizType;
    private Long bizId;
    private LocalDate bizDate;
    private LocalDateTime occurredAt;
    private String title;
    private String subtitle;
    private String detailJson;
    private Long operatorUserId;

    @TableLogic
    private Boolean deleted;
}
