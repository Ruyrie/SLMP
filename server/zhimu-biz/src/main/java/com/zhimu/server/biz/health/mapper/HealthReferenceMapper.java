package com.zhimu.server.biz.health.mapper;

import com.zhimu.server.biz.health.query.BatchPenStockRow;
import com.zhimu.server.biz.health.query.DisinfectantCatalogRow;
import com.zhimu.server.biz.health.query.DrugStockRow;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.query.FarmMemberRow;
import com.zhimu.server.biz.health.query.ImmuneTemplateItemRow;
import com.zhimu.server.biz.health.query.ImmuneTemplateRow;
import com.zhimu.server.biz.health.query.PenResponsibleRow;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 本模块对其他模块表的只读查询：成员与负责栏舍（模块02）、批次在栏数（模块04）、
 * 免疫模板与项目（3B）、本场药品档案与批号结存（模块09）、种畜在档数（模块05）。
 * 只读，不写别人的表。
 */
public interface HealthReferenceMapper {

    /** 本场有效成员：用户、姓名、角色（按所有者、场长、员工排序） */
    @Select("SELECT m.user_id AS userId, u.name, m.role FROM t_farm_member m "
            + "JOIN t_user u ON u.id = m.user_id "
            + "WHERE m.farm_id = #{farmId} AND m.active_flag = 1 "
            + "ORDER BY FIELD(m.role, 'OWNER', 'MANAGER', 'STAFF'), m.id")
    List<FarmMemberRow> selectMembers(@Param("farmId") Long farmId);

    /** 栏舍负责员工 */
    @Select("SELECT mp.pen_id AS penId, mp.user_id AS userId, u.name FROM t_member_pen mp "
            + "JOIN t_farm_member m ON m.id = mp.member_id AND m.active_flag = 1 "
            + "JOIN t_user u ON u.id = mp.user_id "
            + "WHERE mp.farm_id = #{farmId} ORDER BY mp.pen_id, mp.user_id")
    List<PenResponsibleRow> selectPenResponsibles(@Param("farmId") Long farmId);

    /** 在养批次在各栏舍的在栏数 */
    @Select("SELECT bp.batch_id AS batchId, bp.pen_id AS penId, bp.stock_count AS stockCount "
            + "FROM t_batch_pen bp JOIN t_batch b ON b.id = bp.batch_id "
            + "WHERE bp.farm_id = #{farmId} AND b.status = 'ACTIVE' AND b.deleted = 0 AND bp.stock_count > 0 "
            + "ORDER BY bp.stock_count DESC, bp.pen_id, bp.batch_id")
    List<BatchPenStockRow> selectActiveBatchPens(@Param("farmId") Long farmId);

    @Select("SELECT bp.batch_id AS batchId, bp.pen_id AS penId, bp.stock_count AS stockCount "
            + "FROM t_batch_pen bp WHERE bp.batch_id = #{batchId} AND bp.pen_id = #{penId} LIMIT 1")
    BatchPenStockRow selectBatchPenStock(@Param("batchId") Long batchId, @Param("penId") Long penId);

    @Select("SELECT name FROM t_breed WHERE id = #{breedId}")
    String selectBreedName(@Param("breedId") Long breedId);

    @Select("SELECT name FROM t_user WHERE id = #{userId}")
    String selectUserName(@Param("userId") Long userId);

    @Select("SELECT pen_name FROM t_pen WHERE id = #{penId}")
    String selectPenName(@Param("penId") Long penId);

    /** 品种当前已发布模板与版本；没有模板返回 null */
    @Select("SELECT t.id AS templateId, t.breed_id AS breedId, t.name, t.online_version_no AS onlineVersionNo, "
            + "v.id AS versionId, v.version_no AS versionNo "
            + "FROM t_immune_template t JOIN t_immune_template_version v "
            + "ON v.template_id = t.id AND v.status = 'PUBLISHED' "
            + "WHERE t.breed_id = #{breedId} ORDER BY v.version_no DESC LIMIT 1")
    ImmuneTemplateRow selectPublishedTemplate(@Param("breedId") Long breedId);

    @Select("SELECT id, template_id AS templateId, version_id AS versionId, sort_no AS sortNo, "
            + "vaccine_id AS vaccineId, dose_round AS doseRound, schedule_type AS scheduleType, "
            + "day_age AS dayAge, plan_month AS planMonth, plan_day AS planDay, "
            + "advance_days AS advanceDays, dose, dose_unit AS doseUnit, "
            + "vaccinate_method_code AS vaccinateMethodCode "
            + "FROM t_immune_template_item WHERE version_id = #{versionId} ORDER BY sort_no, id")
    List<ImmuneTemplateItemRow> selectTemplateItems(@Param("versionId") Long versionId);

    /** 本场药品档案（含疫苗、消毒剂） */
    @Select("SELECT id, catalog_id AS catalogId, category, name, spec, unit, pack_unit AS packUnit, "
            + "pack_size AS packSize, status FROM t_drug "
            + "WHERE farm_id = #{farmId} AND deleted = 0 ORDER BY id")
    List<FarmDrugRow> selectDrugs(@Param("farmId") Long farmId);

    /** 各药品可用批号结存合计（未过期、未被平台停用） */
    @Select("SELECT drug_id AS drugId, SUM(stock_qty) AS stockQty FROM t_drug_lot "
            + "WHERE farm_id = #{farmId} AND stock_qty > 0 AND expire_date >= #{today} AND recall_id IS NULL "
            + "GROUP BY drug_id")
    List<DrugStockRow> selectAvailableStock(@Param("farmId") Long farmId, @Param("today") LocalDate today);

    /** 栏舍在档种畜数（状态不是已淘汰、已死亡） */
    @Select("SELECT COUNT(1) FROM t_breeding_animal WHERE farm_id = #{farmId} AND pen_id = #{penId} "
            + "AND status NOT IN ('CULLED', 'DEAD')")
    int countBreedingAnimals(@Param("farmId") Long farmId, @Param("penId") Long penId);

    /** 栏舍在档种畜的品种与数量（按日期免疫项目生成用） */
    @Select("SELECT breed_id AS breedId, COUNT(1) AS stockCount FROM t_breeding_animal "
            + "WHERE farm_id = #{farmId} AND pen_id = #{penId} AND status NOT IN ('CULLED', 'DEAD') "
            + "GROUP BY breed_id")
    List<BreedingBreedRow> selectBreedingBreeds(@Param("farmId") Long farmId, @Param("penId") Long penId);

    /** 消毒剂目录（含推荐浓度与适用消毒方式，供 D35 下拉） */
    @Select("SELECT id, name, concentration, disinfect_method_codes AS methodCodes, measure_unit AS measureUnit "
            + "FROM t_drug_catalog WHERE drug_category = 'DISINFECTANT' AND status = 'ENABLED' ORDER BY id")
    List<DisinfectantCatalogRow> selectDisinfectantCatalogs();

    /** 种畜栏舍在档品种与数量（t_breeding_animal） */
    @Getter
    @Setter
    class BreedingBreedRow {
        private Long breedId;
        private Integer stockCount;
    }
}
