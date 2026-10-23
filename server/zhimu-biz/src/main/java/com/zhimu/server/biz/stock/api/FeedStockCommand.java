package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 模块06 饲喂记录保存 / 作废时扣减、回滚饲料库存的命令（模块09 提供实现）。
 * <p>
 * 数量为记账单位（饲料固定千克）；调用方负责把记录与库存扣减放进同一事务：
 * 记录保存失败时扣减一并回滚。不比对页面版本，只在结存不足时抛 42201（data.current_qty）。
 *
 * @param farmId      养殖场 ID
 * @param feedId      饲料 ID（本场）
 * @param qty         扣减数量（千克），> 0
 * @param bizType     BIZ_TYPE：投喂固定 FEED_RECORD
 * @param bizId       来源记录 ID（模块06 的 t_feed_record.id）
 * @param batchId     关联批次 ID，可空
 * @param penId       关联栏舍 ID，可空
 * @param operatorId  经办人 / 执行人用户 ID，可空
 * @param occurAt     发生时间（不传取当前北京时间）
 * @param bizDate     业务日期（不传取 occurAt 的日期）
 * @param relatedDesc 关联说明快照，例如 ZM2610-031 · 3 号育肥舍 早餐
 */
public record FeedStockCommand(Long farmId, Long feedId, BigDecimal qty, String bizType, Long bizId,
                               Long batchId, Long penId, Long operatorId, LocalDateTime occurAt,
                               LocalDate bizDate, String relatedDesc) {

    public static final String BIZ_FEED_RECORD = "FEED_RECORD";
}
