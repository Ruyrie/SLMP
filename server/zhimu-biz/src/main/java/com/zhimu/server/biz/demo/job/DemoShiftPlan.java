package com.zhimu.server.biz.demo.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 演示数据平移的表清单与规则（纯配置，供 {@link DemoDataShifter} 使用）。
 * <ul>
 *     <li>示例行：V9_20 显式登记的 seed ID，或新 sample 迁移显式登记的行。</li>
 *     <li>分组：默认 SAMPLE（按 2026-10-19 编写）；V9_15 的用药/消毒按 10-24 编写（HEALTH_1024）；
 *         V9_16/V9_17 按迁移当天生成（MIGRATION_DAY）。每组各自按“今天 - 锚点”平移。</li>
 *     <li>跟随：生成器为示例行派生的行（待办、模板免疫计划）跟随来源行一起平移。</li>
 *     <li>占位清理：生成器按真实日期生成、未被处理的任务若与平移后的示例行撞唯一键，平移前删除（连同待办）。</li>
 * </ul>
 * 表顺序即处理顺序：被跟随的来源表在前，待办类表在最后。
 */
public final class DemoShiftPlan {

    public static final String GROUP_SAMPLE = "SAMPLE";
    public static final String GROUP_HEALTH_1024 = "HEALTH_1024";
    public static final String GROUP_MIGRATION_DAY = "MIGRATION_DAY";

    /** 饲喂任务补登期限到期后的关闭原因（与 FeedCalc.CLOSE_REASON_BACKFILL_EXPIRED 一致） */
    public static final String FEED_BACKFILL_EXPIRED = "超过补登期限";

    /** 跟随规则：子表 fkColumn 指向的父表行在平移时，子表行用父行的天数（condition 以别名 t 引用子表） */
    public record Follow(String parentTable, String fkColumn, String condition) {
    }

    /**
     * 单表规则。
     *
     * @param table            表名
     * @param groupPredicates  非 SAMPLE 分组的示例行条件（别名 t），按顺序优先匹配；未匹配的示例行归 SAMPLE
     * @param follows          跟随规则（先于自身 SAMPLE 分组匹配）
     * @param excludedColumns  不平移的日期列（运行期记录的登录、切换等时刻）
     * @param occupantPolicy   可删除的“占位行”条件（别名 o）：未参与平移、且与平移后的示例行撞唯一键时删除
     * @param cleanupSql       额外清理：返回需要删除的行 ID 的 SELECT（参数：无），在平移前执行
     * @param todoBizType      本表行被删除时一并删除的待办 biz_type（为空不删待办）
     */
    public record TableRule(String table, Map<String, String> groupPredicates, List<Follow> follows,
                            Set<String> excludedColumns, String occupantPolicy, String cleanupSql,
                            String todoBizType) {
    }

    private static final List<TableRule> TABLES = build();

    private DemoShiftPlan() {
    }

    public static List<TableRule> tables() {
        return TABLES;
    }

    public static List<String> groups() {
        return List.of(GROUP_SAMPLE, GROUP_HEALTH_1024, GROUP_MIGRATION_DAY);
    }

    /**
     * 计划时间列（可以晚于“现在”）：任务的计划时刻、截止 / 补登期限、逾期时刻、提醒时刻、待办排序时刻。
     * 其余 datetime 列都是“已发生”的时刻（登记、生成、处理、发送、创建 / 更新时间等），平移后不应晚于现在。
     */
    private static final Set<String> SCHEDULE_COLUMNS = Set.of("feed_at", "deadline_at", "backfill_end_at",
            "overdue_at", "remind_at", "expire_at");
    private static final Set<String> SCHEDULE_TABLE_COLUMNS = Set.of("t_todo.sort_time");

    /** 平移后需要压到“现在”以内的已发生时刻列（只用于 datetime 列） */
    public static boolean pastOnlyColumn(String table, String column) {
        return !SCHEDULE_COLUMNS.contains(column) && !SCHEDULE_TABLE_COLUMNS.contains(table + "." + column);
    }

    private static List<TableRule> build() {
        List<TableRule> list = new ArrayList<>();
        // 账号与养殖场
        // 存栏批次
        list.add(rule("t_pen").build());
        list.add(rule("t_batch").build());
        list.add(rule("t_batch_pen").build());
        list.add(rule("t_batch_event").build());
        list.add(rule("t_batch_timeline").build());
        list.add(rule("t_batch_stock_daily").build());
        // 繁育
        list.add(rule("t_breeding_animal").build());
        list.add(rule("t_breeding_cycle").build());
        list.add(rule("t_mating_record").build());
        list.add(rule("t_pregnancy_check").build());
        list.add(rule("t_farrowing_record").build());
        list.add(rule("t_weaning_record").build());
        list.add(rule("t_incubation").build());
        // 饲喂
        list.add(rule("t_feed_plan").build());
        list.add(rule("t_feed_plan_stage").build());
        list.add(rule("t_feed_plan_meal").build());
        list.add(rule("t_feed_task")
                // t_feed_task 没有 created_by，饲喂任务全部由系统按方案生成；只要未登记/未处理即可视为可清理占位行。
                .occupant("o.status IN ('PENDING','OVERDUE','MISSED') AND o.record_id IS NULL AND o.handled_by IS NULL")
                .todo("FEED_TASK").build());
        list.add(rule("t_feed_record").build());
        list.add(rule("t_weigh_record").build());
        list.add(rule("t_weigh_record_item").build());
        list.add(rule("t_batch_growth").build());
        list.add(rule("t_feed_exec_daily").build());
        // 巡检
        list.add(rule("t_inspection_setting").build());
        list.add(rule("t_inspection")
                .occupant("o.registrant_id IS NULL AND o.inspect_status IN ('PENDING','MISSED') AND o.created_by=0")
                .todo("INSPECTION").build());
        list.add(rule("t_inspection_item_result").build());
        list.add(rule("t_abnormal_report").build());
        list.add(rule("t_abnormal_report_symptom").build());
        list.add(rule("t_ai_diagnosis").build());
        list.add(rule("t_ai_diagnosis_cause").build());
        list.add(rule("t_ai_diagnosis_advice").build());
        list.add(rule("t_diagnosis_handle").build());
        // 健康：V9_15 用药/消毒按 10-24 编写，V9_16 按迁移当天
        list.add(rule("t_immune_plan")
                .group(GROUP_MIGRATION_DAY, "t.id IN (160101, 160102, 160103)")
                .follow("t_batch", "batch_id", "t.source = 'TEMPLATE' AND t.created_by = 0 AND t.record_id IS NULL AND t.version = 0").build());
        list.add(rule("t_immune_record")
                .group(GROUP_MIGRATION_DAY, "t.id = 160201").build());
        list.add(rule("t_medication_record")
                .group(GROUP_HEALTH_1024, "t.id IN (9101, 9102)")
                .group(GROUP_MIGRATION_DAY, "t.id = 160301").build());
        list.add(rule("t_disinfect_plan")
                .group(GROUP_HEALTH_1024, "t.id IN (9201, 9202)")
                .group(GROUP_MIGRATION_DAY, "t.id = 160401").build());
        list.add(rule("t_disinfect_plan_pen")
                .group(GROUP_HEALTH_1024, "t.plan_id IN (9201, 9202)")
                .group(GROUP_MIGRATION_DAY, "t.plan_id = 160401").build());
        list.add(rule("t_disinfect_task")
                .group(GROUP_HEALTH_1024, "t.plan_id IN (9201, 9202)")
                .group(GROUP_MIGRATION_DAY, "t.plan_id = 160401")
                .occupant(DISINFECT_UNTOUCHED.replace("{a}", "o"))
                // 平移计划的开始日期会改变周期相位：生成器按旧相位生成、未处理的任务全部删除，启动后按新相位重新生成
                .cleanup("SELECT o.id FROM t_disinfect_task o "
                        + "JOIN t_demo_shift_work wp ON wp.table_name = 't_disinfect_plan' AND wp.row_id = o.plan_id "
                        + "WHERE NOT EXISTS (SELECT 1 FROM t_demo_shift_work ow "
                        + "WHERE ow.table_name = 't_disinfect_task' AND ow.row_id = o.id) AND "
                        + DISINFECT_UNTOUCHED.replace("{a}", "o"))
                .todo("DISINFECT_TASK").build());
        list.add(rule("t_disinfect_record")
                .group(GROUP_HEALTH_1024, "t.id IN (9501, 9502, 9503)")
                .group(GROUP_MIGRATION_DAY, "t.id = 160601").build());
        // 库存
        list.add(rule("t_feed").build());
        list.add(rule("t_drug").build());
        list.add(rule("t_drug_lot").build());
        list.add(rule("t_stock_inbound").build());
        list.add(rule("t_stock_outbound").build());
        list.add(rule("t_stock_loss").build());
        list.add(rule("t_stocktake").build());
        list.add(rule("t_stocktake_item").build());
        list.add(rule("t_stock_flow")
                .group(GROUP_HEALTH_1024, HEALTH_1024_SOURCES.replace("{type}", "t.biz_type").replace("{id}", "t.biz_id"))
                .group(GROUP_MIGRATION_DAY, MIGRATION_SOURCES.replace("{type}", "t.biz_type").replace("{id}", "t.biz_id"))
                .build());
        list.add(rule("t_batch_material_cost")
                .group(GROUP_HEALTH_1024, HEALTH_1024_SOURCES.replace("{type}", "t.source_type").replace("{id}", "t.source_id"))
                .group(GROUP_MIGRATION_DAY, MIGRATION_SOURCES.replace("{type}", "t.source_type").replace("{id}", "t.source_id"))
                .build());
        list.add(rule("t_stock_partner").build());
        list.add(rule("t_stock_setting").build());
        // 财务（经营报表按整周/整月平移，由 DemoDataShifter 单独处理）
        list.add(rule("t_expense_subcategory").build());
        list.add(rule("t_finance_record").build());
        list.add(rule("t_sale_order").build());
        list.add(rule("t_sale_receipt").build());
        list.add(rule("t_batch_settlement").build());
        list.add(rule("t_batch_settlement_supplement").build());
        list.add(rule("t_sale_advice").build());
        list.add(rule("t_sale_advice_risk").build());
        list.add(rule(DemoDataShifter.BIZ_REPORT).build());
        // 行情：V9_17 的近 7 天价格（创建时间不是价格日 06:00）按迁移当天；与平移后的 V9_12 价格撞日期时删除 V9_17 行
        list.add(rule("t_market_price")
                .group(GROUP_MIGRATION_DAY, "t.source = 'SEED' AND t.created_at <> TIMESTAMP(t.price_date, '06:00:00')")
                .occupant("o.source = 'SEED' AND o.corrected = 0").build());
        // AI 问答、课程知识
        list.add(rule("t_ai_session").build());
        list.add(rule("t_ai_message").build());
        list.add(rule("t_ai_citation").build());
        list.add(rule("t_ai_correction").build());
        list.add(rule("t_ai_knowledge").build());
        list.add(rule("t_course").build());
        // 今日 / 待办 / 消息：生成的待办跟随来源任务
        list.add(rule("t_message").build());
        list.add(rule("t_todo")
                .follow("t_feed_task", "biz_id", "t.biz_type = 'FEED_TASK' AND t.created_by=0 AND t.status='PENDING'")
                .follow("t_disinfect_task", "biz_id", "t.biz_type = 'DISINFECT_TASK' AND t.created_by=0 AND t.status='PENDING'")
                .follow("t_immune_plan", "biz_id", "t.biz_type = 'IMMUNE_PLAN' AND t.created_by=0 AND t.status='PENDING'")
                .follow("t_inspection", "biz_id", "t.biz_type = 'INSPECTION' AND t.created_by=0 AND t.status='PENDING'").build());
        list.add(rule("t_todo_remind").build());
        list.add(rule("t_todo_handle_log").build());
        return Collections.unmodifiableList(list);
    }

    private static final String DISINFECT_UNTOUCHED = "{a}.source = 'PLAN' AND {a}.status IN ('PENDING','OVERDUE') "
            + "AND {a}.record_id IS NULL AND {a}.manual_done = 0 AND {a}.created_by=0";

    private static final String HEALTH_1024_SOURCES = "(({type} = 'MEDICATION_RECORD' AND {id} IN (9101, 9102)) "
            + "OR ({type} = 'DISINFECT_RECORD' AND {id} IN (9501, 9502, 9503)))";

    private static final String MIGRATION_SOURCES = "(({type} = 'IMMUNE_RECORD' AND {id} = 160201) "
            + "OR ({type} = 'MEDICATION_RECORD' AND {id} = 160301) "
            + "OR ({type} = 'DISINFECT_RECORD' AND {id} = 160601))";

    private static Builder rule(String table) {
        return new Builder(table);
    }

    private static final class Builder {
        private final String table;
        private final Map<String, String> groups = new LinkedHashMap<>();
        private final List<Follow> follows = new ArrayList<>();
        private Set<String> excluded = Set.of();
        private String occupant;
        private String cleanup;
        private String todoBizType;

        private Builder(String table) {
            this.table = table;
        }

        Builder group(String group, String predicate) {
            groups.put(group, predicate);
            return this;
        }

        Builder follow(String parent, String fk, String condition) {
            follows.add(new Follow(parent, fk, condition));
            return this;
        }

        Builder exclude(String... columns) {
            excluded = Set.of(columns);
            return this;
        }

        Builder occupant(String policy) {
            occupant = policy;
            return this;
        }

        Builder cleanup(String sql) {
            cleanup = sql;
            return this;
        }

        Builder todo(String bizType) {
            todoBizType = bizType;
            return this;
        }

        TableRule build() {
            return new TableRule(table, Collections.unmodifiableMap(groups), List.copyOf(follows), excluded,
                    occupant, cleanup, todoBizType);
        }
    }
}
