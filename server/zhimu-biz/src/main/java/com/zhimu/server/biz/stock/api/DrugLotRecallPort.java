package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台停用批号端口（管理端-03 ADM-D36 / D37 调用，模块09 实现）。
 * <p>
 * 管理端只通过本接口读写 t_drug_lot：停用按“目录 + 批号”精确匹配，给所有养殖场该批号写停用快照，
 * 并返回停用时各场结存；用户端批号详情与可选批号据此显示“已停用，只能报废”。
 */
public interface DrugLotRecallPort {

    /** 预览：该目录 + 批号在各养殖场的批号行（含结存为 0 的历史入库记录），不写库 */
    RecallApplyResult holds(Long catalogId, String lotNo);

    /**
     * 停用：给所有养殖场该批号写 recall_id / recalled_at / recall_reason / recall_stock_qty；
     * 返回停用时的批号行，供管理端写 t_drug_lot_recall_farm 与汇总。
     */
    RecallApplyResult markRecalled(Long catalogId, String lotNo, Long recallId, String reason,
                                   LocalDateTime recalledAt);

    /**
     * @param expireDate 批号有效期（多条取最晚的非空值），可空
     * @param lots       匹配到的批号行（含结存为 0）
     */
    record RecallApplyResult(LocalDate expireDate, List<RecallLot> lots) {
        public RecallApplyResult {
            lots = lots == null ? List.of() : List.copyOf(lots);
        }
    }

    /** 一个养殖场的批号结存行 */
    record RecallLot(Long lotId, Long farmId, BigDecimal stockQty, LocalDate expireDate) {
    }
}
