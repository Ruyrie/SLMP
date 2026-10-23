package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品“可用结存”（模块09 2.7 节）：未过期、未停用的批号结存合计；禁用药由调用方按目录另行判断。
 * 药品库存不足按药品判断，不在批号上加标签。
 */
@Component
@RequiredArgsConstructor
public class DrugUsables {

    private final DrugLotMapper lotMapper;

    /** 按药品汇总可用批号结存（只统计结存大于 0 的批号） */
    public Map<Long, BigDecimal> usableByDrug(Collection<Long> drugIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (drugIds == null || drugIds.isEmpty()) {
            return result;
        }
        List<DrugLot> lots = lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .in(DrugLot::getDrugId, drugIds)
                .gt(DrugLot::getStockQty, BigDecimal.ZERO));
        for (DrugLot lot : lots) {
            if (!usable(lot)) {
                continue;
            }
            result.merge(lot.getDrugId(), StockQty.zeroIfNull(lot.getStockQty()), BigDecimal::add);
        }
        return result;
    }

    /** 单个药品的可用结存 */
    public BigDecimal usableOf(Collection<DrugLot> lots) {
        BigDecimal total = BigDecimal.ZERO;
        if (lots == null) {
            return total;
        }
        for (DrugLot lot : lots) {
            if (usable(lot)) {
                total = total.add(StockQty.zeroIfNull(lot.getStockQty()));
            }
        }
        return total;
    }

    public boolean usable(DrugLot lot) {
        return StockQty.zeroIfNull(lot.getStockQty()).signum() > 0
                && lot.getRecalledAt() == null
                && !DrugLotStatuses.expired(lot.getExpireDate());
    }

    /** 药品库存下限（回显值）：录入单位为 PACK 时按包装数显示，否则按记账单位 */
    public static BigDecimal minStockDisplay(Drug drug) {
        BigDecimal min = drug.getMinStock();
        if (min == null) {
            return null;
        }
        if (StockCodesInput.PACK.equals(drug.getMinStockUnit())
                && drug.getPackSize() != null && drug.getPackSize().signum() > 0) {
            return min.divide(drug.getPackSize(), 3, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        }
        return min;
    }

    /** 库存下限录入单位常量（避免与本类名冲突） */
    private static final class StockCodesInput {
        private static final String PACK = "PACK";

        private StockCodesInput() {
        }
    }
}
