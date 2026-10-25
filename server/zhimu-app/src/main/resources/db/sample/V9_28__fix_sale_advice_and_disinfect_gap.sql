-- 补最后两个演示数据缺口：出栏建议失败态、消毒计划首日缺任务。
-- 7303 是 3004 批次唯一一条出栏建议且为 FAILED，App 打开批次会看到失败空态；改成规则生成的正常建议。
UPDATE t_sale_advice
SET status = 'SUCCESS',
    gen_mode = 'RULE',
    conclusion = 'KEEP_FEEDING',
    window_start = '2026-11-01',
    window_end = '2026-11-07',
    in_withdrawal = 0,
    reason_age = '63 日龄，存栏 200 头，处于保育转育肥阶段。',
    reason_weight = '最近一次称重均重 32.5 千克，低于同阶段标准约 4%。',
    reason_price = '近 30 天均价 14.40–15.60 元/千克，当前价格不在高点。',
    basis_insufficient = 0,
    basis_note = NULL,
    error_message = NULL,
    started_at = '2026-10-25 12:30:00',
    generated_at = '2026-10-25 12:30:00',
    updated_at = '2026-10-25 12:30:00'
WHERE id = 7303 AND status = 'FAILED';

-- 160401 计划 10-24 只生成了 2003 舍，2004 舍首日任务缺失；补一条 PENDING（已逾期）。
INSERT INTO t_disinfect_task (id, farm_id, pen_id, plan_id, source, task_date, due_time, catalog_id,
                              disinfectant_name, concentration, method_code, status, active_flag, record_id,
                              manual_done, overdue_at, overdue_notified, version, created_by, created_at, updated_at)
SELECT 160504, p.farm_id, 2004, p.id, 'PLAN', '2026-10-24', p.due_time, p.catalog_id,
       p.disinfectant_name, p.concentration, p.method_code, 'PENDING', 1, NULL,
       0, TIMESTAMP('2026-10-24', p.due_time), 0, 0, 10001,
       '2026-10-24 00:10:00', '2026-10-24 00:10:00'
FROM t_disinfect_plan p
WHERE p.id = 160401
  AND NOT EXISTS (SELECT 1 FROM t_disinfect_task t WHERE t.id = 160504);

INSERT IGNORE INTO t_demo_sample_row (table_name, row_id, group_code, anchor_date)
VALUES ('t_disinfect_task', 160504, 'MIGRATION_DAY', '2026-10-25');
