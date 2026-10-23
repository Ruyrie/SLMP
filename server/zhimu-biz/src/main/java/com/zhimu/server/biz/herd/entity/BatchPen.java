package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 批次栏舍分布（t_batch_pen）：同一批次在同一栏舍只有一行，降为 0 时保留。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_pen")
public class BatchPen extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    private Integer stockCount;
    private LocalDate firstInDate;
    private LocalDate lastOutDate;
}
