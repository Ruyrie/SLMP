-- 西岭鸡场的最小完整业务链。只补演示样例，ID 显式保留在 210000–219999；不复制东山全量账目。
-- 日期统一以 2026-10-19 为锚点，末尾登记到 V9_20 的样例日期清单，启动时按真实日期平移。
SET @b_day = DATE('2026-10-19');
SET @b_at = TIMESTAMP('2026-10-19 09:00:00');

-- 旧 Mock 待办存在不存在的鸡舍 2101、任务 704xx/705xx。仅关闭这些已知样例的孤立来源。
UPDATE t_todo t LEFT JOIN t_feed_task f ON f.id=t.biz_id AND f.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at
WHERE t.id IN(90401,90403,90404,90406,90504,90505) AND t.biz_type='FEED_TASK' AND f.id IS NULL;
UPDATE t_todo t LEFT JOIN t_disinfect_task d ON d.id=t.biz_id AND d.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at
WHERE t.id IN(90112,90113,90114,90407,90502) AND t.biz_type='DISINFECT_TASK' AND d.id IS NULL;
UPDATE t_todo t LEFT JOIN t_immune_plan p ON p.id=t.biz_id AND p.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at
WHERE t.id IN(90117,90121) AND t.biz_type='IMMUNE_PLAN' AND p.id IS NULL;
UPDATE t_todo t LEFT JOIN t_inspection i ON i.id=t.biz_id AND i.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at
WHERE t.id IN(90309,90116,90118,90122,90402,90405) AND t.biz_type='INSPECTION' AND i.id IS NULL;
UPDATE t_todo t LEFT JOIN t_mating_record m ON m.id=t.biz_id AND m.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at WHERE t.id=90106 AND t.biz_type='MATING' AND m.id IS NULL;
UPDATE t_todo t JOIN t_batch_growth g ON g.batch_id=t.batch_id AND g.farm_id=t.farm_id
SET t.biz_id=g.id,t.updated_at=@b_at WHERE t.id=90503 AND t.biz_id=7928 AND t.biz_type='WEIGH_CYCLE';
UPDATE t_todo t LEFT JOIN t_sale_order s ON s.id=t.biz_id AND s.farm_id=t.farm_id
SET t.deleted=1,t.updated_at=@b_at WHERE t.id=90103 AND t.biz_type='SALE_ORDER' AND s.id IS NULL;
-- 实际投喂记录为 7106/7119，不能继续指向原 Mock 88107/88119。
UPDATE t_todo t JOIN t_feed_task f ON f.id=t.biz_id AND f.farm_id=t.farm_id
JOIN t_feed_record r ON r.id=f.record_id AND r.task_id=f.id AND r.status='NORMAL'
SET t.batch_id=f.batch_id,t.pen_id=f.pen_id,t.complete_record_id=r.id,t.updated_at=@b_at
WHERE t.id IN(90107,90119) AND t.status='DONE' AND t.complete_record_id IN(88107,88119);
UPDATE t_feed_task f JOIN t_todo t ON t.biz_type='FEED_TASK' AND t.biz_id=f.id AND t.farm_id=f.farm_id AND t.deleted=0
SET f.todo_id=t.id WHERE f.id IN(70101,70107,70108,70109,70110,70111,70115,70119,70120);
-- 孵化记录的照蛋人必须是该场成员；6005/6006 的转批数量以实际健康雏数量为准。
UPDATE t_incubation SET candle_by=10002,updated_by=IF(updated_by=10003,10002,updated_by)
WHERE farm_id=1002 AND id IN(6001,6002,6004,6005,6006) AND candle_by=10003;
UPDATE t_batch b JOIN t_incubation i ON i.batch_id=b.id AND i.farm_id=b.farm_id
SET b.entry_count=i.healthy_count,b.sold_count=i.healthy_count
WHERE b.farm_id=1002 AND b.id IN(3273,3274) AND b.status='ENDED' AND b.current_count=0
 AND b.client_request_id IN('sample-breed-hatch-6005','sample-breed-hatch-6006');

-- 1. 饲料、两批次方案、餐次、已登记与未登记任务、称重。
INSERT IGNORE INTO t_feed
(id,farm_id,name,feed_type_code,spec,unit,pack_unit,pack_size,category_id,min_stock,warn_days,
 stock_qty,stock_amount,avg_price,usage_7d_qty,usage_days,daily_use_qty,remaining_basis,remaining_days,stat_date,
 is_low_stock,is_unit_locked,version,edit_version,deleted,created_by,updated_by,created_at,updated_at)
VALUES(210001,1002,'肉鸡全价料','COMPLETE','40 千克/袋','KG','袋',40,201,2000,3,
 9070,32652,3.6000,465,1,1860,'PLAN',4,@b_day,0,1,0,0,0,10002,10002,'2026-10-15 09:00:00',@b_at);
INSERT IGNORE INTO t_feed_plan
(id,farm_id,batch_id,breed_id,abnormal_threshold,version,created_by,updated_by,created_at,updated_at)
VALUES(210011,1002,3201,2101,0.1000,0,10002,10002,'2026-10-15 09:00:00',@b_at),
      (210012,1002,3202,2101,0.1000,0,10002,10002,'2026-10-15 09:00:00',@b_at);
INSERT IGNORE INTO t_feed_plan_stage
(id,farm_id,plan_id,batch_id,sort_no,stage_name,day_age_start,feed_id,daily_qty_per_head,meals_per_day,deleted,
 created_by,updated_by,created_at,updated_at)
VALUES(210021,1002,210011,3201,1,'生长期',1,210001,0.100,2,0,10002,10002,'2026-10-15 09:00:00',@b_at),
      (210022,1002,210012,3202,1,'生长期',1,210001,0.100,2,0,10002,10002,'2026-10-15 09:00:00',@b_at);
INSERT IGNORE INTO t_feed_plan_meal(id,farm_id,plan_id,stage_id,meal_no,meal_code,meal_name,feed_time,created_at,updated_at)
VALUES(210031,1002,210011,210021,1,'BREAKFAST','早餐','07:00:00',@b_at,@b_at),
 (210032,1002,210011,210021,2,'DINNER','晚餐','17:00:00',@b_at,@b_at),
 (210033,1002,210012,210022,1,'BREAKFAST','早餐','07:00:00',@b_at,@b_at),
 (210034,1002,210012,210022,2,'DINNER','晚餐','17:00:00',@b_at,@b_at);
INSERT IGNORE INTO t_feed_task
(id,farm_id,batch_id,pen_id,plan_id,stage_id,task_date,meal_no,meal_code,meal_name,feed_time,feed_at,deadline_at,
 backfill_end_at,feed_id,head_count,daily_qty_per_head,meals_per_day,plan_qty,status,record_id,overdue_notified,version,created_at,updated_at)
VALUES
(210041,1002,3201,2202,210011,210021,'2026-10-18',1,'BREAKFAST','早餐','07:00:00','2026-10-18 07:00:00',
 '2026-10-18 09:00:00','2026-10-20 00:00:00',210001,9300,0.100,2,465,'DONE',210051,0,0,'2026-10-18 00:05:00','2026-10-18 07:20:00'),
(210042,1002,3201,2202,210011,210021,@b_day,1,'BREAKFAST','早餐','07:00:00','2026-10-19 07:00:00',
 '2026-10-19 09:00:00','2026-10-21 00:00:00',210001,9300,0.100,2,465,'DONE',210052,0,0,'2026-10-19 00:05:00','2026-10-19 07:20:00'),
(210043,1002,3201,2202,210011,210021,@b_day,2,'DINNER','晚餐','17:00:00','2026-10-19 17:00:00',
 '2026-10-19 19:00:00','2026-10-21 00:00:00',210001,9300,0.100,2,465,'PENDING',NULL,0,0,'2026-10-19 00:05:00',@b_at),
(210044,1002,3202,2201,210012,210022,@b_day,1,'BREAKFAST','早餐','07:00:00','2026-10-19 07:00:00',
 '2026-10-19 09:00:00','2026-10-21 00:00:00',210001,9300,0.100,2,465,'PENDING',NULL,0,0,'2026-10-19 00:05:00',@b_at),
(210045,1002,3202,2201,210012,210022,@b_day,2,'DINNER','晚餐','17:00:00','2026-10-19 17:00:00',
 '2026-10-19 19:00:00','2026-10-21 00:00:00',210001,9300,0.100,2,465,'PENDING',NULL,0,0,'2026-10-19 00:05:00',@b_at);
INSERT IGNORE INTO t_feed_record
(id,farm_id,batch_id,pen_id,task_id,active_task_id,feed_date,meal_no,meal_name,feed_id,plan_qty,actual_qty,
 deviation_ratio,abnormal_type,abnormal_threshold,executor_id,is_proxy,is_backfill,stock_flow_id,status,request_id,
 version,created_by,updated_by,created_at,updated_at)
VALUES(210051,1002,3201,2202,210041,210041,'2026-10-18',1,'早餐',210001,465,465,0,'NONE',0.1,10002,0,0,210072,
 'NORMAL','sample-cross-farm-feed-210051',0,10002,10002,'2026-10-18 07:20:00','2026-10-18 07:20:00'),
 (210052,1002,3201,2202,210042,210042,@b_day,1,'早餐',210001,465,465,0,'NONE',0.1,10002,0,0,210073,
 'NORMAL','sample-cross-farm-feed-210052',0,10002,10002,'2026-10-19 07:20:00','2026-10-19 07:20:00');
INSERT IGNORE INTO t_weigh_record
(id,farm_id,batch_id,weigh_date,day_age,stock_count,mode,sample_count,total_weight,avg_weight,suggested_sample,
 sample_low,growth_result,gain_base_type,gain_base_date,gain_base_weight,daily_gain,request_id,version,created_by,updated_by,created_at,updated_at)
VALUES(210091,1002,3201,'2026-10-17',17,9300,'SAMPLE',1500,1350,0.90,1395,0,'NO_STD','INTAKE',
 '2026-10-01',0.04,0.05,'sample-cross-farm-weigh-210091',0,10002,10002,'2026-10-17 09:00:00','2026-10-17 09:00:00');
INSERT IGNORE INTO t_batch_growth
(id,farm_id,batch_id,weigh_cycle_per_month,last_weigh_id,last_weigh_date,last_avg_weight,last_daily_gain,
 growth_status,growth_status_at,next_weigh_date,version,created_at,updated_at)
VALUES(210092,1002,3201,2,210091,'2026-10-17',0.90,0.05,'NO_STD','2026-10-17 09:00:00','2026-11-01',0,@b_at,@b_at),
 (210093,1002,3202,2,NULL,NULL,NULL,NULL,'NONE',NULL,'2026-10-20',0,@b_at,@b_at);

-- 2. 禽用演示疫苗、兽药、消毒剂。目录与批号同场关联，数量/金额与下方出入库相等。
INSERT IGNORE INTO t_drug_catalog
(id,drug_category,name,dosage_form,spec,measure_unit,pack_unit,pack_size,vaccine_dose_unit,default_vaccinate_method_code,
 status,banned,last_change_note,created_by,updated_by,version,created_at,updated_at)
VALUES(210101,'VACCINE','禽用活疫苗（演示）','活疫苗','1000 头份/瓶','DOSE','瓶',1000,'DOSE_PER_HEAD','WATER',
 'ENABLED',0,'课程设计演示目录',0,0,0,@b_at,@b_at);
INSERT IGNORE INTO t_drug_catalog_usage
(id,catalog_id,species,applicable,admin_route,recommended_dose,dose_unit,course_days,withdrawal_days,created_by,created_at,updated_at)
VALUES(210102,210101,'POULTRY',1,'WATER',1,'DOSE_PER_HEAD',1,0,0,@b_at,@b_at);
INSERT IGNORE INTO t_drug
(id,farm_id,category,catalog_id,name,spec,unit,pack_unit,pack_size,min_stock,min_stock_unit,is_low_stock,is_unit_locked,
 version,deleted,created_by,updated_by,created_at,updated_at)
VALUES(210111,1002,'VACCINE',210101,'禽用活疫苗（演示）','1000 头份/瓶','DOSE','瓶',1000,1000,'MEASURE',0,1,0,0,10002,10002,@b_at,@b_at),
 (210112,1002,'VET_DRUG',7201,'阿莫西林可溶性粉','10% · 500 克/袋','G','袋',500,500,'MEASURE',0,1,0,0,10002,10002,@b_at,@b_at),
 (210113,1002,'DISINFECTANT',7301,'聚维酮碘溶液','5 升/桶','L','桶',5,5,'MEASURE',0,1,0,0,10002,10002,@b_at,@b_at);
INSERT IGNORE INTO t_drug_lot
(id,farm_id,drug_id,catalog_id,category,lot_no,expire_date,first_inbound_date,inbound_count,stock_qty,stock_amount,avg_price,version,created_at,updated_at)
VALUES(210121,1002,210111,210101,'VACCINE','DEMO-V2610','2027-10-19','2026-10-15',1,19500,1170,0.0600,0,@b_at,@b_at),
 (210122,1002,210112,7201,'VET_DRUG','DEMO-M2610','2027-10-19','2026-10-15',1,4500,144,0.0320,0,@b_at,@b_at),
 (210123,1002,210113,7301,'DISINFECTANT','DEMO-D2610','2027-10-19','2026-10-15',1,19.5,175.50,9,0,@b_at,@b_at);
INSERT IGNORE INTO t_immune_plan
(id,farm_id,target_type,batch_id,source,pre_entry,catalog_id,drug_id,vaccine_name,immune_round,plan_mode,
 original_plan_date,plan_date,due_time,dose,dose_unit,method_code,status,postponed,manual_done,missed_notified,record_id,version,created_by,created_at,updated_at)
VALUES(210131,1002,'BATCH',3201,'MANUAL',0,210101,210111,'禽用活疫苗（演示）','首免','BY_DATE',@b_day,@b_day,
 '18:00:00',1,'DOSE_PER_HEAD','WATER','PENDING',0,0,0,NULL,0,10002,@b_at,@b_at),
 (210132,1002,'BATCH',3202,'MANUAL',0,210101,210111,'禽用活疫苗（演示）','首免','BY_DATE','2026-10-18','2026-10-18',
 '18:00:00',1,'DOSE_PER_HEAD','WATER','DONE',0,0,0,210141,0,10002,@b_at,@b_at);
INSERT IGNORE INTO t_immune_record
(id,farm_id,batch_id,pen_id,plan_id,drug_id,vaccine_name,drug_lot_id,lot_no,lot_expire_date,dose,dose_unit,quantity,total_dose,
 method_code,vaccinate_date,executor_id,backfill,unit_price,cost_amount,stock_flow_id,client_request_id,deleted,version,created_by,created_at,updated_at)
VALUES(210141,1002,3202,2201,210132,210111,'禽用活疫苗（演示）',210121,'DEMO-V2610','2027-10-19',1,'DOSE_PER_HEAD',
 500,500,'WATER','2026-10-18',10002,0,0.0600,30,210164,'sample-cross-farm-immune-210141',0,0,10002,'2026-10-18 09:30:00',@b_at);
INSERT IGNORE INTO t_medication_record
(id,farm_id,group_no,target_type,pen_id,batch_id,catalog_id,drug_id,drug_name,drug_lot_id,lot_no,lot_expire_date,dose,
 dose_unit,recommended_dose,route_code,course_days,first_date,last_date,withdrawal_days,withdrawal_end_date,over_dose,
 category_mismatch,reason,estimated_qty,used_qty,used_unit,executor_id,unit_price,cost_amount,stock_flow_id,
 client_request_id,deleted,version,created_by,created_at,updated_at)
VALUES(210142,1002,'MG-DEMO-210142','BATCH',2202,3201,7201,210112,'阿莫西林可溶性粉',210122,'DEMO-M2610','2027-10-19',
 0.250,'G_PER_L_WATER',0.250,'MIX_WATER',5,'2026-10-16','2026-10-20',7,'2026-10-27',0,0,'演示：采食下降，已登记用药',
 500,500,'G',10002,0.0320,16,210165,'sample-cross-farm-medication-210142',0,0,10002,'2026-10-16 09:30:00',@b_at);
INSERT IGNORE INTO t_batch_withdrawal
(id,farm_id,batch_id,release_date,decisive_record_id,decisive_drug_name,last_med_date,withdrawal_days,cycle_no,recalculated_at,created_at,updated_at)
VALUES(210146,1002,3201,'2026-10-27',210142,'阿莫西林可溶性粉','2026-10-20',7,1,@b_at,@b_at,@b_at);
INSERT IGNORE INTO t_disinfect_plan
(id,farm_id,cycle_days,start_date,end_date,due_time,catalog_id,drug_id,disinfectant_name,concentration,method_code,
 status,generated_until,version,created_by,created_at,updated_at)
VALUES(210133,1002,3,@b_day,'2027-10-19','17:00:00',7301,210113,'聚维酮碘溶液','1:500','SPRAY','ACTIVE',@b_day,0,10002,@b_at,@b_at);
INSERT IGNORE INTO t_disinfect_plan_pen(id,farm_id,plan_id,pen_id,created_by,created_at,updated_at)
VALUES(210134,1002,210133,2201,10002,@b_at,@b_at),(210135,1002,210133,2202,10002,@b_at,@b_at);
INSERT IGNORE INTO t_disinfect_task
(id,farm_id,pen_id,plan_id,source,task_date,due_time,catalog_id,disinfectant_name,concentration,method_code,status,
 active_flag,record_id,manual_done,overdue_notified,version,created_by,created_at,updated_at)
VALUES(210143,1002,2201,210133,'PLAN',@b_day,'17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','PENDING',1,NULL,0,0,0,0,@b_at,@b_at),
 (210144,1002,2202,210133,'PLAN',@b_day,'17:00:00',7301,'聚维酮碘溶液','1:500','SPRAY','DONE',1,210145,0,0,0,0,@b_at,@b_at);
INSERT IGNORE INTO t_disinfect_record
(id,farm_id,pen_id,record_type,task_id,plan_concentration,actual_time,executor_id,drug_id,disinfectant_name,drug_lot_id,lot_no,
 usage_qty,usage_unit,concentration,concentration_confirmed,method_code,backfill,in_batch_cost,in_exec_rate,unit_price,cost_amount,
 stock_flow_id,client_request_id,deleted,version,created_by,created_at,updated_at)
VALUES(210145,1002,2202,'PLAN_TASK',210144,'1:500','2026-10-19 08:30:00',10002,210113,'聚维酮碘溶液',210123,'DEMO-D2610',
 0.5,'L','1:500',1,'SPRAY',0,1,1,9,4.50,210166,'sample-cross-farm-disinfect-210145',0,0,10002,'2026-10-19 08:30:00',@b_at);

-- 3. 入库、真实出库与成本。新档案余额已经是下面流水的净余额，重跑迁移不再增减库存。
INSERT IGNORE INTO t_stock_inbound
(id,farm_id,inbound_no,material_type,feed_id,drug_id,drug_lot_id,lot_no,input_qty,input_unit,qty,unit_price,
 measure_price,amount,expire_date,supplier_name,inbound_date,is_sync_finance,finance_record_id,flow_id,status,
 client_request_id,version,created_by,updated_by,created_at,updated_at)
VALUES
(210061,1002,'RK-DEMO-210061','FEED',210001,NULL,NULL,NULL,10000,'MEASURE',10000,3.60,3.6000,36000,NULL,
 '西岭饲料供应商','2026-10-15',1,210901,210071,'NORMAL','sample-cross-farm-inbound-210061',0,10002,10002,'2026-10-15 09:00:00',@b_at),
(210151,1002,'RK-DEMO-210151','DRUG',NULL,210111,210121,'DEMO-V2610',20,'PACK',20000,60,0.0600,1200,'2027-10-19',
 '西岭兽药供应商','2026-10-15',1,210902,210161,'NORMAL','sample-cross-farm-inbound-210151',0,10002,10002,'2026-10-15 09:10:00',@b_at),
(210152,1002,'RK-DEMO-210152','DRUG',NULL,210112,210122,'DEMO-M2610',10,'PACK',5000,16,0.0320,160,'2027-10-19',
 '西岭兽药供应商','2026-10-15',1,210903,210162,'NORMAL','sample-cross-farm-inbound-210152',0,10002,10002,'2026-10-15 09:20:00',@b_at),
(210153,1002,'RK-DEMO-210153','DRUG',NULL,210113,210123,'DEMO-D2610',4,'PACK',20,45,9.0000,180,'2027-10-19',
 '西岭兽药供应商','2026-10-15',1,210904,210163,'NORMAL','sample-cross-farm-inbound-210153',0,10002,10002,'2026-10-15 09:30:00',@b_at);
INSERT IGNORE INTO t_stock_flow
(id,farm_id,material_type,feed_id,drug_id,drug_lot_id,flow_type,direction,qty,unit_price,amount,stock_qty_after,
 stock_amount_after,biz_type,biz_id,batch_id,pen_id,operator_id,related_desc,occur_at,biz_date,is_batch_cost,is_feed_usage,
 origin_flow_id,status,created_at,updated_at)
VALUES
(210071,1002,'FEED',210001,NULL,NULL,'INBOUND','IN',10000,3.6,36000,10000,36000,'INBOUND',210061,NULL,NULL,10002,
 '肉鸡全价料入库','2026-10-15 09:00:00','2026-10-15',0,0,210071,'NORMAL',@b_at,@b_at),
(210072,1002,'FEED',210001,NULL,NULL,'FEED_USE','OUT',465,3.6,1674,9535,34326,'FEED_RECORD',210051,3201,2202,10002,
 '2 号鸡舍 · 早餐','2026-10-18 07:20:00','2026-10-18',1,1,210072,'NORMAL',@b_at,@b_at),
(210073,1002,'FEED',210001,NULL,NULL,'FEED_USE','OUT',465,3.6,1674,9070,32652,'FEED_RECORD',210052,3201,2202,10002,
 '2 号鸡舍 · 早餐','2026-10-19 07:20:00',@b_day,1,1,210073,'NORMAL',@b_at,@b_at),
(210161,1002,'DRUG',NULL,210111,210121,'INBOUND','IN',20000,0.06,1200,20000,1200,'INBOUND',210151,NULL,NULL,10002,
 '禽用活疫苗（演示）入库','2026-10-15 09:10:00','2026-10-15',0,0,210161,'NORMAL',@b_at,@b_at),
(210162,1002,'DRUG',NULL,210112,210122,'INBOUND','IN',5000,0.032,160,5000,160,'INBOUND',210152,NULL,NULL,10002,
 '阿莫西林入库','2026-10-15 09:20:00','2026-10-15',0,0,210162,'NORMAL',@b_at,@b_at),
(210163,1002,'DRUG',NULL,210113,210123,'INBOUND','IN',20,9,180,20,180,'INBOUND',210153,NULL,NULL,10002,
 '聚维酮碘入库','2026-10-15 09:30:00','2026-10-15',0,0,210163,'NORMAL',@b_at,@b_at),
(210164,1002,'DRUG',NULL,210111,210121,'VACCINE_USE','OUT',500,0.06,30,19500,1170,'IMMUNE_RECORD',210141,3202,2201,10002,
 '1 号育雏舍 · 疫苗接种','2026-10-18 09:30:00','2026-10-18',1,0,210164,'NORMAL',@b_at,@b_at),
(210165,1002,'DRUG',NULL,210112,210122,'MEDICATION_USE','OUT',500,0.032,16,4500,144,'MEDICATION_RECORD',210142,3201,2202,10002,
 '2 号鸡舍 · 用药','2026-10-16 09:30:00','2026-10-16',1,0,210165,'NORMAL',@b_at,@b_at),
(210166,1002,'DRUG',NULL,210113,210123,'DISINFECT_USE','OUT',0.5,9,4.5,19.5,175.5,'DISINFECT_RECORD',210145,3201,2202,10002,
 '2 号鸡舍 · 消毒','2026-10-19 08:30:00',@b_day,1,0,210166,'NORMAL',@b_at,@b_at);
INSERT IGNORE INTO t_batch_material_cost
(id,farm_id,batch_id,pen_id,flow_id,source_type,source_id,cost_type,qty,head_count,ratio,amount,biz_date,status,created_at,updated_at)
VALUES
(210081,1002,3201,2202,210072,'FEED_RECORD',210051,'FEED',465,9300,1,1674,'2026-10-18','NORMAL',@b_at,@b_at),
(210082,1002,3201,2202,210073,'FEED_RECORD',210052,'FEED',465,9300,1,1674,@b_day,'NORMAL',@b_at,@b_at),
(210171,1002,3202,2201,210164,'IMMUNE_RECORD',210141,'DRUG',500,9300,1,30,'2026-10-18','NORMAL',@b_at,@b_at),
(210172,1002,3201,2202,210165,'MEDICATION_RECORD',210142,'DRUG',500,9300,1,16,'2026-10-16','NORMAL',@b_at,@b_at),
(210173,1002,3201,2202,210166,'DISINFECT_RECORD',210145,'DRUG',0.5,9300,1,4.5,@b_day,'NORMAL',@b_at,@b_at);

-- 4. 已存在的雏鸡批次 3272（孵化 6004 产生、8480 只已转出），补实际出栏、收款和费用来源。
INSERT IGNORE INTO t_sale_order
(id,farm_id,batch_id,sale_no,pen_id,pen_allocations,category_id,breed_id,sale_date,quantity,total_weight,avg_weight,
 pricing_method,unit_price,sale_amount,buyer_name,due_date,effective_due_date,received_amount,unpaid_amount,receipt_count,
 payment_status,ended_batch,content_version,client_request_id,version,created_by,updated_by,created_at,updated_at)
VALUES(210201,1002,3272,'CL-DEMO-210201',2202,'[{"pen_id":2202,"pen_name":"2 号鸡舍","quantity":8480}]',201,2101,@b_day,
 8480,848.00,0.10,'BY_HEAD',4,33920,'西岭雏鸡采购户','2026-10-22','2026-10-22',10000,23920,1,'PARTIAL',1,1,
 'sample-cross-farm-sale-210201',0,10002,10002,'2026-10-19 15:30:00','2026-10-19 15:35:00');
INSERT IGNORE INTO t_sale_receipt
(id,farm_id,sale_order_id,batch_id,amount,receipt_date,payment_method,finance_record_id,status,client_request_id,created_by,created_at,updated_at)
VALUES(210202,1002,210201,3272,10000,@b_day,'BANK_TRANSFER',210905,'NORMAL','sample-cross-farm-receipt-210202',10002,
 '2026-10-19 15:35:00','2026-10-19 15:35:00');
INSERT IGNORE INTO t_finance_record
(id,farm_id,record_type,source_type,source_id,source_no,sale_order_id,category_id,amount,record_date,batch_id,note,detail_text,
 voucher_count,status,client_request_id,version,created_by,updated_by,created_at,updated_at)
VALUES
(210901,1002,'EXPENSE','FEED_INBOUND',210061,'RK-DEMO-210061',NULL,101,36000,'2026-10-15',NULL,NULL,'肉鸡全价料 10000 千克',
 0,'NORMAL','sample-cross-farm-finance-210901',0,10002,10002,'2026-10-15 09:00:00',@b_at),
(210902,1002,'EXPENSE','DRUG_INBOUND',210151,'RK-DEMO-210151',NULL,102,1200,'2026-10-15',NULL,NULL,'禽用活疫苗（演示） 20000 头份',
 0,'NORMAL','sample-cross-farm-finance-210902',0,10002,10002,'2026-10-15 09:10:00',@b_at),
(210903,1002,'EXPENSE','DRUG_INBOUND',210152,'RK-DEMO-210152',NULL,102,160,'2026-10-15',NULL,NULL,'阿莫西林 5000 克',
 0,'NORMAL','sample-cross-farm-finance-210903',0,10002,10002,'2026-10-15 09:20:00',@b_at),
(210904,1002,'EXPENSE','DRUG_INBOUND',210153,'RK-DEMO-210153',NULL,102,180,'2026-10-15',NULL,NULL,'聚维酮碘溶液 20 升',
 0,'NORMAL','sample-cross-farm-finance-210904',0,10002,10002,'2026-10-15 09:30:00',@b_at),
(210905,1002,'INCOME','SALE_RECEIPT',210202,'CL-DEMO-210201',210201,201,10000,@b_day,3272,NULL,'雏鸡销售 · 银行转账',
 0,'NORMAL','sample-cross-farm-finance-210905',0,10002,10002,'2026-10-19 15:35:00','2026-10-19 15:35:00'),
(210906,1002,'EXPENSE','MANUAL',NULL,NULL,NULL,104,8000,'2026-10-18',3272,'孵化期人工及能耗（演示）','孵化批次人工及能耗支出',
 0,'NORMAL','sample-cross-farm-finance-210906',0,10002,10002,'2026-10-18 16:00:00','2026-10-18 16:00:00');
INSERT IGNORE INTO t_batch_event
(id,farm_id,batch_id,event_type,event_date,pen_id,quantity,executor_user_id,source_biz_type,source_biz_id,
 capacity_confirmed,over_capacity_count,status,client_request_id,version,deleted,created_by,updated_by,created_at,updated_at)
VALUES
(210203,1002,3272,'SALE',@b_day,2202,8480,10002,'SALE_ORDER',210201,0,0,'EFFECTIVE','sample-cross-farm-sale-event',0,0,10002,10002,'2026-10-19 15:30:00',@b_at),
(210204,1002,3272,'ENTRY',@b_day,2202,8480,10001,'INCUBATION',6004,0,0,'EFFECTIVE','sample-cross-farm-hatch-entry',0,0,10001,10001,'2026-10-19 15:20:00',@b_at),
(210211,1002,3201,'ENTRY','2026-10-01',2202,9500,10002,NULL,NULL,0,0,'EFFECTIVE','sample-cross-farm-entry-3201',0,0,10002,10002,'2026-10-01 08:00:00',@b_at),
(210212,1002,3202,'ENTRY','2026-10-01',2201,9500,10002,NULL,NULL,0,0,'EFFECTIVE','sample-cross-farm-entry-3202',0,0,10002,10002,'2026-10-01 08:05:00',@b_at),
(210213,1002,3201,'DEATH','2026-10-18',2202,200,10002,NULL,NULL,0,0,'EFFECTIVE','sample-cross-farm-death-3201',0,0,10002,10002,'2026-10-18 17:00:00',@b_at),
(210214,1002,3202,'DEATH','2026-10-18',2201,200,10002,NULL,NULL,0,0,'EFFECTIVE','sample-cross-farm-death-3202',0,0,10002,10002,'2026-10-18 17:00:00',@b_at);
INSERT IGNORE INTO t_batch_timeline
(id,farm_id,batch_id,pen_id,record_type,biz_type,biz_id,biz_date,occurred_at,title,subtitle,operator_user_id,deleted,created_at,updated_at)
VALUES
(210205,1002,3272,2202,'ENTRY','BATCH_ENTRY',210204,@b_day,'2026-10-19 15:20:00','入栏 · 8480 只','孵化记录 6004 转入',10001,0,@b_at,@b_at),
(210206,1002,3272,2202,'SALE','SALE_ORDER',210201,@b_day,'2026-10-19 15:30:00','出栏 · 8480 只','西岭雏鸡采购户',10002,0,@b_at,@b_at);
INSERT IGNORE INTO t_batch_settlement
(id,farm_id,batch_id,status,settled_at,sale_income,cost_other,cost_total,profit,sold_count,cost_per_head,profit_per_head,
 sold_weight_kg,calc_at,version,created_at,updated_at)
VALUES(210207,1002,3272,'LOCKED','2026-10-19 15:40:00',33920,8000,8000,25920,8480,0.94,3.06,848.00,
 '2026-10-19 15:40:00',0,@b_at,@b_at);

-- 5. 待办严格引用真实任务、方案和登记记录，已完成项的登记记录同场且可追溯。
INSERT IGNORE INTO t_todo
(id,farm_id,todo_type,category,pen_id,batch_id,pen_name,batch_no,biz_type,biz_id,dedup_key,title,content_text,
 receiver_scope,due_date,time_type,due_time,overdue_at,sort_time,status,overdue_done,completed_at,completed_by,
 complete_way,complete_record_type,complete_record_id,version,deleted,created_by,created_at,updated_at)
SELECT 210410+(f.id-210040),f.farm_id,'FEEDING','WORK',f.pen_id,f.batch_id,p.pen_name,b.batch_no,'FEED_TASK',f.id,
 CONCAT('FEED_TASK:',f.id),CONCAT(p.pen_name,' · ',f.meal_name,'饲喂'),'肉鸡全价料 · 计划 465 千克','PEN_ALL',f.task_date,
 'AT_TIME',f.feed_time,f.deadline_at,f.feed_at,IF(f.status='DONE','DONE','PENDING'),0,
 IF(f.status='DONE',r.created_at,NULL),IF(f.status='DONE',r.executor_id,NULL),IF(f.status='DONE','REGISTER',NULL),
 IF(f.status='DONE','FEED_RECORD',NULL),r.id,0,0,0,f.created_at,@b_at
FROM t_feed_task f JOIN t_pen p ON p.id=f.pen_id JOIN t_batch b ON b.id=f.batch_id LEFT JOIN t_feed_record r ON r.id=f.record_id
WHERE f.id IN(210041,210042,210043,210044,210045);
UPDATE t_feed_task f JOIN t_todo t ON t.farm_id=f.farm_id AND t.biz_type='FEED_TASK' AND t.biz_id=f.id
SET f.todo_id=t.id WHERE f.id IN(210041,210042,210043,210044,210045);
INSERT IGNORE INTO t_todo
(id,farm_id,todo_type,category,pen_id,batch_id,pen_name,batch_no,biz_type,biz_id,dedup_key,title,content_text,
 receiver_scope,due_date,time_type,due_time,overdue_at,sort_time,status,overdue_done,completed_at,completed_by,
 complete_way,complete_record_type,complete_record_id,version,deleted,created_by,created_at,updated_at)
VALUES
(210401,1002,'IMMUNIZATION','WORK',2202,3201,'2 号鸡舍','ZM2610-026','IMMUNE_PLAN',210131,'IMMUNE_PLAN:210131',
 '2 号鸡舍 · 免疫接种','禽用活疫苗（演示）','PEN_ALL',@b_day,'BEFORE_TIME','18:00:00','2026-10-19 18:00:00','2026-10-19 18:00:00',
 'PENDING',0,NULL,NULL,NULL,NULL,NULL,0,0,0,@b_at,@b_at),
(210402,1002,'IMMUNIZATION','WORK',2201,3202,'1 号育雏舍','ZM2610-027','IMMUNE_PLAN',210132,'IMMUNE_PLAN:210132',
 '1 号育雏舍 · 免疫接种','禽用活疫苗（演示）','PEN_ALL','2026-10-18','BEFORE_TIME','18:00:00','2026-10-18 18:00:00','2026-10-18 18:00:00',
 'DONE',0,'2026-10-18 09:30:00',10002,'REGISTER','IMMUNE_RECORD',210141,0,0,0,@b_at,@b_at),
(210403,1002,'DISINFECTION','WORK',2201,NULL,'1 号育雏舍',NULL,'DISINFECT_TASK',210143,'DISINFECT_TASK:210143',
 '1 号育雏舍 · 消毒','聚维酮碘溶液 1:500','PEN_ALL',@b_day,'BEFORE_TIME','17:00:00','2026-10-19 17:00:00','2026-10-19 17:00:00',
 'PENDING',0,NULL,NULL,NULL,NULL,NULL,0,0,0,@b_at,@b_at),
(210404,1002,'DISINFECTION','WORK',2202,NULL,'2 号鸡舍',NULL,'DISINFECT_TASK',210144,'DISINFECT_TASK:210144',
 '2 号鸡舍 · 消毒','聚维酮碘溶液 1:500','PEN_ALL',@b_day,'BEFORE_TIME','17:00:00','2026-10-19 17:00:00','2026-10-19 17:00:00',
 'DONE',0,'2026-10-19 08:30:00',10002,'REGISTER','DISINFECT_RECORD',210145,0,0,0,@b_at,@b_at),
(210405,1002,'RECEIVABLE','BUSINESS',NULL,3272,NULL,'ZM2610-072','SALE_ORDER',210201,'SALE_ORDER:210201',
 '雏鸡销售待收款','西岭雏鸡采购户 · 未收 23920 元','MANAGERS','2026-10-22','ALL_DAY',NULL,'2026-10-23 00:00:00','2026-10-22 00:00:00',
 'PENDING',0,NULL,NULL,NULL,NULL,NULL,0,0,0,@b_at,@b_at);

-- V9_20 样例日期白名单：只登记本迁移明确新增的 ID，不按最大 ID 或整张表猜测样例范围。
INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
VALUES
('t_feed',210001,'SAMPLE',@b_day),
('t_feed_plan',210011,'SAMPLE',@b_day),('t_feed_plan',210012,'SAMPLE',@b_day),
('t_feed_plan_stage',210021,'SAMPLE',@b_day),('t_feed_plan_stage',210022,'SAMPLE',@b_day),
('t_feed_plan_meal',210031,'SAMPLE',@b_day),('t_feed_plan_meal',210032,'SAMPLE',@b_day),
('t_feed_plan_meal',210033,'SAMPLE',@b_day),('t_feed_plan_meal',210034,'SAMPLE',@b_day),
('t_feed_task',210041,'SAMPLE',@b_day),('t_feed_task',210042,'SAMPLE',@b_day),('t_feed_task',210043,'SAMPLE',@b_day),
('t_feed_task',210044,'SAMPLE',@b_day),('t_feed_task',210045,'SAMPLE',@b_day),
('t_feed_record',210051,'SAMPLE',@b_day),('t_feed_record',210052,'SAMPLE',@b_day),
('t_weigh_record',210091,'SAMPLE',@b_day),('t_batch_growth',210092,'SAMPLE',@b_day),('t_batch_growth',210093,'SAMPLE',@b_day),
('t_drug_catalog',210101,'SAMPLE',@b_day),('t_drug_catalog_usage',210102,'SAMPLE',@b_day),
('t_drug',210111,'SAMPLE',@b_day),('t_drug',210112,'SAMPLE',@b_day),('t_drug',210113,'SAMPLE',@b_day),
('t_drug_lot',210121,'SAMPLE',@b_day),('t_drug_lot',210122,'SAMPLE',@b_day),('t_drug_lot',210123,'SAMPLE',@b_day),
('t_immune_plan',210131,'SAMPLE',@b_day),('t_immune_plan',210132,'SAMPLE',@b_day),('t_immune_record',210141,'SAMPLE',@b_day),
('t_medication_record',210142,'SAMPLE',@b_day),('t_batch_withdrawal',210146,'SAMPLE',@b_day),
('t_disinfect_plan',210133,'SAMPLE',@b_day),('t_disinfect_plan_pen',210134,'SAMPLE',@b_day),('t_disinfect_plan_pen',210135,'SAMPLE',@b_day),
('t_disinfect_task',210143,'SAMPLE',@b_day),('t_disinfect_task',210144,'SAMPLE',@b_day),('t_disinfect_record',210145,'SAMPLE',@b_day),
('t_stock_inbound',210061,'SAMPLE',@b_day),('t_stock_inbound',210151,'SAMPLE',@b_day),('t_stock_inbound',210152,'SAMPLE',@b_day),('t_stock_inbound',210153,'SAMPLE',@b_day),
('t_stock_flow',210071,'SAMPLE',@b_day),('t_stock_flow',210072,'SAMPLE',@b_day),('t_stock_flow',210073,'SAMPLE',@b_day),
('t_stock_flow',210161,'SAMPLE',@b_day),('t_stock_flow',210162,'SAMPLE',@b_day),('t_stock_flow',210163,'SAMPLE',@b_day),
('t_stock_flow',210164,'SAMPLE',@b_day),('t_stock_flow',210165,'SAMPLE',@b_day),('t_stock_flow',210166,'SAMPLE',@b_day),
('t_batch_material_cost',210081,'SAMPLE',@b_day),('t_batch_material_cost',210082,'SAMPLE',@b_day),
('t_batch_material_cost',210171,'SAMPLE',@b_day),('t_batch_material_cost',210172,'SAMPLE',@b_day),('t_batch_material_cost',210173,'SAMPLE',@b_day),
('t_sale_order',210201,'SAMPLE',@b_day),('t_sale_receipt',210202,'SAMPLE',@b_day),
('t_finance_record',210901,'SAMPLE',@b_day),('t_finance_record',210902,'SAMPLE',@b_day),('t_finance_record',210903,'SAMPLE',@b_day),
('t_finance_record',210904,'SAMPLE',@b_day),('t_finance_record',210905,'SAMPLE',@b_day),('t_finance_record',210906,'SAMPLE',@b_day),
('t_batch_event',210203,'SAMPLE',@b_day),('t_batch_event',210204,'SAMPLE',@b_day),
('t_batch_event',210211,'SAMPLE',@b_day),('t_batch_event',210212,'SAMPLE',@b_day),('t_batch_event',210213,'SAMPLE',@b_day),('t_batch_event',210214,'SAMPLE',@b_day),
('t_batch_timeline',210205,'SAMPLE',@b_day),('t_batch_timeline',210206,'SAMPLE',@b_day),('t_batch_settlement',210207,'SAMPLE',@b_day),
('t_todo',210401,'SAMPLE',@b_day),('t_todo',210402,'SAMPLE',@b_day),('t_todo',210403,'SAMPLE',@b_day),
('t_todo',210404,'SAMPLE',@b_day),('t_todo',210405,'SAMPLE',@b_day),
('t_todo',210411,'SAMPLE',@b_day),('t_todo',210412,'SAMPLE',@b_day),('t_todo',210413,'SAMPLE',@b_day),
('t_todo',210414,'SAMPLE',@b_day),('t_todo',210415,'SAMPLE',@b_day);
