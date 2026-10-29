package com.zhimu.server.biz.demo.job;

import com.zhimu.server.biz.inspect.job.InspectionTodoSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 演示数据平移：把示例行的日期列整体后移到“今天”，可重复执行（同一天第二次执行无变化）。
 * <ol>
 *     <li>按 t_demo_anchor 每组锚点计算天数（今天 - 锚点，大于 0 才平移）；</li>
 *     <li>在 t_demo_shift_work 中算出每张表要平移的行与天数（示例行、分组、跟随来源）；</li>
 *     <li>删除与平移后示例行撞唯一键、且未被处理过的生成任务（连同待办）；</li>
 *     <li>两段式更新：先移到远期区间、再移回目标日期，避免示例行之间互相撞唯一键；
 *         目标位置被用户数据占用时整体回滚；</li>
 *     <li>修正待办去重键中的日期、恢复被定时任务按旧日期自动关闭的示例任务；</li>
 *     <li>示例行里晚于“现在”的已发生时刻（创建、生成、登记等）压回现在，最后更新锚点。</li>
 * </ol>
 * 全程一个事务，任何失败整体回滚且不推进锚点，不影响服务启动。
 */
@Slf4j
public class DemoDataShifter {

    static final String BIZ_REPORT = "t_biz_report";
    static final String WORK = "t_demo_shift_work";
    static final String ANCHOR = "t_demo_anchor";
    static final String REGISTRY = "t_demo_sample_row";
    /** 第一段平移额外加的天数（约 274 年），远期区间内不会有真实数据 */
    static final int FAR_DAYS = 100_000;
    static final String GUARD_MIN = "1970-01-01";
    static final String GUARD_MAX = "2100-01-01";
    static final String FAR_MIN = "2200-01-01";
    static final String FAR_MAX = "2500-01-01";

    private static final Pattern DATE_TOKEN = Pattern.compile("(?<!\\d)(20\\d{6})(?!\\d)");
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoDataShifter(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    DemoDataShifter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 平移结果：各组天数、各表平移/跳过/清理行数 */
    public record Result(boolean executed, Map<String, Integer> groupDays, int moved, int skipped, int cleaned,
                         List<String> failedTables) {

        static Result noop(Map<String, Integer> days) {
            return new Result(false, days, 0, 0, 0, List.of());
        }
    }

    /** 启动入口：任何异常只记日志，不影响服务启动 */
    public Result shiftQuietly(LocalDate today, LocalDateTime now) {
        try {
            return shift(today, now);
        } catch (RuntimeException e) {
            log.error("演示数据平移失败，已回滚并跳过，不影响启动", e);
            return Result.noop(Map.of());
        }
    }

    public Result shift(LocalDate today, LocalDateTime now) {
        Set<String> tables = existingTables();
        if (!tables.contains(ANCHOR) || !tables.contains(WORK) || !tables.contains(REGISTRY)) {
            log.info("没有演示数据锚点表（非 dev 示例库），跳过平移");
            return Result.noop(Map.of());
        }
        return tx.execute(status -> doShift(status, tables, today, now));
    }

    private Result doShift(TransactionStatus status, Set<String> tables, LocalDate today, LocalDateTime now) {
        Map<String, LocalDate> anchors = new LinkedHashMap<>();
        jdbc.query("SELECT group_code, anchor_date FROM " + ANCHOR + " ORDER BY group_code FOR UPDATE",
                (RowCallbackHandler) rs -> {
                    anchors.put(rs.getString(1), rs.getDate(2).toLocalDate());
                });
        Map<String, Integer> days = new LinkedHashMap<>();
        for (String group : DemoShiftPlan.groups()) {
            days.put(group, deltaDays(anchors.get(group), today));
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + REGISTRY + " WHERE anchor_date < ?", Integer.class, Date.valueOf(today)) == 0) {
            int clamped = clampPastTimes(tables, now);
            log.info("演示数据锚点 {} 均不早于今天 {}，无需平移；晚于现在的已发生时刻压回现在 {} 行", anchors, today, clamped);
            return Result.noop(days);
        }
        log.info("演示数据平移开始：今天 {}，锚点 {}，平移天数 {}", today, anchors, days);

        Map<String, List<String>> dateColumns = dateColumns();
        Map<String, List<List<String>>> uniqueKeys = uniqueKeys();
        jdbc.update("DELETE FROM " + WORK);

        List<DemoShiftPlan.TableRule> rules = new ArrayList<>();
        for (DemoShiftPlan.TableRule rule : DemoShiftPlan.tables()) {
            if (tables.contains(rule.table())) {
                rules.add(rule);
            }
        }
        for (DemoShiftPlan.TableRule rule : rules) {
            collectWork(rule, today);
        }
        jdbc.update("DELETE FROM " + WORK + " WHERE delta <= 0");

        int skipped = protectOccupiedTargets(rules, dateColumns, uniqueKeys, today);
        int cleaned = 0;
        for (DemoShiftPlan.TableRule rule : rules) {
            cleaned += cleanup(rule, shiftedColumns(rule, dateColumns), uniqueKeys.getOrDefault(rule.table(), List.of()));
        }

        int moved = 0;
        for (DemoShiftPlan.TableRule rule : rules) {
            int[] counts = BIZ_REPORT.equals(rule.table())
                    ? moveReports(today) : move(rule.table(), shiftedColumns(rule, dateColumns));
            moved += counts[0];
        }
        // Newly generated, untouched rows following a sample source become tracked with their own anchor.
        jdbc.update("INSERT IGNORE INTO " + REGISTRY + " (table_name,row_id,group_code,anchor_date) "
                + "SELECT table_name,row_id,'DERIVED',? FROM " + WORK, Date.valueOf(today));
        jdbc.update("UPDATE " + REGISTRY + " r JOIN " + WORK
                + " w ON w.table_name=r.table_name AND w.row_id=r.row_id SET r.anchor_date=?", Date.valueOf(today));

        rewriteDateKeys("t_todo", "dedup_key");
        rewriteDateKeys("t_todo_remind", "cycle_key");
        int reopened = reopenAutoClosed(today, now);
        jdbc.update("UPDATE t_batch_withdrawal w JOIN (SELECT r.*,ROW_NUMBER() OVER(PARTITION BY r.farm_id,r.batch_id "
                + "ORDER BY r.withdrawal_end_date DESC,r.id DESC) rn FROM t_medication_record r "
                + "WHERE r.deleted=0 AND EXISTS(SELECT 1 FROM " + REGISTRY
                + " d WHERE d.table_name='t_batch' AND d.row_id=r.batch_id)) x "
                + "ON x.batch_id=w.batch_id AND x.farm_id=w.farm_id AND x.rn=1 "
                + "SET w.release_date=x.withdrawal_end_date,w.decisive_record_id=x.id,w.decisive_drug_name=x.drug_name,"
                + "w.last_med_date=x.last_date,w.withdrawal_days=x.withdrawal_days,w.recalculated_at=?", Timestamp.valueOf(now));
        int clamped = clampPastTimes(tables, now);

        for (Map.Entry<String, Integer> entry : days.entrySet()) {
            if (entry.getValue() > 0) {
                jdbc.update("UPDATE " + ANCHOR + " SET anchor_date = ?, shifted_at = ?, shift_days = ?, updated_at = ? "
                                + "WHERE group_code = ?", Date.valueOf(today), Timestamp.valueOf(now), entry.getValue(),
                        Timestamp.valueOf(now), entry.getKey());
            }
        }
        jdbc.update("DELETE FROM " + WORK);
        log.info("演示数据平移完成：天数 {}，平移 {} 行，目标日期被占用跳过 {} 行，清理生成任务 {} 条，恢复自动关闭 {} 条，"
                        + "晚于现在的已发生时刻压回现在 {} 行，失败表 {}",
                days, moved, skipped, cleaned, reopened, clamped, List.of());
        return new Result(true, days, moved, skipped, cleaned, List.of());
    }

    // ===== 计算待平移行 =====

    private void collectWork(DemoShiftPlan.TableRule rule, LocalDate today) {
        String table = rule.table();
        String derived = switch (table) {
            case "t_immune_plan" -> " AND (r.group_code <> 'DERIVED' OR (t.source='TEMPLATE' AND t.created_by=0 AND t.record_id IS NULL AND t.version=0))";
            case "t_todo" -> " AND (r.group_code <> 'DERIVED' OR (t.status='PENDING' AND t.created_by=0))";
            default -> "";
        };
        jdbc.update("INSERT IGNORE INTO " + WORK + " (table_name,row_id,delta) "
                + "SELECT ?,t.id,DATEDIFF(?,r.anchor_date) FROM " + q(table)
                + " t JOIN " + REGISTRY + " r ON r.table_name=? AND r.row_id=t.id WHERE r.anchor_date < ?"
                + derived, table, Date.valueOf(today), table, Date.valueOf(today));
        for (DemoShiftPlan.Follow follow : rule.follows()) {
            jdbc.update("INSERT IGNORE INTO " + WORK + " (table_name,row_id,delta) SELECT ?,t.id,w.delta FROM "
                    + q(table) + " t JOIN " + WORK + " w ON w.table_name=? AND w.row_id=t." + q(follow.fkColumn())
                    + " WHERE NOT EXISTS(SELECT 1 FROM " + REGISTRY + " r WHERE r.table_name=? AND r.row_id=t.id)"
                    + (follow.condition() == null ? "" : " AND (" + follow.condition() + ")"),
                    table, follow.parentTable(), table);
        }
    }

    /** Keep user-owned slots and linked sample history unchanged; retry blocked sample rows on a later restart. */
    private int protectOccupiedTargets(List<DemoShiftPlan.TableRule> rules,
                                      Map<String,List<String>> columns, Map<String,List<List<String>>> keys,
                                      LocalDate today) {
        int total = 0;
        int removed;
        do {
            removed = 0;
            for (DemoShiftPlan.TableRule rule : rules) {
                for (List<String> key : keys.getOrDefault(rule.table(), List.of())) {
                    String query = occupantSql(rule.table(), key, shiftedColumns(rule, columns), "1=1");
                    if (query == null || BIZ_REPORT.equals(rule.table())) continue;
                    query = query.replace("SELECT DISTINCT o.id", "SELECT DISTINCT s.id");
                    String allowed = effectiveOccupantPolicy(rule);
                    if (allowed != null) query += " AND NOT (" + allowed + ")";
                    List<Long> blocked = jdbc.queryForList(query, Long.class, rule.table(), rule.table());
                    removeFromWork(rule.table(), blocked);
                    removed += blocked.size();
                }
            }
            removed += pruneSkippedDependents(today);
            total += removed;
        } while (removed > 0);
        return total;
    }

    private String effectiveOccupantPolicy(DemoShiftPlan.TableRule rule) {
        if (rule.occupantPolicy() == null) return null;
        String policy = "(" + rule.occupantPolicy() + ")";
        if (rule.todoBizType() != null) {
            policy += " AND NOT EXISTS(SELECT 1 FROM t_todo td WHERE td.biz_type='" + rule.todoBizType()
                    + "' AND td.biz_id=o.id AND (td.completed_by IS NOT NULL OR td.ignored_by IS NOT NULL "
                    + "OR EXISTS(SELECT 1 FROM t_todo_handle_log h WHERE h.todo_id=td.id)))";
        }
        return policy;
    }

    private int pruneSkippedDependents(LocalDate today) {
        int count = 0;
        String[][] links = {
            {"t_feed_record","task_id","t_feed_task",null},
            {"t_inspection_item_result","inspection_id","t_inspection",null},
            {"t_abnormal_report","inspection_id","t_inspection",null},
            {"t_abnormal_report_symptom","report_id","t_abnormal_report",null},
            {"t_ai_diagnosis","report_id","t_abnormal_report",null},
            {"t_ai_diagnosis_cause","diagnosis_id","t_ai_diagnosis",null},
            {"t_ai_diagnosis_advice","diagnosis_id","t_ai_diagnosis",null},
            {"t_diagnosis_handle","report_id","t_abnormal_report",null},
            {"t_batch_material_cost","flow_id","t_stock_flow",null},
            {"t_todo","biz_id","t_feed_task","c.biz_type='FEED_TASK'"},
            {"t_todo","biz_id","t_inspection","c.biz_type='INSPECTION'"},
            {"t_todo","biz_id","t_disinfect_task","c.biz_type='DISINFECT_TASK'"},
            {"t_todo","biz_id","t_immune_plan","c.biz_type='IMMUNE_PLAN'"},
            {"t_todo_remind","todo_id","t_todo",null},
            {"t_todo_handle_log","todo_id","t_todo",null},
            {"t_stock_flow","biz_id","t_feed_record","c.biz_type='FEED_RECORD'"},
            {"t_stock_flow","biz_id","t_immune_record","c.biz_type='IMMUNE_RECORD'"},
            {"t_stock_flow","biz_id","t_medication_record","c.biz_type='MEDICATION_RECORD'"},
            {"t_stock_flow","biz_id","t_disinfect_record","c.biz_type='DISINFECT_RECORD'"}
        };
        for (String[] link : links) {
            count += jdbc.update("DELETE w FROM " + WORK + " w JOIN " + q(link[0]) + " c ON w.table_name=? AND w.row_id=c.id "
                    + "JOIN " + REGISTRY + " r ON r.table_name=? AND r.row_id=c." + q(link[1])
                    + " LEFT JOIN " + WORK + " p ON p.table_name=r.table_name AND p.row_id=r.row_id "
                    + "WHERE r.anchor_date < ? AND p.row_id IS NULL" + (link[3] == null ? "" : " AND " + link[3]),
                    link[0],link[2],Date.valueOf(today));
        }
        return count;
    }

    // ===== 清理与示例行冲突的生成任务 =====

    private int cleanup(DemoShiftPlan.TableRule rule, List<String> shifted, List<List<String>> keys) {
        Set<Long> ids = new LinkedHashSet<>();
        if (rule.cleanupSql() != null) {
            ids.addAll(jdbc.queryForList(rule.cleanupSql(), Long.class));
        }
        if (rule.occupantPolicy() != null) {
            for (List<String> key : keys) {
                String sql = occupantSql(rule.table(), key, shifted, effectiveOccupantPolicy(rule));
                if (sql != null) {
                    ids.addAll(jdbc.queryForList(sql, Long.class, rule.table(), rule.table()));
                }
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        if (rule.todoBizType() != null) {
            ids.removeIf(id -> jdbc.queryForObject("SELECT COUNT(*) FROM t_todo td WHERE td.biz_type=? AND td.biz_id=? "
                    + "AND (td.completed_by IS NOT NULL OR td.ignored_by IS NOT NULL OR EXISTS(SELECT 1 "
                    + "FROM t_todo_handle_log h WHERE h.todo_id=td.id))", Integer.class, rule.todoBizType(), id) > 0);
        }
        for (List<Long> chunk : chunks(new ArrayList<>(ids))) {
            String in = placeholders(chunk.size());
            if (rule.todoBizType() != null) {
                List<Object> args = new ArrayList<>();
                args.add(rule.todoBizType());
                args.addAll(chunk);
                List<Long> todoIds = jdbc.queryForList("SELECT td.id FROM t_todo td WHERE td.biz_type = ? AND td.biz_id IN ("
                        + in + ") AND NOT EXISTS (SELECT 1 FROM " + WORK
                        + " w WHERE w.table_name = 't_todo' AND w.row_id = td.id)", Long.class, args.toArray());
                deleteTodos(todoIds);
            }
            jdbc.update("DELETE FROM " + q(rule.table()) + " WHERE id IN (" + in + ")", chunk.toArray());
        }
        log.info("演示数据平移前清理 {}：{} 条未处理的生成行与示例行冲突或相位失效", rule.table(), ids.size());
        return ids.size();
    }

    private void deleteTodos(List<Long> todoIds) {
        for (List<Long> chunk : chunks(todoIds)) {
            String in = placeholders(chunk.size());
            jdbc.update("DELETE FROM t_todo_remind WHERE todo_id IN (" + in + ")", chunk.toArray());
            jdbc.update("DELETE FROM t_todo_handle_log WHERE todo_id IN (" + in + ")", chunk.toArray());
            jdbc.update("DELETE FROM t_todo WHERE id IN (" + in + ")", chunk.toArray());
        }
    }

    /** 唯一键含平移日期列时，找出“目标位置上未参与平移、符合清理条件”的行；参数：表名、表名 */
    static String occupantSql(String table, List<String> keyColumns, List<String> shifted, String policy) {
        if (keyColumns.stream().noneMatch(shifted::contains)) {
            return null;
        }
        StringJoiner on = new StringJoiner(" AND ");
        for (String column : keyColumns) {
            on.add(shifted.contains(column)
                    ? "o." + q(column) + " = s." + q(column) + " + INTERVAL w.delta DAY"
                    : "o." + q(column) + " = s." + q(column));
        }
        // 先取待平移行 s 及其 delta，再关联目标位置 o；否则 ON 里的 w.delta 无法解析。
        return "SELECT DISTINCT o.id FROM " + q(table) + " s"
                + " JOIN " + WORK + " w ON w.table_name = ? AND w.row_id = s.id"
                + " JOIN " + q(table) + " o ON " + on
                + " WHERE o.id <> s.id AND NOT EXISTS (SELECT 1 FROM " + WORK
                + " ow WHERE ow.table_name = ? AND ow.row_id = o.id) AND (" + policy + ")";
    }

    // ===== 两段式平移 =====

    /** @return [平移行数, 跳过行数] */
    private int[] move(String table, List<String> columns) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM " + WORK + " WHERE table_name=?", Integer.class, table);
        if (total == null || total == 0 || columns.isEmpty()) return new int[]{0, 0};
        // Both phases are strict: a user-owned occupied target rolls back the complete shift and its anchors.
        jdbc.update(moveSql(table, columns, MovePhase.TO_FAR), table);
        jdbc.update(moveSql(table, columns, MovePhase.BACK_TO_TARGET), table);
        return new int[]{total, 0};
    }

    private void removeFromWork(String table, List<Long> ids) {
        for (List<Long> chunk : chunks(ids)) {
            List<Object> args = new ArrayList<>();
            args.add(table);
            args.addAll(chunk);
            jdbc.update("DELETE FROM " + WORK + " WHERE table_name = ? AND row_id IN ("
                    + placeholders(chunk.size()) + ")", args.toArray());
        }
    }

    /** 至少一个日期列在可平移范围内（第一段应当被移到远期区间） */
    static String guardCondition(List<String> columns) {
        StringJoiner any = new StringJoiner(" OR ", "(", ")");
        for (String column : columns) {
            String c = "t." + q(column);
            any.add("(" + c + " >= '" + GUARD_MIN + "' AND " + c + " < '" + GUARD_MAX + "')");
        }
        return any.toString();
    }

    enum MovePhase { TO_FAR, BACK_TO_TARGET, BACK_TO_ORIGIN }

    /** 生成某一段的 UPDATE；参数：表名 */
    static String moveSql(String table, List<String> columns, MovePhase phase) {
        StringJoiner set = new StringJoiner(", ");
        for (String column : columns) {
            String c = "t." + q(column);
            switch (phase) {
                case TO_FAR -> set.add(c + " = IF(" + c + " >= '" + GUARD_MIN + "' AND " + c + " < '" + GUARD_MAX
                        + "', " + c + " + INTERVAL (w.delta + " + FAR_DAYS + ") DAY, " + c + ")");
                case BACK_TO_TARGET -> set.add(c + " = IF(" + far(c) + ", " + c + " - INTERVAL " + FAR_DAYS
                        + " DAY, " + c + ")");
                case BACK_TO_ORIGIN -> set.add(c + " = IF(" + far(c) + ", " + c + " - INTERVAL (w.delta + "
                        + FAR_DAYS + ") DAY, " + c + ")");
            }
        }
        String sql = "UPDATE " + q(table) + " t JOIN " + WORK + " w ON w.table_name = ? AND w.row_id = t.id SET "
                + set;
        return phase == MovePhase.TO_FAR ? sql : sql + " WHERE " + farCondition(columns);
    }

    static String farCondition(List<String> columns) {
        StringJoiner any = new StringJoiner(" OR ", "(", ")");
        for (String column : columns) {
            any.add(far("t." + q(column)));
        }
        return any.toString();
    }

    private static String far(String column) {
        return "(" + column + " >= '" + FAR_MIN + "' AND " + column + " < '" + FAR_MAX + "')";
    }

    // ===== 经营报表：按整周 / 整月平移，保持周一、月初对齐 =====

    private int[] moveReports(LocalDate today) {
        List<Integer> deltas = jdbc.queryForList("SELECT DISTINCT delta FROM " + WORK + " WHERE table_name = ?",
                Integer.class, BIZ_REPORT);
        int moved = 0;
        for (Integer delta : deltas) {
            ReportShift shift = reportShift(today.minusDays(delta), today);
            String ids = "SELECT row_id FROM " + WORK + " WHERE table_name = '" + BIZ_REPORT + "' AND delta = ?";
            if (shift.weekDays() > 0) {
                moved += jdbc.update("UPDATE " + BIZ_REPORT + " SET period_start = period_start + INTERVAL ? DAY, "
                                + "period_end = period_end + INTERVAL ? DAY, generated_at = generated_at + INTERVAL ? DAY, "
                                + "created_at = created_at + INTERVAL ? DAY, updated_at = updated_at + INTERVAL ? DAY "
                                + "WHERE report_type = 'WEEKLY' AND id IN (" + ids + ") ORDER BY period_start DESC",
                        shift.weekDays(), shift.weekDays(), shift.weekDays(), shift.weekDays(), shift.weekDays(), delta);
            }
            if (shift.months() > 0) {
                moved += jdbc.update("UPDATE " + BIZ_REPORT + " SET period_start = period_start + INTERVAL ? MONTH, "
                                + "period_end = LAST_DAY(period_end + INTERVAL ? MONTH), "
                                + "generated_at = generated_at + INTERVAL ? MONTH, created_at = created_at + INTERVAL ? MONTH, "
                                + "updated_at = updated_at + INTERVAL ? MONTH "
                                + "WHERE report_type = 'MONTHLY' AND id IN (" + ids + ") ORDER BY period_start DESC",
                        shift.months(), shift.months(), shift.months(), shift.months(), shift.months(), delta);
            }
        }
        return new int[]{moved, 0};
    }

    /** 周报按周一对齐的整周天数、月报按自然月的月数 */
    record ReportShift(int weekDays, int months) {
    }

    static ReportShift reportShift(LocalDate anchor, LocalDate today) {
        LocalDate fromMonday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate toMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekDays = (int) Math.max(0, ChronoUnit.DAYS.between(fromMonday, toMonday));
        int months = (int) Math.max(0, ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(today)));
        return new ReportShift(weekDays, months);
    }

    // ===== 平移后的修正 =====

    /** 待办去重键、提醒周期键里的 yyyyMMdd 随行一起平移 */
    private void rewriteDateKeys(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id, t." + q(column) + " AS k, w.delta FROM "
                + q(table) + " t JOIN " + WORK + " w ON w.table_name = ? AND w.row_id = t.id WHERE t." + q(column)
                + " REGEXP '20[0-9]{6}'", table);
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("k");
            String shifted = shiftDateTokens(key, ((Number) row.get("delta")).intValue());
            if (!shifted.equals(key)) {
                jdbc.update("UPDATE " + q(table) + " SET " + q(column) + " = ? WHERE id = ?", shifted,
                        ((Number) row.get("id")).longValue());
            }
        }
    }

    static String shiftDateTokens(String value, int delta) {
        if (value == null || delta == 0) {
            return value;
        }
        Matcher matcher = DATE_TOKEN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = token;
            try {
                replacement = LocalDate.parse(token, BASIC).plusDays(delta).format(BASIC);
            } catch (DateTimeParseException ignored) {
                // 不是日期的 8 位数字保持原样
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 示例任务在旧日期下被定时任务自动关闭（饲喂超过补登期限、巡检占位行漏巡），平移后又回到期限内时恢复为待处理，
     * 对应的自动忽略待办一并恢复；用户处理过的记录不动。
     */
    private int reopenAutoClosed(LocalDate today, LocalDateTime now) {
        int count = jdbc.update("UPDATE t_feed_task t JOIN " + WORK + " w ON w.table_name = 't_feed_task' AND w.row_id = t.id "
                + "SET t.status = 'PENDING', t.close_reason = NULL WHERE t.status = 'MISSED' AND t.close_reason = ? "
                + "AND t.record_id IS NULL AND t.handled_by IS NULL AND t.backfill_end_at > ?",
                DemoShiftPlan.FEED_BACKFILL_EXPIRED, Timestamp.valueOf(now));
        count += jdbc.update("UPDATE t_todo td JOIN " + WORK + " w ON w.table_name = 't_todo' AND w.row_id = td.id "
                + "JOIN t_feed_task t ON td.biz_type = 'FEED_TASK' AND t.id = td.biz_id AND t.farm_id = td.farm_id "
                + "SET td.status = 'PENDING', td.ignored_at = NULL, td.ignored_by = NULL, td.ignore_reason = NULL, "
                + "td.ignore_way = NULL WHERE td.status = 'IGNORED' AND td.ignore_way = 'AUTO' AND td.ignore_reason = ? "
                + "AND t.status IN ('PENDING','OVERDUE')", DemoShiftPlan.FEED_BACKFILL_EXPIRED);
        count += jdbc.update("UPDATE t_inspection t JOIN " + WORK + " w ON w.table_name = 't_inspection' AND w.row_id = t.id "
                + "SET t.inspect_status = 'PENDING' WHERE t.inspect_status = 'MISSED' AND t.registrant_id IS NULL "
                + "AND t.deleted = 0 AND t.inspect_date >= ?", Date.valueOf(today));
        count += jdbc.update("UPDATE t_todo td JOIN " + WORK + " w ON w.table_name = 't_todo' AND w.row_id = td.id "
                        + "SET td.status = 'PENDING', td.ignored_at = NULL, td.ignored_by = NULL, td.ignore_reason = NULL, "
                        + "td.ignore_way = NULL WHERE td.todo_type = 'INSPECTION' AND td.status = 'IGNORED' "
                        + "AND td.ignore_way = 'AUTO' AND td.ignore_reason = ? AND td.due_date >= ?",
                InspectionTodoSource.EXPIRED_REASON,
                Date.valueOf(today.minusDays(InspectionTodoSource.BACKFILL_DAYS)));
        return count;
    }

    // ===== 已发生时刻不晚于现在 =====

    /**
     * 示例行里“已发生”的时刻（登记、生成、处理、发送、创建 / 更新时间等）平移后不能晚于现在。
     * 会落到未来的有三类：按锚点日编写的当天晚些时刻、迁移执行时写入的 NOW()、晚于锚点编写的行
     * （如 V9_28 把 7303 出栏建议改成 10-25 生成，锚点仍是 10-19）。计划类时刻（任务截止、逾期、提醒）不动。
     *
     * @return 改动的行数
     */
    int clampPastTimes(Set<String> tables, LocalDateTime now) {
        Map<String, List<String>> datetimeColumns = datetimeColumns();
        Timestamp at = Timestamp.valueOf(now);
        int total = 0;
        for (DemoShiftPlan.TableRule rule : DemoShiftPlan.tables()) {
            String table = rule.table();
            if (!tables.contains(table)) {
                continue;
            }
            List<String> columns = new ArrayList<>();
            for (String column : datetimeColumns.getOrDefault(table, List.of())) {
                if (!rule.excludedColumns().contains(column) && DemoShiftPlan.pastOnlyColumn(table, column)) {
                    columns.add(column);
                }
            }
            if (columns.isEmpty()) {
                continue;
            }
            List<Object> args = new ArrayList<>();
            args.add(table);
            for (int i = 0; i < columns.size() * 3; i++) {
                args.add(at);
            }
            total += jdbc.update(clampSql(table, columns), args.toArray());
        }
        return total;
    }

    /** 参数：表名，之后每列 3 个“现在”（SET 两个、WHERE 一个） */
    static String clampSql(String table, List<String> columns) {
        StringJoiner set = new StringJoiner(", ");
        StringJoiner where = new StringJoiner(" OR ", "(", ")");
        for (String column : columns) {
            String c = "t." + q(column);
            set.add(c + " = IF(" + c + " > ?, ?, " + c + ")");
        }
        for (String column : columns) {
            where.add("t." + q(column) + " > ?");
        }
        return "UPDATE " + q(table) + " t JOIN " + REGISTRY + " r ON r.table_name = ? AND r.row_id = t.id SET "
                + set + " WHERE " + where;
    }

    // ===== 元数据 =====

    private Set<String> existingTables() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()", String.class));
    }

    private Map<String, List<String>> dateColumns() {
        Map<String, List<String>> map = new HashMap<>();
        jdbc.query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND DATA_TYPE IN ('date', 'datetime') AND EXTRA NOT LIKE '%VIRTUAL%' AND EXTRA NOT LIKE '%STORED%' "
                + "ORDER BY TABLE_NAME, ORDINAL_POSITION", (RowCallbackHandler) rs -> {
                    map.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return map;
    }

    private Map<String, List<String>> datetimeColumns() {
        Map<String, List<String>> map = new HashMap<>();
        jdbc.query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND DATA_TYPE = 'datetime' AND EXTRA NOT LIKE '%VIRTUAL%' AND EXTRA NOT LIKE '%STORED%' "
                + "ORDER BY TABLE_NAME, ORDINAL_POSITION", (RowCallbackHandler) rs -> {
                    map.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return map;
    }

    private Map<String, List<List<String>>> uniqueKeys() {
        Map<String, Map<String, List<String>>> byIndex = new LinkedHashMap<>();
        Set<String> functional = new HashSet<>();
        jdbc.query("SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY' "
                + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX", (RowCallbackHandler) rs -> {
                    String key = rs.getString(1) + "." + rs.getString(2);
                    String column = rs.getString(3);
                    if (column == null) {
                        functional.add(key);
                    }
                    byIndex.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
                            .computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(column);
                });
        Map<String, List<List<String>>> result = new HashMap<>();
        byIndex.forEach((table, indexes) -> indexes.forEach((index, columns) -> {
            if (!functional.contains(table + "." + index)) {
                result.computeIfAbsent(table, k -> new ArrayList<>()).add(columns);
            }
        }));
        return result;
    }

    private static List<String> shiftedColumns(DemoShiftPlan.TableRule rule, Map<String, List<String>> dateColumns) {
        List<String> columns = new ArrayList<>();
        for (String column : dateColumns.getOrDefault(rule.table(), List.of())) {
            if (!rule.excludedColumns().contains(column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    // ===== 工具 =====

    static int deltaDays(LocalDate anchor, LocalDate today) {
        if (anchor == null || today == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(anchor, today));
    }

    private static String q(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法标识符：" + identifier);
        }
        return "`" + identifier + "`";
    }

    private static String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private static List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            chunks.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return chunks;
    }
}
