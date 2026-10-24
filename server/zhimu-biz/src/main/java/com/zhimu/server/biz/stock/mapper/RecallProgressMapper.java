package com.zhimu.server.biz.stock.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批号停用处理进度（t_drug_lot_recall_farm）回写：模块09 登记“平台停用”报废时累加已报废数量。
 * 只写管理端-03 的进度表，不读该表其他字段；由模块09 报废事务内调用。
 */
public interface RecallProgressMapper {

    @Update("UPDATE t_drug_lot_recall_farm SET "
            + "scrapped_qty = scrapped_qty + #{qty}, "
            + "scrap_status = CASE WHEN scrapped_qty + #{qty} >= qty_at_recall THEN 'SCRAPPED' "
            + "WHEN scrapped_qty + #{qty} > 0 THEN 'PARTIAL' ELSE 'UNSCRAPPED' END, "
            + "scrapped_at = CASE WHEN scrapped_qty + #{qty} >= qty_at_recall THEN #{date} ELSE NULL END, "
            + "updated_at = #{now} "
            + "WHERE recall_id = #{recallId} AND farm_id = #{farmId}")
    int addScrap(@Param("recallId") Long recallId, @Param("farmId") Long farmId,
                 @Param("qty") BigDecimal qty, @Param("date") LocalDate date,
                 @Param("now") LocalDateTime now);
}
