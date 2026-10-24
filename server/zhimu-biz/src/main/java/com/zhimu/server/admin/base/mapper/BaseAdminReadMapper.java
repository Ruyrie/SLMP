package com.zhimu.server.admin.base.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.query.BannedHitRow;
import com.zhimu.server.admin.base.query.DrugLotRecallRow;
import com.zhimu.server.admin.base.query.IdNameRow;
import com.zhimu.server.admin.base.query.RecallFarmRow;
import com.zhimu.server.admin.base.query.StockTotalRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理端基础数据只读统计（WA-D02/D03/D04）。
 * <p>
 * 只 SELECT，不写业务表；跨模块只读批次、栏舍、种畜、用药、库存表用于“在养批次 / 建档数 / 影响面”，
 * 与 FarmMainCategoryReadMapper 的只读口径一致。召回本身写 t_drug_lot 的动作走 biz.stock 的端口。
 */
public interface BaseAdminReadMapper {

    /** 按免疫模板生成计划的在养批次数（ADM-D14 / D15） */
    @Select("SELECT COUNT(DISTINCT p.batch_id) FROM t_immune_plan p "
            + "JOIN t_batch b ON b.id = p.batch_id "
            + "WHERE p.template_id = #{templateId} AND p.status <> 'CANCELLED' "
            + "AND b.deleted = 0 AND b.status = 'ACTIVE'")
    int countActiveBatchesByTemplate(@Param("templateId") Long templateId);

    /** 在档种畜分布养殖场数（按日期免疫模板，ADM-D15） */
    @Select("SELECT COUNT(DISTINCT a.farm_id) FROM t_breeding_animal a "
            + "WHERE a.breed_id = #{breedId} AND a.status NOT IN ('CULLED', 'DEAD')")
    int countBreedingFarmsByBreed(@Param("breedId") Long breedId);

    /** 使用该品类巡检模板的启用栏舍数（ADM-D40） */
    @Select("SELECT COUNT(*) FROM t_pen WHERE category_id = #{categoryId} AND status = 'ENABLED'")
    int countEnabledPensByCategory(@Param("categoryId") Long categoryId);

    /** 已建药品档案的养殖场数（ADM-D24 / D25 / D31） */
    @Select("SELECT COUNT(DISTINCT farm_id) FROM t_drug WHERE catalog_id = #{catalogId} AND deleted = 0")
    int countArchivedFarmsByCatalog(@Param("catalogId") Long catalogId);

    /** 用过本品的在养批次数（ADM-D25） */
    @Select("SELECT COUNT(DISTINCT m.batch_id) FROM t_medication_record m "
            + "JOIN t_batch b ON b.id = m.batch_id "
            + "WHERE m.catalog_id = #{catalogId} AND m.deleted = 0 "
            + "AND b.deleted = 0 AND b.status = 'ACTIVE'")
    int countActiveBatchesUsingCatalog(@Param("catalogId") Long catalogId);

    /** 现有库存合计（按药品记账单位，ADM-D31） */
    @Select("SELECT SUM(l.stock_qty) AS qty, d.unit AS unit FROM t_drug_lot l "
            + "JOIN t_drug d ON d.id = l.drug_id AND d.deleted = 0 "
            + "WHERE l.catalog_id = #{catalogId} AND l.stock_qty > 0 GROUP BY d.unit")
    List<StockTotalRow> stockTotalsByCatalog(@Param("catalogId") Long catalogId);

    /** 管理员姓名（t_admin 数量少，一次取全量后按 ID 映射） */
    @Select("SELECT id, name FROM t_admin")
    List<IdNameRow> adminNames();

    /** 养殖场名称（召回预览 / 处理进度展示用） */
    @Select("<script>SELECT id, name FROM t_farm WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<IdNameRow> farmNames(@Param("ids") List<Long> ids);

    /** 批号停用消息接收人：有结存养殖场的所有者与场长 */
    @Select("<script>SELECT farm_id AS farmId, user_id AS userId FROM t_farm_member "
            + "WHERE status = 'ACTIVE' AND role IN ('OWNER', 'MANAGER') AND farm_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<com.zhimu.server.admin.base.query.FarmReceiverRow> recallReceivers(@Param("ids") List<Long> farmIds);

    /** ADM-D34 禁用药拦截记录（按时间倒序） */
    @Select("SELECT h.hit_at AS hitAt, h.hit_source AS hitSource, f.name AS farmName, "
            + "u.name AS userName, h.keyword AS keyword "
            + "FROM t_banned_drug_hit h "
            + "LEFT JOIN t_farm f ON f.id = h.farm_id "
            + "LEFT JOIN t_user u ON u.id = h.user_id "
            + "WHERE h.banned_drug_id = #{bannedDrugId} ORDER BY h.hit_at DESC, h.id DESC")
    Page<BannedHitRow> selectHitPage(Page<BannedHitRow> page, @Param("bannedDrugId") Long bannedDrugId);

    /** ADM-D35 批号停用列表（按停用时间倒序，附带已全部报废的养殖场数） */
    @Select("SELECT r.id AS id, r.catalog_id AS catalogId, c.name AS catalogName, "
            + "c.drug_category AS drugCategory, r.lot_no AS lotNo, r.expire_date AS expireDate, "
            + "r.reason AS reason, r.farm_count AS farmCount, r.total_qty AS totalQty, "
            + "r.qty_unit AS qtyUnit, r.recalled_at AS recalledAt, r.recalled_by AS recalledBy, "
            + "(SELECT COUNT(*) FROM t_drug_lot_recall_farm f "
            + " WHERE f.recall_id = r.id AND f.scrap_status = 'SCRAPPED') AS scrappedFarmCount "
            + "FROM t_drug_lot_recall r JOIN t_drug_catalog c ON c.id = r.catalog_id "
            + "ORDER BY r.recalled_at DESC, r.id DESC")
    Page<DrugLotRecallRow> selectRecallPage(Page<DrugLotRecallRow> page);

    /** ADM-D38 处理进度：未报废在前，其次部分报废，最后已报废；同状态按场名 */
    @Select("SELECT f.farm_id AS farmId, fa.name AS farmName, f.qty_at_recall AS qtyAtRecall, "
            + "f.qty_unit AS qtyUnit, f.scrap_status AS scrapStatus, f.scrapped_qty AS scrappedQty, "
            + "f.scrapped_at AS scrappedAt "
            + "FROM t_drug_lot_recall_farm f LEFT JOIN t_farm fa ON fa.id = f.farm_id "
            + "WHERE f.recall_id = #{recallId} "
            + "ORDER BY CASE f.scrap_status WHEN 'UNSCRAPPED' THEN 0 WHEN 'PARTIAL' THEN 1 ELSE 2 END, "
            + "fa.name, f.id")
    Page<RecallFarmRow> selectRecallFarmPage(Page<RecallFarmRow> page, @Param("recallId") Long recallId);
}
