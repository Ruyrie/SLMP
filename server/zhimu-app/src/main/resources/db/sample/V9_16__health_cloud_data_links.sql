-- 健康云端联调补齐。V9_15 已可能手工导入，不改旧脚本、不重置用户操作。
-- 日期取迁移执行当天的北京时间；后续消毒任务由 DisinfectTaskGenerator 持续生成。
SET @health_today = DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR);
SET @health_now = UTC_TIMESTAMP() + INTERVAL 8 HOUR;

-- 旧库存流水 9525 已经记账，但原用药详情 5101 缺失。只补来源，不重复扣库存。
INSERT INTO t_medication_record
(id,farm_id,group_no,target_type,pen_id,batch_id,catalog_id,drug_id,drug_name,drug_lot_id,lot_no,
 lot_expire_date,dose,dose_unit,recommended_dose,route_code,course_days,first_date,last_date,
 withdrawal_days,withdrawal_end_date,over_dose,category_mismatch,reason,estimated_qty,used_qty,used_unit,
 executor_id,unit_price,cost_amount,stock_flow_id,client_request_id,deleted,version,created_by,created_at,updated_at)
SELECT 5101,f.farm_id,'MG2610095101','BATCH',f.pen_id,f.batch_id,l.catalog_id,f.drug_id,d.name,l.id,l.lot_no,
 l.expire_date,0.200,'G_PER_L_WATER',0.200,'MIX_WATER',1,f.biz_date,f.biz_date,
 20,DATE_ADD(f.biz_date,INTERVAL 20 DAY),0,0,'呼吸道感染，历史用药记录补齐',f.qty,f.qty,d.unit,
 f.operator_id,f.unit_price,f.amount,f.id,'sample-health-legacy-5101',0,0,f.operator_id,f.occur_at,f.occur_at
FROM t_stock_flow f JOIN t_drug_lot l ON l.id=f.drug_lot_id JOIN t_drug d ON d.id=f.drug_id
WHERE f.id=9525 AND f.biz_type='MEDICATION_RECORD' AND f.biz_id=5101 AND f.status='NORMAL'
 AND NOT EXISTS (SELECT 1 FROM t_medication_record r WHERE r.id=5101);

-- 当前日期的手工免疫项目：待登记、已接种、下周计划均有对应详情。
INSERT IGNORE INTO t_immune_plan
(id,farm_id,target_type,batch_id,source,pre_entry,catalog_id,drug_id,vaccine_name,immune_round,plan_mode,
 original_plan_date,plan_date,due_time,dose,dose_unit,method_code,status,postponed,manual_done,missed_notified,
 version,created_by,created_at,updated_at)
VALUES
(160101,1001,'BATCH',3001,'MANUAL',0,7001,5001,'猪瘟活疫苗','加强免疫','BY_DATE',
 @health_today,@health_today,'18:00:00',1.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,10001,@health_now,@health_now),
(160102,1001,'BATCH',3002,'MANUAL',0,7001,5001,'猪瘟活疫苗','加强免疫','BY_DATE',
 DATE_SUB(@health_today,INTERVAL 1 DAY),DATE_SUB(@health_today,INTERVAL 1 DAY),'18:00:00',
 1.000,'DOSE_PER_HEAD','IM','DONE',0,0,0,0,10001,@health_now,@health_now),
(160103,1001,'BATCH',3002,'MANUAL',0,7002,5002,'猪伪狂犬病活疫苗','加强免疫','BY_DATE',
 DATE_ADD(@health_today,INTERVAL 7 DAY),DATE_ADD(@health_today,INTERVAL 7 DAY),'18:00:00',
 1.000,'DOSE_PER_HEAD','IM','PENDING',0,0,0,0,10001,@health_now,@health_now);

INSERT IGNORE INTO t_immune_record
(id,farm_id,batch_id,pen_id,plan_id,drug_id,vaccine_name,drug_lot_id,lot_no,lot_expire_date,
 dose,dose_unit,quantity,total_dose,method_code,vaccinate_date,executor_id,backfill,
 client_request_id,deleted,version,created_by,created_at,updated_at)
SELECT 160201,1001,3002,2004,160102,5001,'猪瘟活疫苗',l.id,l.lot_no,l.expire_date,
 1.000,'DOSE_PER_HEAD',100,100.000,'IM',DATE_SUB(@health_today,INTERVAL 1 DAY),10004,0,
 'sample-health-immune-160201',0,0,10004,TIMESTAMP(DATE_SUB(@health_today,INTERVAL 1 DAY),'09:00:00'),@health_now
FROM t_drug_lot l WHERE l.id=6002;
UPDATE t_immune_plan p JOIN t_immune_record r ON r.plan_id=p.id
SET p.record_id=r.id,p.status='DONE'
WHERE p.id=160102 AND r.client_request_id='sample-health-immune-160201' AND p.version=0;

-- 已结束疗程供用药记录列表查看；当前批次的最终休药状态仍按所有有效记录的最晚日期计算。
INSERT IGNORE INTO t_medication_record
(id,farm_id,group_no,target_type,pen_id,batch_id,catalog_id,drug_id,drug_name,drug_lot_id,lot_no,
 lot_expire_date,dose,dose_unit,recommended_dose,route_code,course_days,first_date,last_date,
 withdrawal_days,withdrawal_end_date,over_dose,category_mismatch,reason,estimated_qty,used_qty,used_unit,
 executor_id,client_request_id,deleted,version,created_by,created_at,updated_at)
SELECT 160301,1001,'MG-DEMO-160301','BATCH',2004,3002,7202,5202,'氟苯尼考粉',l.id,l.lot_no,l.expire_date,
 0.200,'G_PER_L_WATER',0.200,'MIX_WATER',3,DATE_SUB(@health_today,INTERVAL 24 DAY),DATE_SUB(@health_today,INTERVAL 22 DAY),
 20,DATE_SUB(@health_today,INTERVAL 2 DAY),0,0,'早期腹泻，疗程和休药期已结束',100.000,100.000,'G',
 10004,'sample-health-medication-160301',0,0,10004,
 TIMESTAMP(DATE_SUB(@health_today,INTERVAL 24 DAY),'08:30:00'),@health_now
FROM t_drug_lot l WHERE l.id=6111;

-- 每日消毒演示计划覆盖员工实际负责的 3、4 号育肥舍，后续每天均可继续登记。
INSERT IGNORE INTO t_disinfect_plan
(id,farm_id,cycle_days,start_date,end_date,due_time,catalog_id,drug_id,disinfectant_name,concentration,
 method_code,status,generated_until,version,created_by,created_at,updated_at)
VALUES (160401,1001,1,DATE_SUB(@health_today,INTERVAL 1 DAY),DATE_ADD(@health_today,INTERVAL 365 DAY),
 '17:00:00',7301,5301,'聚维酮碘溶液','1:500','SPRAY','ACTIVE',@health_today,0,10001,@health_now,@health_now);
INSERT IGNORE INTO t_disinfect_plan_pen (id,farm_id,plan_id,pen_id,created_by,created_at,updated_at)
VALUES (160401,1001,160401,2003,10001,@health_now,@health_now),(160402,1001,160401,2004,10001,@health_now,@health_now);
INSERT IGNORE INTO t_disinfect_task
(id,farm_id,pen_id,plan_id,source,task_date,due_time,catalog_id,disinfectant_name,concentration,method_code,
 status,active_flag,manual_done,overdue_notified,version,created_by,created_at,updated_at)
VALUES
(160501,1001,2003,160401,'PLAN',DATE_SUB(@health_today,INTERVAL 1 DAY),'17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY',
 'DONE',1,0,0,0,0,@health_now,@health_now),
(160502,1001,2003,160401,'PLAN',@health_today,'17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY',
 'PENDING',1,0,0,0,0,@health_now,@health_now),
(160503,1001,2004,160401,'PLAN',@health_today,'17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY',
 'PENDING',1,0,0,0,0,@health_now,@health_now);
INSERT IGNORE INTO t_disinfect_record
(id,farm_id,pen_id,record_type,task_id,plan_concentration,actual_time,executor_id,drug_id,disinfectant_name,
 drug_lot_id,lot_no,usage_qty,usage_unit,concentration,concentration_confirmed,method_code,backfill,
 in_batch_cost,in_exec_rate,client_request_id,deleted,version,created_by,created_at,updated_at)
SELECT 160601,1001,2003,'PLAN_TASK',160501,'1:500',TIMESTAMP(DATE_SUB(@health_today,INTERVAL 1 DAY),'09:00:00'),
 10003,5301,'聚维酮碘溶液',l.id,l.lot_no,0.200,'L','1:500',1,'SPRAY',0,1,1,
 'sample-health-disinfect-160601',0,0,10003,TIMESTAMP(DATE_SUB(@health_today,INTERVAL 1 DAY),'09:00:00'),@health_now
FROM t_drug_lot l WHERE l.id=6301;
UPDATE t_disinfect_task t JOIN t_disinfect_record r ON r.task_id=t.id
SET t.record_id=r.id,t.status='DONE' WHERE t.id=160501 AND r.client_request_id='sample-health-disinfect-160601' AND t.version=0;

-- 仅捕获尚未记账且未被修改的样例。账面批号均价为准，不能沿用旧样例硬编码单价。
-- 临时表确保重复执行不会再次扣减；既有 9525 不在补扣范围。
CREATE TEMPORARY TABLE tmp_health_consumption AS
SELECT CASE r.id WHEN 9601 THEN 160001 ELSE 160007 END AS flow_id,r.farm_id,'IMMUNE_RECORD' AS biz_type,r.id AS biz_id,
 r.drug_id,r.drug_lot_id,r.total_dose AS qty,'VACCINE_USE' AS flow_type,r.batch_id,r.pen_id,r.executor_id AS operator_id,
 r.vaccine_name AS related_desc,r.created_at AS occur_at,r.vaccinate_date AS biz_date
FROM t_immune_record r WHERE r.id IN (9601,160201) AND r.stock_flow_id IS NULL AND r.version=0 AND r.deleted=0
UNION ALL
SELECT CASE r.id WHEN 9101 THEN 160002 WHEN 9102 THEN 160003 ELSE 160008 END,r.farm_id,'MEDICATION_RECORD',r.id,
 r.drug_id,r.drug_lot_id,r.used_qty,'MEDICATION_USE',r.batch_id,r.pen_id,r.executor_id,r.drug_name,r.created_at,r.first_date
FROM t_medication_record r WHERE r.id IN (9101,9102,160301) AND r.stock_flow_id IS NULL AND r.version=0 AND r.deleted=0
UNION ALL
SELECT CASE r.id WHEN 9501 THEN 160004 WHEN 9502 THEN 160005 WHEN 9503 THEN 160006 ELSE 160009 END,
 r.farm_id,'DISINFECT_RECORD',r.id,r.drug_id,r.drug_lot_id,r.usage_qty,'DISINFECT_USE',NULL,r.pen_id,r.executor_id,
 r.disinfectant_name,r.actual_time,DATE(r.actual_time)
FROM t_disinfect_record r WHERE r.id IN (9501,9502,9503,160601) AND r.stock_flow_id IS NULL AND r.version=0 AND r.deleted=0;
DELETE c FROM tmp_health_consumption c JOIN t_stock_flow f
 ON f.farm_id=c.farm_id AND f.biz_type=c.biz_type AND f.biz_id=c.biz_id AND f.status='NORMAL';

-- 扣减前保存每条流水的结存快照，按业务发生日期和来源 ID 排序。
CREATE TEMPORARY TABLE tmp_health_priced AS
SELECT c.*,l.avg_price AS unit_price,ROUND(c.qty*l.avg_price,2) AS amount,
 l.stock_qty-SUM(c.qty) OVER(PARTITION BY c.drug_lot_id ORDER BY c.biz_date,c.biz_type,c.biz_id) AS qty_after,
 l.stock_amount-SUM(ROUND(c.qty*l.avg_price,2)) OVER(PARTITION BY c.drug_lot_id ORDER BY c.biz_date,c.biz_type,c.biz_id) AS amount_after
FROM tmp_health_consumption c JOIN t_drug_lot l ON l.id=c.drug_lot_id AND l.farm_id=c.farm_id;
UPDATE t_drug_lot l JOIN
 (SELECT drug_lot_id,SUM(qty) AS qty,SUM(amount) AS amount FROM tmp_health_priced GROUP BY drug_lot_id) c ON c.drug_lot_id=l.id
SET l.stock_qty=l.stock_qty-c.qty,l.stock_amount=l.stock_amount-c.amount,l.version=l.version+1,l.updated_at=@health_now;
INSERT INTO t_stock_flow
(id,farm_id,material_type,drug_id,drug_lot_id,flow_type,direction,qty,unit_price,amount,stock_qty_after,stock_amount_after,
 biz_type,biz_id,batch_id,pen_id,operator_id,related_desc,occur_at,biz_date,is_batch_cost,is_feed_usage,origin_flow_id,status,created_at,updated_at)
SELECT flow_id,farm_id,'DRUG',drug_id,drug_lot_id,flow_type,'OUT',qty,unit_price,amount,qty_after,amount_after,
 biz_type,biz_id,batch_id,pen_id,operator_id,related_desc,occur_at,biz_date,1,0,flow_id,'NORMAL',@health_now,@health_now
FROM tmp_health_priced;
UPDATE t_immune_record r JOIN tmp_health_priced c ON c.biz_type='IMMUNE_RECORD' AND c.biz_id=r.id
SET r.stock_flow_id=c.flow_id,r.unit_price=c.unit_price,r.cost_amount=c.amount;
UPDATE t_medication_record r JOIN tmp_health_priced c ON c.biz_type='MEDICATION_RECORD' AND c.biz_id=r.id
SET r.stock_flow_id=c.flow_id,r.unit_price=c.unit_price,r.cost_amount=c.amount;
UPDATE t_disinfect_record r JOIN tmp_health_priced c ON c.biz_type='DISINFECT_RECORD' AND c.biz_id=r.id
SET r.stock_flow_id=c.flow_id,r.unit_price=c.unit_price,r.cost_amount=c.amount;

-- 接种/用药全部计入目标批次；消毒按栏内实际在养批次数量分摊，尾差归最后一个批次。
INSERT INTO t_batch_material_cost
(farm_id,batch_id,pen_id,flow_id,source_type,source_id,cost_type,qty,head_count,ratio,amount,biz_date,status,created_at,updated_at)
SELECT c.farm_id,c.batch_id,c.pen_id,c.flow_id,c.biz_type,c.biz_id,'DRUG',c.qty,b.current_count,1.0000,c.amount,c.biz_date,
 'NORMAL',@health_now,@health_now
FROM tmp_health_priced c JOIN t_batch b ON b.id=c.batch_id WHERE c.batch_id IS NOT NULL;
INSERT INTO t_batch_material_cost
(farm_id,batch_id,pen_id,flow_id,source_type,source_id,cost_type,qty,head_count,ratio,amount,biz_date,status,created_at,updated_at)
SELECT farm_id,batch_id,pen_id,flow_id,biz_type,biz_id,'DRUG',
 CASE WHEN rn=cnt THEN total_qty-(SUM(share_qty) OVER(PARTITION BY flow_id)-share_qty) ELSE share_qty END,
 stock_count,ratio,
 CASE WHEN rn=cnt THEN total_amount-(SUM(share_amount) OVER(PARTITION BY flow_id)-share_amount) ELSE share_amount END,
 biz_date,'NORMAL',@health_now,@health_now
FROM (SELECT c.farm_id,bp.batch_id,c.pen_id,c.flow_id,c.biz_type,c.biz_id,c.qty AS total_qty,c.amount AS total_amount,c.biz_date,
 bp.stock_count,ROUND(bp.stock_count/SUM(bp.stock_count) OVER(PARTITION BY c.flow_id),4) AS ratio,
 ROUND(c.qty*bp.stock_count/SUM(bp.stock_count) OVER(PARTITION BY c.flow_id),3) AS share_qty,
 ROUND(c.amount*bp.stock_count/SUM(bp.stock_count) OVER(PARTITION BY c.flow_id),2) AS share_amount,
 ROW_NUMBER() OVER(PARTITION BY c.flow_id ORDER BY bp.batch_id) AS rn,COUNT(*) OVER(PARTITION BY c.flow_id) AS cnt
 FROM tmp_health_priced c JOIN t_batch_pen bp ON bp.farm_id=c.farm_id AND bp.pen_id=c.pen_id AND bp.stock_count>0
 JOIN t_batch b ON b.id=bp.batch_id AND b.status='ACTIVE' AND b.deleted=0 WHERE c.batch_id IS NULL) allocated;

-- 汇总与详情使用所有有效用药记录的最晚休药日，不只看新加的一条记录。
INSERT INTO t_batch_withdrawal
(farm_id,batch_id,release_date,decisive_record_id,decisive_drug_name,last_med_date,withdrawal_days,cycle_no,recalculated_at,created_at,updated_at)
SELECT farm_id,batch_id,withdrawal_end_date,id,drug_name,last_date,withdrawal_days,
 IF(withdrawal_end_date>@health_today,1,0),@health_now,@health_now,@health_now
FROM (SELECT r.*,ROW_NUMBER() OVER(PARTITION BY r.batch_id ORDER BY r.withdrawal_end_date DESC,r.id DESC) AS rn
 FROM t_medication_record r WHERE r.farm_id=1001 AND r.batch_id IN(3001,3002) AND r.deleted=0) records
WHERE rn=1
ON DUPLICATE KEY UPDATE release_date=VALUES(release_date),decisive_record_id=VALUES(decisive_record_id),
 decisive_drug_name=VALUES(decisive_drug_name),last_med_date=VALUES(last_med_date),withdrawal_days=VALUES(withdrawal_days),
 recalculated_at=VALUES(recalculated_at),updated_at=VALUES(updated_at);

DROP TEMPORARY TABLE tmp_health_priced;
DROP TEMPORARY TABLE tmp_health_consumption;
