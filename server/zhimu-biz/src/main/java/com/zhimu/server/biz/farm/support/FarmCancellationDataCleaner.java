package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.common.api.AccountFileCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 注销所有者的场到期清理。表名为审核过的固定白名单，所有 SQL 都有精确 farm_id；
 * 不按操作人删除业务记录，不删除全局课程/知识库/用户或其他场的汇总报表。
 * 对象存储不在数据库事务内删除：只把无共享引用的文件交回公共过期文件生命周期。
 */
@Component
@RequiredArgsConstructor
public class FarmCancellationDataCleaner {
    private final JdbcTemplate jdbc;
    private final AccountProperties properties;
    private final AccountFileCleanupService fileCleanup;

    static final List<String> OPERATIONAL_TABLES = List.of(
            "t_pen", "t_batch", "t_breeding_animal", "t_incubation", "t_feed_record", "t_weigh_record",
            "t_inspection", "t_abnormal_report", "t_immune_record", "t_medication_record", "t_disinfect_record",
            "t_stock_inbound", "t_stock_outbound", "t_stock_flow", "t_finance_record", "t_sale_order");

    // 子记录先于父记录；成员/场最后删除，便于调用方在本事务中先取通知接收人。
    static final List<String> FARM_TABLES = List.of(
            "t_notice_recipient", "t_push_record", "t_message", "t_todo_remind", "t_todo_handle_log", "t_todo",
            "t_ai_message", "t_ai_session",
            "t_sale_receipt", "t_batch_settlement_supplement", "t_batch_settlement", "t_sale_advice",
            "t_finance_record", "t_sale_order", "t_expense_subcategory", "t_biz_report_summary", "t_biz_report_export", "t_biz_report",
            "t_batch_material_cost", "t_stocktake_item", "t_stocktake", "t_stock_loss", "t_stock_outbound", "t_stock_inbound",
            "t_stock_flow", "t_drug_lot_recall_farm", "t_drug_lot", "t_drug", "t_feed", "t_stock_partner", "t_stock_setting",
            "t_batch_withdrawal", "t_medication_record", "t_immune_record", "t_immune_plan",
            "t_disinfect_record", "t_disinfect_task", "t_disinfect_plan_pen", "t_disinfect_plan",
            "t_diagnosis_handle", "t_ai_diagnosis_advice", "t_ai_diagnosis_cause", "t_ai_diagnosis",
            "t_abnormal_report_symptom", "t_abnormal_report", "t_inspection_item_result", "t_inspection", "t_inspection_setting",
            "t_weigh_record_item", "t_weigh_record", "t_batch_growth", "t_feed_exec_daily", "t_feed_record", "t_feed_task",
            "t_feed_plan_meal", "t_feed_plan_stage", "t_feed_plan",
            "t_weaning_record", "t_farrowing_record", "t_pregnancy_check", "t_mating_record", "t_breeding_cycle",
            "t_breeding_animal", "t_incubation", "t_batch_timeline", "t_batch_event", "t_batch_stock_daily", "t_batch_pen", "t_batch", "t_pen",
            "t_change_request", "t_operation_log", "t_banned_drug_hit", "t_user_active_day",
            "t_farm_invite_pen", "t_member_pen", "t_farm_invite", "t_farm_transfer", "t_farm_change", "t_farm_audit", "t_farm_member");

    public boolean hasOperationalData(Long farmId) {
        for (String table : OPERATIONAL_TABLES) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE farm_id = ?", Long.class, farmId);
            if (count != null && count > 0) return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(Long farmId, Long ownerId, LocalDateTime now) {
        LocalDateTime cutoff = now.minus(properties.getFarmRetention());
        List<Long> eligible = jdbc.queryForList("""
                SELECT f.id FROM t_farm f JOIN t_user u ON u.id = f.owner_user_id
                WHERE f.id = ? AND f.owner_user_id = ? AND u.status = 'CANCELLED'
                  AND f.delete_type = 'ACCOUNT_CANCEL' AND f.frozen = 0
                  AND ((f.disabled = 1 AND f.audit_status = 'APPROVED' AND f.disabled_at <= ?)
                    OR (f.disabled = 0 AND f.audit_status IN ('PENDING', 'REJECTED')))
                FOR UPDATE
                """, Long.class, farmId, ownerId, Timestamp.valueOf(cutoff));
        if (!eligible.equals(List.of(farmId))) throw new IllegalStateException("Farm is not eligible for cancellation cleanup: " + farmId);

        Set<Long> files = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT file_id FROM t_biz_media WHERE farm_id = ? OR (biz_type = 'FARM' AND biz_id = ?)",
                Long.class, farmId, farmId));
        files.addAll(jdbc.queryForList("SELECT file_id FROM t_biz_report_export WHERE farm_id = ? AND file_id IS NOT NULL", Long.class, farmId));
        files.addAll(jdbc.queryForList("SELECT id FROM t_file WHERE farm_id = ? AND deleted = 0", Long.class, farmId));

        jdbc.update("DELETE FROM t_sale_advice_risk WHERE advice_id IN (SELECT id FROM t_sale_advice WHERE farm_id = ?)", farmId);
        jdbc.update("DELETE FROM t_ai_citation WHERE message_id IN (SELECT id FROM t_ai_message WHERE farm_id = ?)", farmId);
        jdbc.update("DELETE FROM t_ai_correction WHERE message_id IN (SELECT id FROM t_ai_message WHERE farm_id = ?)", farmId);
        // 审核结论与已沉淀知识保留，去掉来源私人正文，不级联删除全局知识任务。
        jdbc.update("UPDATE t_ai_qa_review SET spot_check_title = NULL, spot_check_content = NULL WHERE message_id IN (SELECT id FROM t_ai_message WHERE farm_id = ?)", farmId);
        jdbc.update("UPDATE t_ai_knowledge_task SET source_message_id = NULL WHERE source_message_id IN (SELECT id FROM t_ai_message WHERE farm_id = ?)", farmId);
        jdbc.update("DELETE FROM t_biz_media WHERE farm_id = ? OR (biz_type = 'FARM' AND biz_id = ?)", farmId, farmId);
        for (String table : FARM_TABLES) jdbc.update("DELETE FROM " + table + " WHERE farm_id = ?", farmId);

        fileCleanup.queueFarmFiles(ownerId, farmId, files);
        int removed = jdbc.update("DELETE FROM t_farm WHERE id = ? AND owner_user_id = ? AND delete_type = 'ACCOUNT_CANCEL'", farmId, ownerId);
        if (removed != 1) throw new IllegalStateException("Farm ownership changed during cancellation cleanup: " + farmId);
    }
}
