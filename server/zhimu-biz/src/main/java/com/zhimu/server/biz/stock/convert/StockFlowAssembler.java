package com.zhimu.server.biz.stock.convert;

import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水 → App {@code StockFlowItemVO}。
 * <p>
 * 药品流水的 stock_qty_after 按 App mock 输出为 null（药品的“结存”列由批号详情展示）；
 * 饲料流水输出发生后的结存。员工视图的单价、金额由 @ManagerView 裁剪。
 */
@Component
@RequiredArgsConstructor
public class StockFlowAssembler {

    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final BatchQueryService batchQueryService;
    private final StockLookups lookups;

    public List<StockFlowItemVO> toVos(List<StockFlow> flows) {
        List<StockFlowItemVO> result = new ArrayList<>();
        if (flows == null) {
            return result;
        }
        Map<Long, String> feedNames = new HashMap<>();
        Map<Long, Drug> drugs = new HashMap<>();
        Map<Long, String> lotNos = new HashMap<>();
        for (StockFlow flow : flows) {
            result.add(toVo(flow, feedNames, drugs, lotNos));
        }
        return result;
    }

    public StockFlowItemVO toVo(StockFlow flow) {
        return toVo(flow, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private StockFlowItemVO toVo(StockFlow flow, Map<Long, String> feedNames, Map<Long, Drug> drugs,
                                 Map<Long, String> lotNos) {
        StockFlowItemVO vo = new StockFlowItemVO();
        vo.setFlowId(flow.getId() == null ? 0 : flow.getId());
        vo.setFlowType(flow.getFlowType());
        vo.setFlowTypeText(StockCodes.flowTypeText(flow.getFlowType()));
        vo.setDirection(flow.getDirection());
        vo.setMaterialType(flow.getMaterialType());
        vo.setRelatedDesc(flow.getRelatedDesc());
        vo.setBatchId(flow.getBatchId());
        vo.setBatchNo(batchNo(flow.getBatchId()));
        vo.setPenName(lookups.penName(flow.getPenId()));
        vo.setOperatorName(flow.getOperatorId() == null ? null : lookups.userName(flow.getOperatorId()));
        vo.setOccurAt(BeijingTime.format(flow.getOccurAt()));
        vo.setBizDate(BeijingTime.format(flow.getBizDate()));
        vo.setStatus(flow.getStatus());
        vo.setStatusText(StockCodes.statusText(flow.getStatus()));
        vo.setBizType(flow.getBizType());
        vo.setBizId(flow.getBizId());
        vo.setUnitPrice(flow.getUnitPrice());
        vo.setAmount(flow.getAmount());
        if (StockCodes.isFeed(flow.getMaterialType())) {
            vo.setMaterialName(feedName(flow.getFeedId(), feedNames));
            vo.setQty(StockQty.feed(flow.getQty()));
            vo.setStockQtyAfter(flow.getStockQtyAfter());
        } else {
            Drug drug = drug(flow.getDrugId(), drugs);
            vo.setMaterialName(drug == null ? null : drug.getName());
            if (drug != null) {
                vo.setQty(StockQty.drug(flow.getQty(), drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
            } else {
                vo.setQty(StockQty.drug(flow.getQty(), null, null, null));
            }
            vo.setLotNo(lotNo(flow.getDrugLotId(), lotNos));
            vo.setStockQtyAfter(null);
        }
        return vo;
    }

    private String feedName(Long feedId, Map<Long, String> cache) {
        if (feedId == null) {
            return null;
        }
        return cache.computeIfAbsent(feedId, id -> {
            Feed feed = feedMapper.selectById(id);
            return feed == null ? null : feed.getName();
        });
    }

    private Drug drug(Long drugId, Map<Long, Drug> cache) {
        if (drugId == null) {
            return null;
        }
        return cache.computeIfAbsent(drugId, drugMapper::selectById);
    }

    private String lotNo(Long lotId, Map<Long, String> cache) {
        if (lotId == null) {
            return null;
        }
        return cache.computeIfAbsent(lotId, id -> {
            DrugLot lot = drugLotMapper.selectById(id);
            return lot == null ? null : lot.getLotNo();
        });
    }

    private String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            return batch.batchNo();
        } catch (BizException e) {
            return null;
        }
    }
}
