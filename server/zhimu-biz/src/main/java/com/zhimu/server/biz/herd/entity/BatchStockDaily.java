package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 批次日末存栏快照（t_batch_stock_daily）：模块10 报表读取。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_stock_daily")
public class BatchStockDaily extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long categoryId;
    private String purpose;
    private LocalDate statDate;
    private Integer stockCount;
}
