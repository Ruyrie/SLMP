package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.BatchStockDaily;

/** 批次日末存栏快照 Mapper（本批只提供读取与建表，快照任务由后续批次接入）。 */
public interface BatchStockDailyMapper extends BaseMapper<BatchStockDaily> {
}
