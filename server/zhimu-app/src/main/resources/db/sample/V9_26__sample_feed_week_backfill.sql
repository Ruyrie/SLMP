-- 补齐近 7 天饲喂任务与投喂记录，让执行情况曲线有完整数据。
-- V9_08 只种了 10-23/10-24 少量任务；这里以现有任务为模板补齐 10-18–10-24，
-- 并把所有已完成任务的投喂记录补齐、回填 task.record_id。新增行登记到日期锚点，后续重启随演示数据一起平移。

DROP TEMPORARY TABLE IF EXISTS tmp_demo_days;
CREATE TEMPORARY TABLE tmp_demo_days (d DATE PRIMARY KEY);
INSERT INTO tmp_demo_days (d)
VALUES ('2026-10-18'), ('2026-10-19'), ('2026-10-20'), ('2026-10-21'),
       ('2026-10-22'), ('2026-10-23'), ('2026-10-24');

-- 东山猪场：以 10-24 的 9 条任务（3 栏舍 × 3 餐）为模板，补齐缺的日期/栏舍
INSERT INTO t_feed_task (id, farm_id, batch_id, pen_id, plan_id, stage_id, task_date, meal_no, meal_code,
                         meal_name, feed_time, feed_at, deadline_at, backfill_end_at, feed_id, head_count,
                         daily_qty_per_head, meals_per_day, plan_qty, assignee_id, status, record_id, todo_id,
                         overdue_notified, version, created_at, updated_at)
SELECT 291000 + (DATEDIFF(d.d, '2026-10-17') - 1) * 100 + (t.pen_id - 2000) * 10 + t.meal_no,
       t.farm_id, t.batch_id, t.pen_id, t.plan_id, t.stage_id, d.d, t.meal_no, t.meal_code, t.meal_name,
       t.feed_time, TIMESTAMP(d.d, t.feed_time), TIMESTAMP(d.d, t.feed_time) + INTERVAL 2 HOUR,
       TIMESTAMP(d.d + INTERVAL 2 DAY, '00:00:00'), t.feed_id, t.head_count, t.daily_qty_per_head,
       t.meals_per_day, t.plan_qty, t.assignee_id, 'DONE', NULL, NULL, 0, 0,
       TIMESTAMP(d.d, '00:05:00'), TIMESTAMP(d.d, t.feed_time) + INTERVAL 10 MINUTE
FROM tmp_demo_days d
         JOIN (SELECT * FROM t_feed_task WHERE farm_id = 1001 AND task_date = '2026-10-24') t
WHERE NOT EXISTS (SELECT 1
                  FROM t_feed_task x
                  WHERE x.batch_id = t.batch_id AND x.pen_id = t.pen_id
                    AND x.task_date = d.d AND x.meal_no = t.meal_no);

-- 西岭鸡场：以 10-25 的 4 条任务（2 栏舍 × 2 餐）为模板
INSERT INTO t_feed_task (id, farm_id, batch_id, pen_id, plan_id, stage_id, task_date, meal_no, meal_code,
                         meal_name, feed_time, feed_at, deadline_at, backfill_end_at, feed_id, head_count,
                         daily_qty_per_head, meals_per_day, plan_qty, assignee_id, status, record_id, todo_id,
                         overdue_notified, version, created_at, updated_at)
SELECT 292000 + (DATEDIFF(d.d, '2026-10-17') - 1) * 100 + (t.pen_id - 2200) * 10 + t.meal_no,
       t.farm_id, t.batch_id, t.pen_id, t.plan_id, t.stage_id, d.d, t.meal_no, t.meal_code, t.meal_name,
       t.feed_time, TIMESTAMP(d.d, t.feed_time), TIMESTAMP(d.d, t.feed_time) + INTERVAL 2 HOUR,
       TIMESTAMP(d.d + INTERVAL 2 DAY, '00:00:00'), t.feed_id, t.head_count, t.daily_qty_per_head,
       t.meals_per_day, t.plan_qty, t.assignee_id, 'DONE', NULL, NULL, 0, 0,
       TIMESTAMP(d.d, '00:05:00'), TIMESTAMP(d.d, t.feed_time) + INTERVAL 10 MINUTE
FROM tmp_demo_days d
         JOIN (SELECT * FROM t_feed_task WHERE farm_id = 1002 AND task_date = '2026-10-25') t
WHERE NOT EXISTS (SELECT 1
                  FROM t_feed_task x
                  WHERE x.batch_id = t.batch_id AND x.pen_id = t.pen_id
                    AND x.task_date = d.d AND x.meal_no = t.meal_no);

-- 已完成任务补投喂记录：早/中/晚餐分别按 100%/97%/95% 执行，形成轻微的 UNDER 偏差
INSERT INTO t_feed_record (id, farm_id, batch_id, pen_id, task_id, active_task_id, feed_date, meal_no,
                           meal_name, feed_id, plan_qty, actual_qty, deviation_ratio, abnormal_type,
                           abnormal_threshold, executor_id, is_proxy, is_backfill, remark, stock_flow_id,
                           status, request_id, version, created_by, updated_by, created_at, updated_at)
SELECT 295000 + t.id, t.farm_id, t.batch_id, t.pen_id, t.id, t.id, t.task_date, t.meal_no, t.meal_name,
       t.feed_id, t.plan_qty,
       ROUND(t.plan_qty * r.ratio, 2), r.ratio - 1,
       CASE WHEN r.ratio < 0.98 THEN 'UNDER' ELSE 'NONE' END, 0.05,
       COALESCE(t.assignee_id, CASE WHEN t.farm_id = 1001 THEN 10003 ELSE 10002 END),
       0, 1, NULL, NULL, 'NORMAL', CONCAT('seed-feed-', t.id), 0,
       COALESCE(t.assignee_id, CASE WHEN t.farm_id = 1001 THEN 10003 ELSE 10002 END),
       COALESCE(t.assignee_id, CASE WHEN t.farm_id = 1001 THEN 10003 ELSE 10002 END),
       TIMESTAMP(t.task_date, t.feed_time), TIMESTAMP(t.task_date, t.feed_time) + INTERVAL 10 MINUTE
FROM t_feed_task t
         JOIN (SELECT 1 AS meal_no, 1.00 AS ratio
               UNION ALL SELECT 2, 0.97
               UNION ALL SELECT 3, 0.95) r ON r.meal_no = t.meal_no
WHERE t.status IN ('DONE', 'MANUAL_DONE')
  AND NOT EXISTS (SELECT 1 FROM t_feed_record x WHERE x.task_id = t.id);

UPDATE t_feed_task t
    JOIN t_feed_record r ON r.task_id = t.id AND r.status = 'NORMAL'
SET t.record_id = r.id
WHERE t.record_id IS NULL;

-- 登记新行到演示日期锚点（当前锚点 2026-10-25），后续重启随样例一起平移
INSERT IGNORE INTO t_demo_sample_row (table_name, row_id, group_code, anchor_date)
SELECT 't_feed_task', id, 'SAMPLE', '2026-10-25'
FROM t_feed_task
WHERE id BETWEEN 291000 AND 292999;
INSERT IGNORE INTO t_demo_sample_row (table_name, row_id, group_code, anchor_date)
SELECT 't_feed_record', id, 'SAMPLE', '2026-10-25'
FROM t_feed_record
WHERE id >= 295000;
