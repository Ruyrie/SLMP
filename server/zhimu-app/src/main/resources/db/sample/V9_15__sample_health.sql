-- 模块08 健康管理示例数据：免疫计划/接种记录、用药记录、消毒计划/任务/记录。
-- 关联东山猪场（1001）在养批次 3001/3002、栏舍 2001/2002/2003/2004。
-- 全部使用 INSERT IGNORE / NOT EXISTS，重复执行不会产生重复数据。

-- ===== 免疫计划：4 条入栏前 + 5 条入栏后 =====
INSERT IGNORE INTO t_immune_plan
(id, farm_id, target_type, batch_id, pen_id, source, pre_entry, template_id, template_version,
 template_item_id, catalog_id, drug_id, vaccine_name, immune_round, plan_mode, plan_age_day,
 original_plan_date, plan_date, due_time, dose, dose_unit, method_code, status, postponed,
 manual_done, missed_notified, version, created_by, created_at, updated_at)
VALUES
(9001,1001,'BATCH',3001,NULL,'TEMPLATE',1,21,1,2101,7001,NULL,'猪瘟活疫苗','首免','BY_DAY_AGE',25,
 '2026-08-09','2026-08-09','18:00:00',1.000,'DOSE_PER_HEAD','IM','PRE_ENTRY',0,0,0,0,0,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9002,1001,'BATCH',3001,NULL,'TEMPLATE',1,21,1,2102,7002,NULL,'猪伪狂犬病活疫苗','首免','BY_DAY_AGE',35,
 '2026-08-19','2026-08-19','18:00:00',1.000,'DOSE_PER_HEAD','IM','PRE_ENTRY',0,0,0,0,0,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9003,1001,'BATCH',3001,NULL,'TEMPLATE',1,21,1,2103,7001,NULL,'猪瘟活疫苗','二免','BY_DAY_AGE',60,
 '2026-09-13','2026-09-13','18:00:00',1.000,'DOSE_PER_HEAD','IM','PRE_ENTRY',0,0,0,0,0,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9004,1001,'BATCH',3001,NULL,'TEMPLATE',1,21,1,2104,7003,NULL,'口蹄疫灭活疫苗','首免','BY_DAY_AGE',60,
 '2026-09-13','2026-09-13','18:00:00',2.000,'DOSE_PER_HEAD','IM','PRE_ENTRY',0,0,0,0,0,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9005,1001,'BATCH',3001,NULL,'TEMPLATE',0,21,1,2105,7003,NULL,'口蹄疫灭活疫苗','二免','BY_DAY_AGE',85,
 '2026-10-08','2026-10-08','18:00:00',2.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,0,'2026-09-01 08:00:00','2026-10-01 08:00:00'),
(9006,1001,'BATCH',3001,NULL,'TEMPLATE',0,21,1,2106,7002,NULL,'猪伪狂犬病活疫苗','二免','BY_DAY_AGE',89,
 '2026-10-12','2026-10-12','18:00:00',1.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,0,'2026-09-01 08:00:00','2026-10-01 08:00:00'),
(9007,1001,'BATCH',3001,NULL,'TEMPLATE',0,21,1,2107,7001,NULL,'猪瘟活疫苗','加强免疫','BY_DAY_AGE',96,
 '2026-10-19','2026-10-19','18:00:00',1.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,0,'2026-09-01 08:00:00','2026-10-01 08:00:00'),
(9008,1001,'BATCH',3001,NULL,'TEMPLATE',0,21,1,2108,7003,NULL,'口蹄疫灭活疫苗','三免','BY_DAY_AGE',110,
 '2026-11-02','2026-11-02','18:00:00',2.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,0,'2026-09-01 08:00:00','2026-10-01 08:00:00'),
(9009,1001,'BATCH',3001,NULL,'TEMPLATE',0,21,1,2109,7002,NULL,'猪伪狂犬病活疫苗','加强免疫','BY_DAY_AGE',117,
 '2026-11-09','2026-11-09','18:00:00',1.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,0,'2026-09-01 08:00:00','2026-10-01 08:00:00');

-- 一条已完成的接种记录：批次 3001 猪瘟加强免疫（模板项 2107）
INSERT IGNORE INTO t_immune_record
(id, farm_id, batch_id, pen_id, plan_id, drug_id, vaccine_name, drug_lot_id, lot_no, lot_expire_date,
 dose, dose_unit, quantity, total_dose, method_code, vaccinate_date, executor_id, backfill,
 unit_price, cost_amount, stock_flow_id, client_request_id, deleted, version, created_by, created_at, updated_at)
SELECT 9601, p.farm_id, p.batch_id, 2003, p.id, 5001, '猪瘟活疫苗', 6001, '2025081', '2026-12-30',
       1.000, 'DOSE_PER_HEAD', 200, 200.000, 'IM', '2026-10-19', 10003, 0,
       2.3500, 470.00, NULL, 'seed-immune-2107', 0, 0, 10003, '2026-10-19 09:00:00', '2026-10-19 09:00:00'
FROM t_immune_plan p
WHERE p.farm_id = 1001 AND p.batch_id = 3001 AND p.template_item_id = 2107
  AND NOT EXISTS (SELECT 1 FROM t_immune_record r WHERE r.client_request_id = 'seed-immune-2107')
LIMIT 1;

UPDATE t_immune_plan p
JOIN t_immune_record r ON r.plan_id = p.id AND r.client_request_id = 'seed-immune-2107'
SET p.status = 'DONE', p.record_id = r.id, p.updated_at = '2026-10-19 09:00:00'
WHERE p.farm_id = 1001 AND p.batch_id = 3001 AND p.template_item_id = 2107;

-- ===== 用药记录：2 条（一条休药期进行中，一条已结束） =====
INSERT IGNORE INTO t_medication_record
(id, farm_id, group_no, target_type, pen_id, batch_id, catalog_id, drug_id, drug_name, drug_lot_id, lot_no,
 lot_expire_date, dose, dose_unit, recommended_dose, route_code, course_days, first_date, last_date,
 withdrawal_days, withdrawal_end_date, over_dose, over_dose_note, category_mismatch, usage_note, reason,
 estimated_qty, used_qty, used_unit, report_id, diagnosis_id, executor_id, unit_price, cost_amount,
 stock_flow_id, client_request_id, deleted, version, created_by, created_at, updated_at)
VALUES
(9101,1001,'MG2610200001','BATCH',2003,3001,7201,5201,'阿莫西林可溶性粉',6101,'2025091',
 '2027-08-31',0.400,'G_PER_L_WATER',0.300,'MIX_WATER',5,'2026-10-20','2026-10-24',
 14,'2026-11-07',0,NULL,0,NULL,'呼吸道感染，采食下降',
 1000.000,800.000,'G',NULL,NULL,10003,0.0320,25.60,
 NULL,'seed-med-9101',0,0,10003,'2026-10-20 08:30:00','2026-10-20 08:30:00'),
(9102,1001,'MG2610100001','BATCH',2004,3002,7202,5202,'氟苯尼考粉',6111,'2025062',
 '2027-06-30',0.200,'G_PER_L_WATER',0.200,'MIX_WATER',3,'2026-10-10','2026-10-12',
 20,'2026-11-01',0,NULL,0,NULL,'腹泻，采食量下降',
 800.000,500.000,'G',NULL,NULL,10004,0.1400,70.00,
 NULL,'seed-med-9102',0,0,10004,'2026-10-10 08:30:00','2026-10-10 08:30:00');

-- ===== 消毒计划：2 个周期计划，覆盖 4 个栏舍 =====
INSERT IGNORE INTO t_disinfect_plan
(id, farm_id, cycle_days, start_date, end_date, due_time, catalog_id, drug_id, disinfectant_name,
 concentration, method_code, status, generated_until, version, created_by, created_at, updated_at)
VALUES
(9201,1001,7,'2026-10-01','2026-12-31','17:00:00',7301,5301,'聚维酮碘溶液','1:500','SPRAY','ACTIVE','2026-10-24',0,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9202,1001,3,'2026-10-01','2026-12-31','17:00:00',7302,5302,'戊二醛溶液','1:200','SPRAY','ACTIVE','2026-10-24',0,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00');

INSERT IGNORE INTO t_disinfect_plan_pen (id, farm_id, plan_id, pen_id, created_by, created_at, updated_at)
VALUES
(9301,1001,9201,2001,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9302,1001,9201,2003,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9303,1001,9202,2002,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00'),
(9304,1001,9202,2004,10001,'2026-10-01 08:00:00','2026-10-01 08:00:00');

-- ===== 消毒任务：今天 + 近几天，含待执行/逾期/已完成 =====
INSERT IGNORE INTO t_disinfect_task
(id, farm_id, pen_id, plan_id, source, task_date, due_time, catalog_id, disinfectant_name, concentration,
 method_code, status, active_flag, record_id, manual_done, overdue_at, overdue_notified, cancelled_at,
 cancel_reason, version, created_by, created_at, updated_at)
VALUES
(9401,1001,2001,9201,'PLAN','2026-10-20','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','DONE',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-20 08:00:00','2026-10-20 09:10:00'),
(9402,1001,2001,9201,'PLAN','2026-10-22','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','DONE',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-22 08:00:00','2026-10-22 09:10:00'),
(9403,1001,2001,9201,'PLAN','2026-10-23','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','OVERDUE',1,NULL,0,'2026-10-23 17:00:00',1,NULL,NULL,0,0,'2026-10-23 08:00:00','2026-10-23 17:00:00'),
(9404,1001,2001,9201,'PLAN','2026-10-24','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','PENDING',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-24 08:00:00','2026-10-24 08:00:00'),
(9405,1001,2003,9201,'PLAN','2026-10-22','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','DONE',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-22 08:00:00','2026-10-22 09:10:00'),
(9406,1001,2003,9201,'PLAN','2026-10-24','17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','PENDING',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-24 08:00:00','2026-10-24 08:00:00'),
(9407,1001,2002,9202,'PLAN','2026-10-24','17:00:00',7302,'戊二醛溶液','1:200','SPRAY','PENDING',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-24 08:00:00','2026-10-24 08:00:00'),
(9408,1001,2004,9202,'PLAN','2026-10-23','17:00:00',7302,'戊二醛溶液','1:200','SPRAY','OVERDUE',1,NULL,0,'2026-10-23 17:00:00',1,NULL,NULL,0,0,'2026-10-23 08:00:00','2026-10-23 17:00:00'),
(9409,1001,2004,9202,'PLAN','2026-10-24','17:00:00',7302,'戊二醛溶液','1:200','SPRAY','PENDING',1,NULL,0,NULL,0,NULL,NULL,0,0,'2026-10-24 08:00:00','2026-10-24 08:00:00');

-- ===== 消毒记录：3 条计划任务执行记录 =====
INSERT IGNORE INTO t_disinfect_record
(id, farm_id, pen_id, record_type, task_id, plan_concentration, actual_time, executor_id, drug_id,
 disinfectant_name, drug_lot_id, lot_no, usage_qty, usage_unit, concentration, concentration_confirmed,
 method_code, backfill, in_batch_cost, in_exec_rate, unit_price, cost_amount, stock_flow_id,
 client_request_id, deleted, version, created_by, created_at, updated_at)
VALUES
(9501,1001,2001,'PLAN_TASK',9401,'1:500','2026-10-20 09:10:00',10003,5301,'聚维酮碘溶液',6301,'2025033',
 0.500,'L','1:500',1,'SPRAY',0,1,1,9.0000,4.50,NULL,'seed-disinfect-9501',0,0,10003,'2026-10-20 09:10:00','2026-10-20 09:10:00'),
(9502,1001,2001,'PLAN_TASK',9402,'1:500','2026-10-22 09:10:00',10003,5301,'聚维酮碘溶液',6301,'2025033',
 0.400,'L','1:500',1,'SPRAY',0,1,1,9.0000,3.60,NULL,'seed-disinfect-9502',0,0,10003,'2026-10-22 09:10:00','2026-10-22 09:10:00'),
(9503,1001,2003,'PLAN_TASK',9405,'1:500','2026-10-22 09:20:00',10004,5301,'聚维酮碘溶液',6301,'2025033',
 0.300,'L','1:500',1,'SPRAY',0,1,1,9.0000,2.70,NULL,'seed-disinfect-9503',0,0,10004,'2026-10-22 09:20:00','2026-10-22 09:20:00');

UPDATE t_disinfect_task t
JOIN t_disinfect_record r ON r.task_id = t.id
SET t.status = 'DONE', t.record_id = r.id, t.updated_at = r.actual_time
WHERE t.id IN (9401,9402,9405);
