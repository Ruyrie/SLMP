package com.zhimu.server.biz.stock.service.impl;

import com.zhimu.server.biz.stock.api.BatchCost;
import com.zhimu.server.biz.stock.api.StockCostQueryService;
import com.zhimu.server.biz.stock.mapper.BatchMaterialCostMapper;
import com.zhimu.server.biz.stock.query.CostTypeSum;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批次成本只读实现（模块09 2.3.10，模块10 通过适配器调用）。
 * <p>
 * 按 farm_id + batch_id 汇总 t_batch_material_cost 的 NORMAL 明细：cost_type = FEED / DRUG / INTRO 分别归入
 * 饲料、药品、入栏成本，其余归入其他成本；total 为全部明细金额之和。汇总不依赖 finance 包，
 * 也不依赖 FarmContext（入参 farmId 优先，为空时取当前养殖场）。
 */
@Service
@RequiredArgsConstructor
public class StockCostQueryServiceImpl implements StockCostQueryService {

    /** COST_TYPE：饲料 */
    public static final String COST_FEED = "FEED";
    /** COST_TYPE：药品 */
    public static final String COST_DRUG = "DRUG";
    /** COST_TYPE：入栏成本 */
    public static final String COST_INTRO = "INTRO";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(StockQty.AMOUNT_SCALE);

    private final BatchMaterialCostMapper costMapper;

    @Override
    @Transactional(readOnly = true)
    public BatchCost batchCost(Long farmId, Long batchId) {
        Long scopeFarmId = farmId != null ? farmId : FarmContext.farmId();
        if (scopeFarmId == null || batchId == null) {
            return new BatchCost(ZERO, ZERO, ZERO, ZERO, ZERO);
        }
        // 入参 farmId 已给出，跳过租户条件，这样 finance 侧没有 FarmContext 时也能读
        List<CostTypeSum> rows = TenantSkip.call(() -> costMapper.sumByCostType(scopeFarmId, batchId));
        BigDecimal feed = BigDecimal.ZERO;
        BigDecimal drug = BigDecimal.ZERO;
        BigDecimal intro = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (CostTypeSum row : rows == null ? List.<CostTypeSum>of() : rows) {
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            total = total.add(amount);
            String type = row.getCostType() == null ? "" : row.getCostType().trim().toUpperCase();
            switch (type) {
                case COST_FEED -> feed = feed.add(amount);
                case COST_DRUG -> drug = drug.add(amount);
                case COST_INTRO -> intro = intro.add(amount);
                default -> other = other.add(amount);
            }
        }
        return new BatchCost(StockQty.amount(feed), StockQty.amount(drug), StockQty.amount(intro),
                StockQty.amount(other), StockQty.amount(total));
    }
}
