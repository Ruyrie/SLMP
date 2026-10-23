package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.query.BatchListQuery;
import com.zhimu.server.biz.herd.query.BatchLookupQuery;
import com.zhimu.server.biz.herd.query.TimelineQuery;
import com.zhimu.server.biz.herd.vo.BatchDetailVO;
import com.zhimu.server.biz.herd.vo.BatchEventDetailVO;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.BatchLookupVO;
import com.zhimu.server.biz.herd.vo.PenStockVO;
import com.zhimu.server.biz.herd.vo.RequestCheckVO;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 模块04 批次查询（API-L06、L14–L17、L19、L26）。写入接口在后续批次补入本接口。 */
public interface BatchService {

    PageVO<BatchListItemVO> list(BatchListQuery query);

    BatchLookupVO lookup(BatchLookupQuery query, Long currentFarmId);

    BatchDetailVO detail(Long batchId);

    PageVO<TimelineItemVO> timeline(Long batchId, TimelineQuery query);

    List<PenStockVO> penStocks(Long batchId);

    BatchEventDetailVO eventDetail(Long eventId);

    RequestCheckVO checkByRequest(String requestKey);
}
