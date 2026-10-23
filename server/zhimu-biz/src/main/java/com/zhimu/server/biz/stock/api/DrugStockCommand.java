package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 模块08 接种 / 用药 / 消毒记录保存、作废时扣减、回滚药品库存的命令（模块09 提供实现）。
 * <p>
 * 数量为批号记账单位（头份、克、毫升、升）；drugLotId 为空时按“先到期先出”取第一个可用批号（不跨批号拆分）。
 * 调用方负责把记录与库存扣减放进同一事务。
 *
 * @param farmId      养殖场 ID
 * @param drugId      药品档案 ID
 * @param drugLotId   批号 ID，可空（为空时自动选批号）
 * @param qty         扣减数量（记账单位），> 0
 * @param flowType    FLOW_TYPE：VACCINE_USE / MEDICATION_USE / DISINFECT_USE
 * @param bizType     BIZ_TYPE：IMMUNE_RECORD / MEDICATION_RECORD / DISINFECT_RECORD
 * @param bizId       来源记录 ID
 * @param batchId     关联批次 ID，可空
 * @param penId       关联栏舍 ID，可空
 * @param operatorId  经办人 / 执行人用户 ID，可空
 * @param occurAt     发生时间（不传取当前北京时间）
 * @param bizDate     业务日期（不传取 occurAt 的日期）
 * @param relatedDesc 关联说明快照
 */
public record DrugStockCommand(Long farmId, Long drugId, Long drugLotId, BigDecimal qty, String flowType,
                               String bizType, Long bizId, Long batchId, Long penId, Long operatorId,
                               LocalDateTime occurAt, LocalDate bizDate, String relatedDesc) {

    public static final String FLOW_VACCINE_USE = "VACCINE_USE";
    public static final String FLOW_MEDICATION_USE = "MEDICATION_USE";
    public static final String FLOW_DISINFECT_USE = "DISINFECT_USE";

    public static final String BIZ_IMMUNE_RECORD = "IMMUNE_RECORD";
    public static final String BIZ_MEDICATION_RECORD = "MEDICATION_RECORD";
    public static final String BIZ_DISINFECT_RECORD = "DISINFECT_RECORD";
}
