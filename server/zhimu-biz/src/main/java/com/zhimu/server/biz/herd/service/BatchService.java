package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.query.BatchListQuery;
import com.zhimu.server.biz.herd.query.BatchLookupQuery;
import com.zhimu.server.biz.herd.query.TimelineQuery;
import com.zhimu.server.biz.herd.request.BatchCreateRequest;
import com.zhimu.server.biz.herd.request.DeathCreateRequest;
import com.zhimu.server.biz.herd.request.DeathUpdateRequest;
import com.zhimu.server.biz.herd.request.EntryUpdateRequest;
import com.zhimu.server.biz.herd.request.TransferCreateRequest;
import com.zhimu.server.biz.herd.request.TransferUpdateRequest;
import com.zhimu.server.biz.herd.vo.BatchDetailVO;
import com.zhimu.server.biz.herd.vo.BatchEntryResultVO;
import com.zhimu.server.biz.herd.vo.BatchEventDetailVO;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.BatchLookupVO;
import com.zhimu.server.biz.herd.vo.DeathResultVO;
import com.zhimu.server.biz.herd.vo.PenStockVO;
import com.zhimu.server.biz.herd.vo.RequestCheckVO;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.herd.vo.TransferResultVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 模块04 批次接口（API-L06、L14–L26）。 */
public interface BatchService {

    PageVO<BatchListItemVO> list(BatchListQuery query);

    BatchLookupVO lookup(BatchLookupQuery query, Long currentFarmId);

    BatchDetailVO detail(Long batchId);

    PageVO<TimelineItemVO> timeline(Long batchId, TimelineQuery query);

    List<PenStockVO> penStocks(Long batchId);

    BatchEventDetailVO eventDetail(Long eventId);

    RequestCheckVO checkByRequest(String requestKey);

    BatchEntryResultVO create(BatchCreateRequest request, String requestKey);

    BatchDetailVO updateEntry(Long batchId, EntryUpdateRequest request);

    void delete(Long batchId, int version);

    TransferResultVO createTransfer(TransferCreateRequest request, String requestKey);

    TransferResultVO updateTransfer(Long eventId, TransferUpdateRequest request);

    DeathResultVO createDeath(DeathCreateRequest request, String requestKey);

    DeathResultVO updateDeath(Long eventId, DeathUpdateRequest request);
}
