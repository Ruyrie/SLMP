package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.entity.BatchMaterialCost;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.BatchMaterialCostMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存流水与批次成本明细的写入（模块09 2.3.9 / 2.3.10）：
 * 数量、物资、来源写入后不改；作废只改 status；批次成本随流水写入，流水作废时一并置 VOID。
 */
@Component
@RequiredArgsConstructor
public class StockFlowWriter {

    private final StockFlowMapper flowMapper;
    private final BatchMaterialCostMapper costMapper;

    /** 写流水：origin_flow_id 首次写入等于自身 ID */
    public StockFlow record(StockFlow flow) {
        if (flow.getStatus() == null) {
            flow.setStatus(StockCodes.STATUS_NORMAL);
        }
        if (flow.getOriginFlowId() == null) {
            flow.setOriginFlowId(0L);
        }
        flowMapper.insert(flow);
        Long id = flow.getId();
        if (id != null) {
            flow.setOriginFlowId(id);
            flowMapper.update(null, Wrappers.<StockFlow>lambdaUpdate()
                    .eq(StockFlow::getId, id)
                    .set(StockFlow::getOriginFlowId, id));
        }
        return flow;
    }

    /** 来源记录的有效流水（不含已被更正替换的） */
    public List<StockFlow> validFlows(String bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return List.of();
        }
        return flowMapper.selectList(Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getBizType, bizType)
                .eq(StockFlow::getBizId, bizId)
                .eq(StockFlow::getStatus, StockCodes.STATUS_NORMAL)
                // void_reason 默认是 NULL，SQL 的 <> 不会匹配 NULL；必须显式包含 NULL，否则首次流水永远查不到。
                .and(w -> w.isNull(StockFlow::getVoidReason)
                        .or().ne(StockFlow::getVoidReason, StockCodes.VOID_CORRECTED))
                .orderByAsc(StockFlow::getOriginFlowId));
    }

    /** 按来源作废流水并同步作废批次成本明细 */
    public void voidByBiz(String bizType, Long bizId, String reason) {
        for (StockFlow flow : validFlows(bizType, bizId)) {
            flow.setStatus(StockCodes.STATUS_VOID);
            flow.setVoidReason(reason);
            flow.setVoidAt(BeijingTime.now());
            flowMapper.updateById(flow);
            voidCostsByFlow(flow.getId());
        }
    }

    /** 作废单个流水 */
    public void voidFlow(StockFlow flow, String reason) {
        flow.setStatus(StockCodes.STATUS_VOID);
        flow.setVoidReason(reason);
        flow.setVoidAt(BeijingTime.now());
        flowMapper.updateById(flow);
        voidCostsByFlow(flow.getId());
    }

    /** 写批次成本明细 */
    public void writeCosts(StockFlow flow, String costType, Long penId, List<StockCostAllocation> rows,
                           LocalDate bizDate) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StockCostAllocation row : rows) {
            BatchMaterialCost cost = new BatchMaterialCost();
            cost.setFarmId(flow.getFarmId());
            cost.setBatchId(row.batchId());
            cost.setPenId(penId);
            cost.setFlowId(flow.getId());
            cost.setSourceType(flow.getBizType());
            cost.setSourceId(flow.getBizId());
            cost.setCostType(costType);
            // 分摊数量 = 流水数量 × 比例（4 位小数）；单批次（比例 1）直接取流水数量
            cost.setQty(row.ratio() == null || row.ratio().compareTo(BigDecimal.ONE) == 0
                    ? flow.getQty()
                    : StockQty.qty(flow.getQty().multiply(row.ratio())));
            cost.setHeadCount(row.headCount());
            cost.setRatio(row.ratio());
            cost.setAmount(row.amount());
            cost.setBizDate(bizDate);
            cost.setStatus(StockCodes.STATUS_NORMAL);
            costMapper.insert(cost);
        }
    }

    /** 作废某流水对应的全部成本明细 */
    public void voidCostsByFlow(Long flowId) {
        if (flowId == null) {
            return;
        }
        for (BatchMaterialCost cost : costMapper.selectList(Wrappers.<BatchMaterialCost>lambdaQuery()
                .eq(BatchMaterialCost::getFlowId, flowId)
                .eq(BatchMaterialCost::getStatus, StockCodes.STATUS_NORMAL))) {
            cost.setStatus(StockCodes.STATUS_VOID);
            costMapper.updateById(cost);
        }
    }
}
