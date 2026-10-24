package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.api.DrugLotRecallPort;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 平台停用批号端口实现（模块09）：只改 t_drug_lot 的停用快照，不动结存与流水。 */
@Service
@RequiredArgsConstructor
public class DrugLotRecallPortImpl implements DrugLotRecallPort {

    private final DrugLotMapper lotMapper;

    @Override
    public RecallApplyResult holds(Long catalogId, String lotNo) {
        return result(selectLots(catalogId, lotNo));
    }

    @Override
    @Transactional
    public RecallApplyResult markRecalled(Long catalogId, String lotNo, Long recallId, String reason,
                                          LocalDateTime recalledAt) {
        List<DrugLot> lots = selectLots(catalogId, lotNo);
        for (DrugLot lot : lots) {
            BigDecimal stock = lot.getStockQty() == null ? BigDecimal.ZERO : lot.getStockQty();
            lotMapper.update(null, Wrappers.<DrugLot>lambdaUpdate()
                    .eq(DrugLot::getId, lot.getId())
                    .set(DrugLot::getRecallId, recallId)
                    .set(DrugLot::getRecalledAt, recalledAt)
                    .set(DrugLot::getRecallReason, reason)
                    .set(DrugLot::getRecallStockQty, stock)
                    .set(DrugLot::getUpdatedAt, recalledAt)
                    .setSql("version = version + 1"));
        }
        return result(lots);
    }

    private List<DrugLot> selectLots(Long catalogId, String lotNo) {
        return new ArrayList<>(lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .eq(DrugLot::getCatalogId, catalogId)
                .eq(DrugLot::getLotNo, lotNo)
                .orderByAsc(DrugLot::getFarmId)
                .orderByAsc(DrugLot::getId)));
    }

    private static RecallApplyResult result(List<DrugLot> lots) {
        LocalDate expireDate = null;
        List<RecallLot> rows = new ArrayList<>();
        for (DrugLot lot : lots) {
            if (lot.getExpireDate() != null
                    && (expireDate == null || lot.getExpireDate().isAfter(expireDate))) {
                expireDate = lot.getExpireDate();
            }
            rows.add(new RecallLot(lot.getId(), lot.getFarmId(), lot.getStockQty(), lot.getExpireDate()));
        }
        return new RecallApplyResult(expireDate, rows);
    }
}
