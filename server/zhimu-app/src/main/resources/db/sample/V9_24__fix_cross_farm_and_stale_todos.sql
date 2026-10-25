-- 修正演示数据：种禽舍 2303 / 肉鸡批次 3213 被误挂到东山猪场 1001，串入猪场存栏、批次、巡检与行情默认品类。
-- 只移动这两条演示对象及其引用行；用户自建数据不在这些 ID 上。
UPDATE t_pen SET farm_id = 1002 WHERE id = 2303 AND farm_id = 1001;
UPDATE t_batch SET farm_id = 1002 WHERE id = 3213 AND farm_id = 1001;

UPDATE t_todo SET farm_id = 1002 WHERE pen_id = 2303 AND farm_id = 1001;
UPDATE t_inspection SET farm_id = 1002 WHERE pen_id = 2303 AND farm_id = 1001;
UPDATE t_incubation SET farm_id = 1002 WHERE pen_id = 2303 OR batch_id = 3213;
UPDATE t_batch_pen SET farm_id = 1002 WHERE batch_id = 3213 OR pen_id = 2303;
UPDATE t_sale_advice SET farm_id = 1002 WHERE batch_id = 3213;
UPDATE t_abnormal_report SET farm_id = 1002 WHERE pen_id = 2303 OR batch_id = 3213;
UPDATE t_feed_task SET farm_id = 1002 WHERE pen_id = 2303 OR batch_id = 3213;
UPDATE t_disinfect_task SET farm_id = 1002 WHERE pen_id = 2303;
UPDATE t_disinfect_record SET farm_id = 1002 WHERE pen_id = 2303;
UPDATE t_disinfect_plan_pen SET farm_id = 1002 WHERE pen_id = 2303;

-- 种禽舍改由西岭鸡场所有者刘芳负责；V9_23 里错挂给东山李明的行一并纠正。
UPDATE t_member_pen
SET farm_id = 1002, member_id = 5005, user_id = 10002
WHERE pen_id = 2303;

-- 随待办 / 巡检一起移动的关联行。
UPDATE t_todo_remind r JOIN t_todo t ON t.id = r.todo_id
SET r.farm_id = t.farm_id
WHERE t.pen_id = 2303;
UPDATE t_todo_handle_log h JOIN t_todo t ON t.id = h.todo_id
SET h.farm_id = t.farm_id
WHERE t.pen_id = 2303;
UPDATE t_inspection_item_result r JOIN t_inspection i ON i.id = r.inspection_id
SET r.farm_id = i.farm_id
WHERE i.pen_id = 2303;

-- V9_05 为对齐 App Mock 硬编码了不存在的业务来源，日期平移后变成 5 条“幽灵待办”。
DELETE FROM t_message WHERE todo_id IN (90113, 90114, 90116, 90118, 90122, 90201);
DELETE FROM t_todo_remind WHERE todo_id IN (90113, 90114, 90116, 90118, 90122, 90201);
DELETE FROM t_todo_handle_log WHERE todo_id IN (90113, 90114, 90116, 90118, 90122, 90201);
DELETE FROM t_todo WHERE id IN (90113, 90114, 90116, 90118, 90122, 90201);
DELETE FROM t_demo_sample_row WHERE table_name = 't_todo'
  AND row_id IN (90113, 90114, 90116, 90118, 90122, 90201);

-- 悬挂的 farm_id=1004（养殖场已不存在）改为全平台通知，避免消息中心按场过滤后丢失。
UPDATE t_message SET farm_id = NULL WHERE farm_id = 1004;
