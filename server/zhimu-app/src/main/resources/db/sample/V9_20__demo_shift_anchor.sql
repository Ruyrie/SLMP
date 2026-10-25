-- 演示行显式白名单。不能按 MAX(id) 划界：上线前用户已创建的数据可能位于样例 ID 之间。
CREATE TABLE t_demo_anchor (
 group_code VARCHAR(30) NOT NULL PRIMARY KEY, anchor_date DATE NOT NULL,
 shifted_at DATETIME NULL, shift_days INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE t_demo_sample_row (
 table_name VARCHAR(64) NOT NULL, row_id BIGINT UNSIGNED NOT NULL,
 group_code VARCHAR(30) NOT NULL, anchor_date DATE NOT NULL,
 PRIMARY KEY(table_name,row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE t_demo_shift_work (
 table_name VARCHAR(64) NOT NULL, row_id BIGINT UNSIGNED NOT NULL, delta INT NOT NULL,
 PRIMARY KEY(table_name,row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
SET @demo_now = UTC_TIMESTAMP() + INTERVAL 8 HOUR;
SET @demo_migration_day = COALESCE((SELECT original_plan_date FROM t_immune_plan WHERE id=160101),DATE(@demo_now));
INSERT INTO t_demo_anchor(group_code,anchor_date,created_at,updated_at) VALUES
 ('SAMPLE','2026-10-19',@demo_now,@demo_now),
 ('HEALTH_1024','2026-10-24',@demo_now,@demo_now),
 ('MIGRATION_DAY',@demo_migration_day,@demo_now,@demo_now);


INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_abnormal_report',id,'SAMPLE','2026-10-19' FROM t_abnormal_report WHERE id IN (5988,6001,6002,6003,6005);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_citation',id,'SAMPLE','2026-10-19' FROM t_ai_citation WHERE id IN (9901,9902);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_correction',id,'SAMPLE','2026-10-19' FROM t_ai_correction WHERE id IN (9001);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_diagnosis',id,'SAMPLE','2026-10-19' FROM t_ai_diagnosis WHERE id IN (9901,9902,9903,9904,9905);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_knowledge',id,'SAMPLE','2026-10-19' FROM t_ai_knowledge WHERE id IN (8101,8102,8103,8104);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_message',id,'SAMPLE','2026-10-19' FROM t_ai_message WHERE id IN (5460,5461,5462,5470,5471,5472,5473,5474,5475,5476,5477,5478,5479,5480,5481,5482,5483,5484,5485,5490,5491,5492,5493,5494,5495,5501);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_session',id,'SAMPLE','2026-10-19' FROM t_ai_session WHERE id IN (1006,1009,1012,1015,1018,1021);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch',id,'SAMPLE','2026-10-19' FROM t_batch WHERE id IN (3001,3002,3003,3004,3010,3011,3012,3013,3014,3015,3016,3017,3018,3019,3020,3021,3041,3042,3052,3201,3202,3211,3212,3213,3272,3273,3274);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_event',id,'SAMPLE','2026-10-19' FROM t_batch_event WHERE id IN (901,902,903,904,905,906,907,908,909,910,911);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_growth',id,'SAMPLE','2026-10-19' FROM t_batch_growth WHERE id IN (601,602,603,604);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_material_cost',id,'SAMPLE','2026-10-19' FROM t_batch_material_cost WHERE id IN (8001,8002,8003,8004,8005,8006,8007);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_pen',id,'SAMPLE','2026-10-19' FROM t_batch_pen WHERE id IN (4001,4002,4003,4004,4005,4006,4010,4011,4012);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_settlement',id,'SAMPLE','2026-10-19' FROM t_batch_settlement WHERE id IN (6001,6002,6009);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_settlement_supplement',id,'SAMPLE','2026-10-19' FROM t_batch_settlement_supplement WHERE id IN (401);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_stock_daily',id,'SAMPLE','2026-10-19' FROM t_batch_stock_daily WHERE id IN (9001,9002,9003,9004);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_batch_timeline',id,'SAMPLE','2026-10-19' FROM t_batch_timeline WHERE id IN (8100,8101,8102,8103,8104,8105,8106,8107,8108,8109,8110);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_biz_report',id,'SAMPLE','2026-10-19' FROM t_biz_report WHERE id IN (6001,6002,6003,6004,6005);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_breeding_animal',id,'SAMPLE','2026-10-19' FROM t_breeding_animal WHERE id IN (4102,4109,4115,4118,4124,4127,4131,4133,4136,4139,4142,4145,4146,4149,4152,4155,4157,4161,4163,4166,4168,4171,4172,4174,4178,4182);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_breeding_cycle',id,'SAMPLE','2026-10-19' FROM t_breeding_cycle WHERE id IN (5031,5032,5061,5109,5118,5124,5127,5131,5136,5142,5146,5149,5152,5155,5157,5163,5166,5168,5171,5172,5174,5178);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_course',id,'SAMPLE','2026-10-19' FROM t_course WHERE id IN (6101,6102,6103,6104,6105,6106,6201,6202,6203,6204,6301,6302,6303);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_diagnosis_handle',id,'SAMPLE','2026-10-19' FROM t_diagnosis_handle WHERE id IN (7702);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_disinfect_plan',id,'SAMPLE','2026-10-19' FROM t_disinfect_plan WHERE id IN (9201,9202,160401);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_disinfect_plan_pen',id,'SAMPLE','2026-10-19' FROM t_disinfect_plan_pen WHERE id IN (9301,9302,9303,9304,160401,160402);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_disinfect_record',id,'SAMPLE','2026-10-19' FROM t_disinfect_record WHERE id IN (9501,9502,9503,160601);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_disinfect_task',id,'SAMPLE','2026-10-19' FROM t_disinfect_task WHERE id IN (9401,9402,9403,9404,9405,9406,9407,9408,9409,160501,160502,160503);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_drug',id,'SAMPLE','2026-10-19' FROM t_drug WHERE id IN (5001,5002,5003,5004,5201,5202,5204,5205,5301,5302);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_drug_lot',id,'SAMPLE','2026-10-19' FROM t_drug_lot WHERE id IN (5201,6001,6002,6011,6041,6101,6103,6111,6121,6131,6301,6311);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_expense_subcategory',id,'SAMPLE','2026-10-19' FROM t_expense_subcategory WHERE id IN (1011,1012,1013,1031,1032,1041,1061);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_farrowing_record',id,'SAMPLE','2026-10-19' FROM t_farrowing_record WHERE id IN (8109,8118,8124,8127,8161,8201,8202);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed',id,'SAMPLE','2026-10-19' FROM t_feed WHERE id IN (5101,5102,5103);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_plan',id,'SAMPLE','2026-10-19' FROM t_feed_plan WHERE id IN (501,502,503);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_plan_stage',id,'SAMPLE','2026-10-19' FROM t_feed_plan_stage WHERE id IN (811,812,813,814,821,822,823,824,831,832);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_record',id,'SAMPLE','2026-10-19' FROM t_feed_record WHERE id IN (7001,7002,7106,7119);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_task',id,'SAMPLE','2026-10-19' FROM t_feed_task WHERE id IN (70101,70102,70103,70104,70105,70106,70107,70108,70109,70110,70111,70112,70113,70114,70115,70116,70117,70118,70119,70120);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_finance_record',id,'SAMPLE','2026-10-19' FROM t_finance_record WHERE id IN (8201,8202,8203,8204,8205,8206,8207,8208,8209);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_immune_plan',id,'SAMPLE','2026-10-19' FROM t_immune_plan WHERE id IN (9001,9002,9003,9004,9005,9006,9007,9008,9009,160101,160102,160103);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_immune_record',id,'SAMPLE','2026-10-19' FROM t_immune_record WHERE id IN (9601,160201);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_incubation',id,'SAMPLE','2026-10-19' FROM t_incubation WHERE id IN (6001,6002,6003,6004,6005,6006,6101,6102);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_inspection',id,'SAMPLE','2026-10-19' FROM t_inspection WHERE id IN (50060,50071,50080,50081,50091,50101,50103,50121,50122,50123,50124);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_inspection_setting',id,'SAMPLE','2026-10-19' FROM t_inspection_setting WHERE id IN (1601);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_market_price',id,'SAMPLE','2026-10-19' FROM t_market_price WHERE id IN (5001,5002,5003,5004,5005,5006,5007,5008,5009,5010,5011,5012,5013,5014,5015,5016,5017,5018,5019,5020,5021,5022,5023,5024,5025,5026,5027,5028,5029,5101,5102,5103,5201,5202,5301,5302,5401,5402);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_mating_record',id,'SAMPLE','2026-10-19' FROM t_mating_record WHERE id IN (72001,72002,72003,72009,72018,72024,72027,72036,72042,72046,72049,72052,72055,72057,72061,72063,72066,72068,72071,72072,72074,72078);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_medication_record',id,'SAMPLE','2026-10-19' FROM t_medication_record WHERE id IN (5101,9101,9102,160301);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_message',id,'SAMPLE','2026-10-19' FROM t_message WHERE id IN (501,502,503,504,505,506,507,508,509,510,511,512,513,514,515,516,517,518,519,520,521,522,523,524);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_pen',id,'SAMPLE','2026-10-19' FROM t_pen WHERE id IN (2001,2002,2003,2004,2005,2006,2007,2201,2202,2203,2301,2302,2303);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_pregnancy_check',id,'SAMPLE','2026-10-19' FROM t_pregnancy_check WHERE id IN (71001,71005,71009,71031,71032,71033,71034,71036,71042,71046,71052,71057,71062,71063,71068,71071,71074,71078);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_sale_advice',id,'SAMPLE','2026-10-19' FROM t_sale_advice WHERE id IN (7301,7302,7303);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_sale_advice_risk',id,'SAMPLE','2026-10-19' FROM t_sale_advice_risk WHERE id IN (8101,8102);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_sale_order',id,'SAMPLE','2026-10-19' FROM t_sale_order WHERE id IN (9102,9105,9106);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_sale_receipt',id,'SAMPLE','2026-10-19' FROM t_sale_receipt WHERE id IN (5201,5202);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_flow',id,'SAMPLE','2026-10-19' FROM t_stock_flow WHERE id IN (9201,9202,9240,9260,9270,9288,9290,9301,9302,9500,9501,9502,9503,9504,9505,9506,9507,9508,9511,9512,9520,9521,9522,9523,9524,9525,160001,160002,160003,160004,160005,160006,160007,160008,160009);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_inbound',id,'SAMPLE','2026-10-19' FROM t_stock_inbound WHERE id IN (6201,6202,6203,6204,6205,6207,6209,6210,6211,6212,6213,6214,6215,6216);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_loss',id,'SAMPLE','2026-10-19' FROM t_stock_loss WHERE id IN (6401);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_outbound',id,'SAMPLE','2026-10-19' FROM t_stock_outbound WHERE id IN (6501,6511,6512);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_partner',id,'SAMPLE','2026-10-19' FROM t_stock_partner WHERE id IN (8101,8102,8103,8104,8105);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stock_setting',id,'SAMPLE','2026-10-19' FROM t_stock_setting WHERE id IN (8201);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stocktake',id,'SAMPLE','2026-10-19' FROM t_stocktake WHERE id IN (6290,6301);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_stocktake_item',id,'SAMPLE','2026-10-19' FROM t_stocktake_item WHERE id IN (1,2,3);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_todo',id,'SAMPLE','2026-10-19' FROM t_todo WHERE id IN (90101,90103,90105,90106,90107,90108,90109,90110,90111,90112,90113,90114,90115,90116,90117,90118,90119,90120,90121,90122,90201,90309,90401,90402,90403,90404,90405,90406,90407,90501,90502,90503,90504,90505);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_todo_handle_log',id,'SAMPLE','2026-10-19' FROM t_todo_handle_log WHERE id IN (7801,7802,7803);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_todo_remind',id,'SAMPLE','2026-10-19' FROM t_todo_remind WHERE id IN (7701,7702,7703,7704,7705,7706,7707,7708,7709,7710,7711,7712,7713);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_weaning_record',id,'SAMPLE','2026-10-19' FROM t_weaning_record WHERE id IN (71103,71107,71161);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_weigh_record',id,'SAMPLE','2026-10-19' FROM t_weigh_record WHERE id IN (3300,3301,3303,3304,3310);

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_abnormal_report_symptom',id,'SAMPLE','2026-10-19' FROM t_abnormal_report_symptom WHERE (farm_id = 1001 AND report_id = 5988 AND symptom_id = 404 AND created_at = '2026-10-18 15:20:00') OR (farm_id = 1001 AND report_id = 5988 AND symptom_id = 403 AND created_at = '2026-10-18 15:20:00') OR (farm_id = 1001 AND report_id = 6001 AND symptom_id = 401 AND created_at = '2026-10-19 09:20:00') OR (farm_id = 1001 AND report_id = 6001 AND symptom_id = 402 AND created_at = '2026-10-19 09:20:00') OR (farm_id = 1001 AND report_id = 6001 AND symptom_id = 403 AND created_at = '2026-10-19 09:20:00') OR (farm_id = 1001 AND report_id = 6002 AND symptom_id = 405 AND created_at = '2026-10-19 09:26:00') OR (farm_id = 1001 AND report_id = 6002 AND symptom_id = 404 AND created_at = '2026-10-19 09:26:00') OR (farm_id = 1001 AND report_id = 6002 AND symptom_id = 403 AND created_at = '2026-10-19 09:26:00') OR (farm_id = 1001 AND report_id = 6003 AND symptom_id = 401 AND created_at = '2026-10-19 10:05:00') OR (farm_id = 1001 AND report_id = 6005 AND symptom_id = 401 AND created_at = '2026-10-19 10:30:00') OR (farm_id = 1001 AND report_id = 6005 AND symptom_id = 406 AND created_at = '2026-10-19 10:30:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_diagnosis_advice',id,'SAMPLE','2026-10-19' FROM t_ai_diagnosis_advice WHERE (farm_id = 1001 AND diagnosis_id = 9901 AND seq_no = 1 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND seq_no = 2 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND seq_no = 3 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND seq_no = 4 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND seq_no = 5 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND seq_no = 1 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND seq_no = 2 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND seq_no = 3 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND seq_no = 4 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9904 AND seq_no = 1 AND created_at = '2026-10-19 10:31:00') OR (farm_id = 1001 AND diagnosis_id = 9904 AND seq_no = 2 AND created_at = '2026-10-19 10:31:00') OR (farm_id = 1001 AND diagnosis_id = 9904 AND seq_no = 3 AND created_at = '2026-10-19 10:31:00') OR (farm_id = 1001 AND diagnosis_id = 9905 AND seq_no = 1 AND created_at = '2026-10-18 15:21:00') OR (farm_id = 1001 AND diagnosis_id = 9905 AND seq_no = 2 AND created_at = '2026-10-18 15:21:00') OR (farm_id = 1001 AND diagnosis_id = 9905 AND seq_no = 3 AND created_at = '2026-10-18 15:21:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_ai_diagnosis_cause',id,'SAMPLE','2026-10-19' FROM t_ai_diagnosis_cause WHERE (farm_id = 1001 AND diagnosis_id = 9901 AND rank_no = 1 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND rank_no = 2 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9901 AND rank_no = 3 AND created_at = '2026-10-19 09:21:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND rank_no = 1 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND rank_no = 2 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9902 AND rank_no = 3 AND created_at = '2026-10-19 09:27:00') OR (farm_id = 1001 AND diagnosis_id = 9904 AND rank_no = 1 AND created_at = '2026-10-19 10:31:00') OR (farm_id = 1001 AND diagnosis_id = 9904 AND rank_no = 2 AND created_at = '2026-10-19 10:31:00') OR (farm_id = 1001 AND diagnosis_id = 9905 AND rank_no = 1 AND created_at = '2026-10-18 15:21:00') OR (farm_id = 1001 AND diagnosis_id = 9905 AND rank_no = 2 AND created_at = '2026-10-18 15:21:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_exec_daily',id,'SAMPLE','2026-10-19' FROM t_feed_exec_daily WHERE (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-12' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-13' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-14' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-15' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-16' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-17' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3001 AND stat_date = '2026-10-18' AND created_at = '2026-10-19 00:10:00') OR (farm_id = 1001 AND batch_id = 3002 AND stat_date = '2026-10-18' AND created_at = '2026-10-19 00:10:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_feed_plan_meal',id,'SAMPLE','2026-10-19' FROM t_feed_plan_meal WHERE (farm_id = 1001 AND plan_id = 501 AND stage_id = 811 AND meal_no = 1 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 811 AND meal_no = 2 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 811 AND meal_no = 3 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 812 AND meal_no = 1 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 812 AND meal_no = 2 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 812 AND meal_no = 3 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 813 AND meal_no = 1 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 813 AND meal_no = 2 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 813 AND meal_no = 3 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 814 AND meal_no = 1 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 501 AND stage_id = 814 AND meal_no = 2 AND created_at = '2026-10-01 15:20:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 821 AND meal_no = 1 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 821 AND meal_no = 2 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 821 AND meal_no = 3 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 822 AND meal_no = 1 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 822 AND meal_no = 2 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 822 AND meal_no = 3 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 823 AND meal_no = 1 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 823 AND meal_no = 2 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 823 AND meal_no = 3 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 824 AND meal_no = 1 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 502 AND stage_id = 824 AND meal_no = 2 AND created_at = '2026-10-05 10:30:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 831 AND meal_no = 1 AND created_at = '2026-10-12 08:40:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 831 AND meal_no = 2 AND created_at = '2026-10-12 08:40:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 831 AND meal_no = 3 AND created_at = '2026-10-12 08:40:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 832 AND meal_no = 1 AND created_at = '2026-10-12 08:40:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 832 AND meal_no = 2 AND created_at = '2026-10-12 08:40:00') OR (farm_id = 1001 AND plan_id = 503 AND stage_id = 832 AND meal_no = 3 AND created_at = '2026-10-12 08:40:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_inspection_item_result',id,'SAMPLE','2026-10-19' FROM t_inspection_item_result WHERE (farm_id = 1001 AND inspection_id = 50060 AND item_id = 101 AND created_at = '2026-10-13 08:40:00') OR (farm_id = 1001 AND inspection_id = 50060 AND item_id = 102 AND created_at = '2026-10-13 08:40:00') OR (farm_id = 1001 AND inspection_id = 50060 AND item_id = 103 AND created_at = '2026-10-13 08:40:00') OR (farm_id = 1001 AND inspection_id = 50071 AND item_id = 101 AND created_at = '2026-10-14 08:44:00') OR (farm_id = 1001 AND inspection_id = 50071 AND item_id = 102 AND created_at = '2026-10-14 08:44:00') OR (farm_id = 1001 AND inspection_id = 50071 AND item_id = 103 AND created_at = '2026-10-14 08:44:00') OR (farm_id = 1001 AND inspection_id = 50080 AND item_id = 101 AND created_at = '2026-10-15 08:52:00') OR (farm_id = 1001 AND inspection_id = 50080 AND item_id = 102 AND created_at = '2026-10-15 08:52:00') OR (farm_id = 1001 AND inspection_id = 50080 AND item_id = 103 AND created_at = '2026-10-15 08:52:00') OR (farm_id = 1001 AND inspection_id = 50081 AND item_id = 101 AND created_at = '2026-10-17 10:05:00') OR (farm_id = 1001 AND inspection_id = 50081 AND item_id = 102 AND created_at = '2026-10-17 10:05:00') OR (farm_id = 1001 AND inspection_id = 50081 AND item_id = 103 AND created_at = '2026-10-17 10:05:00') OR (farm_id = 1001 AND inspection_id = 50091 AND item_id = 101 AND created_at = '2026-10-17 08:47:00') OR (farm_id = 1001 AND inspection_id = 50091 AND item_id = 102 AND created_at = '2026-10-17 08:47:00') OR (farm_id = 1001 AND inspection_id = 50091 AND item_id = 103 AND created_at = '2026-10-17 08:47:00') OR (farm_id = 1001 AND inspection_id = 50101 AND item_id = 101 AND created_at = '2026-10-18 18:36:00') OR (farm_id = 1001 AND inspection_id = 50101 AND item_id = 102 AND created_at = '2026-10-18 18:36:00') OR (farm_id = 1001 AND inspection_id = 50101 AND item_id = 103 AND created_at = '2026-10-18 18:36:00') OR (farm_id = 1001 AND inspection_id = 50121 AND item_id = 101 AND created_at = '2026-10-19 09:12:00') OR (farm_id = 1001 AND inspection_id = 50121 AND item_id = 102 AND created_at = '2026-10-19 09:12:00') OR (farm_id = 1001 AND inspection_id = 50121 AND item_id = 103 AND created_at = '2026-10-19 09:12:00') OR (farm_id = 1001 AND inspection_id = 50124 AND item_id = 101 AND created_at = '2026-10-19 08:40:00') OR (farm_id = 1001 AND inspection_id = 50124 AND item_id = 102 AND created_at = '2026-10-19 08:40:00') OR (farm_id = 1001 AND inspection_id = 50124 AND item_id = 103 AND created_at = '2026-10-19 08:40:00');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date) SELECT 't_weigh_record_item',id,'SAMPLE','2026-10-19' FROM t_weigh_record_item WHERE (farm_id = 1001 AND weigh_record_id = 3303 AND seq_no = 1 AND created_at = '2026-10-01 09:10:00') OR (farm_id = 1001 AND weigh_record_id = 3303 AND seq_no = 2 AND created_at = '2026-10-01 09:10:00') OR (farm_id = 1001 AND weigh_record_id = 3303 AND seq_no = 3 AND created_at = '2026-10-01 09:10:00');

-- V9_16 自动分摊的成本行通过这次明确的健康出库流水识别。
INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
SELECT 't_batch_material_cost',id,'SAMPLE','2026-10-19' FROM t_batch_material_cost WHERE flow_id IN (160001,160002,160003,160004,160005,160006,160007,160008,160009);
-- 10-24 健康样例和按迁移当天生成的样例各用自己的基准。
UPDATE t_demo_sample_row SET group_code='HEALTH_1024',anchor_date='2026-10-24'
WHERE (table_name='t_medication_record' AND row_id IN(9101,9102))
 OR (table_name='t_disinfect_plan' AND row_id IN(9201,9202))
 OR (table_name='t_disinfect_plan_pen' AND row_id IN(9301,9302,9303,9304))
 OR (table_name='t_disinfect_task' AND row_id BETWEEN 9401 AND 9409)
 OR (table_name='t_disinfect_record' AND row_id IN(9501,9502,9503));
UPDATE t_demo_sample_row SET group_code='MIGRATION_DAY',anchor_date=@demo_migration_day
WHERE (table_name='t_immune_plan' AND row_id IN(160101,160102,160103))
 OR (table_name='t_immune_record' AND row_id=160201)
 OR (table_name='t_medication_record' AND row_id=160301)
 OR (table_name='t_disinfect_plan' AND row_id=160401)
 OR (table_name='t_disinfect_plan_pen' AND row_id IN(160401,160402))
 OR (table_name='t_disinfect_task' AND row_id IN(160501,160502,160503))
 OR (table_name='t_disinfect_record' AND row_id=160601);
UPDATE t_demo_sample_row r JOIN t_stock_flow f ON r.table_name='t_stock_flow' AND r.row_id=f.id
SET r.group_code=CASE WHEN (f.biz_type='MEDICATION_RECORD' AND f.biz_id IN(9101,9102)) OR (f.biz_type='DISINFECT_RECORD' AND f.biz_id IN(9501,9502,9503)) THEN 'HEALTH_1024' ELSE 'MIGRATION_DAY' END,
 r.anchor_date=CASE WHEN (f.biz_type='MEDICATION_RECORD' AND f.biz_id IN(9101,9102)) OR (f.biz_type='DISINFECT_RECORD' AND f.biz_id IN(9501,9502,9503)) THEN '2026-10-24' ELSE @demo_migration_day END
WHERE f.id BETWEEN 160001 AND 160009 AND NOT(f.biz_type='IMMUNE_RECORD' AND f.biz_id=9601);
UPDATE t_demo_sample_row r JOIN t_batch_material_cost c ON r.table_name='t_batch_material_cost' AND r.row_id=c.id
JOIN t_demo_sample_row f ON f.table_name='t_stock_flow' AND f.row_id=c.flow_id
SET r.group_code=f.group_code,r.anchor_date=f.anchor_date;
