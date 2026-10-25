-- 西岭鸡场（1002）演示数据补齐：员工身份、AI 会话、异常上报、饲料盘点、免疫计划。
-- 只新增演示行，ID 显式落在 230001–239999；全部 INSERT IGNORE，可重复执行。
-- 业务日期以 2026-10-25 为锚点（SAMPLE 组当前锚点），末尾逐行登记 t_demo_sample_row，重启时随样例平移。
SET @anchor = DATE('2026-10-25');

-- 1. 员工身份：李明（10003）加入西岭鸡场为 STAFF，负责 1 号育雏舍、2 号鸡舍。
--    1002 已有刘芳（5005，OWNER）与王建国（5006，MANAGER）；同一栏舍可配置多名成员。
INSERT IGNORE INTO t_farm_member
(id,farm_id,user_id,role,status,active_flag,join_type,invite_id,joined_at,left_at,removed_by,last_switch_at,
 version,created_by,updated_by,created_at,updated_at)
VALUES(5008,1002,10003,'STAFF','ACTIVE',1,'INVITE',NULL,'2026-10-20 09:00:00',NULL,NULL,NULL,
 0,10002,10002,'2026-10-20 09:00:00','2026-10-20 09:00:00');

INSERT IGNORE INTO t_member_pen(id,farm_id,member_id,user_id,pen_id,created_by,created_at)
VALUES(230511,1002,5008,10003,2201,10002,'2026-10-20 09:05:00'),
      (230512,1002,5008,10003,2202,10002,'2026-10-20 09:05:00');

-- 2. AI 会话：2 条会话共 5 轮问答，绑定在养批次 3201/3202，App/Web 的列表与详情都有内容。
--    查询方为 10001 王建国（1002 的 MANAGER，也是 App 西岭演示身份），与东山 1001 样例一致。
INSERT IGNORE INTO t_ai_session
(id,farm_id,user_id,title,topic,batch_id,batch_snapshot_json,batch_link_state,round_count,context_truncated,
 last_asked_at,has_correction,has_epidemic_alert,created_by,created_at,updated_at)
VALUES
(230001,1002,10001,'2 号鸡舍肉鸡咳嗽采食下降怎么办','DISEASE',3201,
 '{"batch_id":3201,"batch_no":"ZM2610-026","category_name":"肉鸡","stage_name":"育成期","breed_name":"白羽肉鸡","pen_id":2202,"pen_name":"2 号鸡舍","day_age":18,"current_count":9300,"count_unit_text":"只"}',
 'VALID',3,0,'2026-10-23 15:42:00',0,0,10001,'2026-10-23 15:30:00','2026-10-23 15:42:00'),
(230002,1002,10001,'雏鸡免疫前后需要注意什么','IMMUNE',3202,
 '{"batch_id":3202,"batch_no":"ZM2610-027","category_name":"肉鸡","stage_name":"育成期","breed_name":"白羽肉鸡","pen_id":2201,"pen_name":"1 号育雏舍","day_age":18,"current_count":9300,"count_unit_text":"只"}',
 'VALID',2,0,'2026-10-24 08:42:00',0,0,10001,'2026-10-24 08:30:00','2026-10-24 08:42:00');

INSERT IGNORE INTO t_ai_message
(id,session_id,farm_id,user_id,round_no,client_msg_id,client_type,question_text,image_file_ids,batch_id,batch_no,
 context_course_id,context_course_title,context_knowledge_id,status,answer_type,answer_text,error_code,error_message,
 retry_count,rating,rated_at,handle_status,model_name,latency_ms,asked_at,started_at,answered_at,
 created_by,created_at,updated_at)
VALUES
(230011,230001,1002,10001,1,'sample-west-ai-230001-1','APP_ANDROID',
 '2 号鸡舍这两天咳嗽的鸡多了，采食量也下降了，可能是什么原因？',NULL,3201,'ZM2610-026',NULL,NULL,NULL,
 'ANSWERED','NORMAL',
 '18 日龄肉鸡出现咳嗽和采食下降，先排查舍内氨气味、粉尘和昼夜温差，再检查饮水线是否堵塞。\n\n建议记录咳嗽比例和每顿剩料；若咳嗽比例超过 5%，或出现张口呼吸、甩鼻，先把明显病鸡隔离并请兽医到场检查。',
 NULL,NULL,0,NULL,NULL,NULL,'qwen3.8-max',18000,
 '2026-10-23 15:30:00','2026-10-23 15:30:00','2026-10-23 15:30:18',10001,
 '2026-10-23 15:30:00','2026-10-23 15:30:18'),
(230012,230001,1002,10001,2,'sample-west-ai-230001-2','APP_ANDROID',
 '现在需要马上用抗生素吗？',NULL,3201,'ZM2610-026',NULL,NULL,NULL,
 'ANSWERED','NORMAL',
 '不建议盲目用抗生素。先加强通风、降低粉尘并保证饮水，连续观察 1 至 2 天；确需用药时按兽医建议选择批准药品，同时登记用药和休药期。',
 NULL,NULL,0,NULL,NULL,NULL,'qwen3.8-max',15000,
 '2026-10-23 15:36:00','2026-10-23 15:36:00','2026-10-23 15:36:15',10001,
 '2026-10-23 15:36:00','2026-10-23 15:36:15'),
(230013,230001,1002,10001,3,'sample-west-ai-230001-3','APP_ANDROID',
 '免疫前后还能正常通风吗？',NULL,3201,'ZM2610-026',NULL,NULL,NULL,
 'ANSWERED','NORMAL',
 '可以正常通风，但免疫前后 3 天避免冷风直吹和转群应激，保持最小通风量；接种后观察精神、采食和呼吸道反应，并记录疫苗批号与有效期。',
 NULL,NULL,0,NULL,NULL,NULL,'qwen3.8-max',16000,
 '2026-10-23 15:42:00','2026-10-23 15:42:00','2026-10-23 15:42:16',10001,
 '2026-10-23 15:42:00','2026-10-23 15:42:16'),
(230021,230002,1002,10001,1,'sample-west-ai-230002-1','APP_ANDROID',
 '雏鸡免疫前后需要注意什么？',NULL,3202,'ZM2610-027',NULL,NULL,NULL,
 'ANSWERED','NORMAL',
 '免疫前确认鸡群健康、无呼吸道症状，免疫前后 3 天避免转群、断喙和带鸡消毒；饮水免疫前按说明停水，确保每只鸡都能喝到疫苗水。',
 NULL,NULL,0,NULL,NULL,NULL,'qwen3.8-max',17000,
 '2026-10-24 08:30:00','2026-10-24 08:30:00','2026-10-24 08:30:17',10001,
 '2026-10-24 08:30:00','2026-10-24 08:30:17'),
(230022,230002,1002,10001,2,'sample-west-ai-230002-2','APP_ANDROID',
 '饮水免疫的水量怎么控制？',NULL,3202,'ZM2610-027',NULL,NULL,NULL,
 'ANSWERED','NORMAL',
 '按鸡群 1 小时能喝完的水量计算，通常每只准备 10 至 20 毫升；水中不加消毒剂，可加脱脂奶粉保护疫苗，喝完后再恢复正常供水。',
 NULL,NULL,0,NULL,NULL,NULL,'qwen3.8-max',14000,
 '2026-10-24 08:42:00','2026-10-24 08:42:00','2026-10-24 08:42:14',10001,
 '2026-10-24 08:42:00','2026-10-24 08:42:14');

-- 3. 异常上报：3201 批次 10-23 的呼吸道异常，AI 诊断暂不可用（与 1001 样例 6003 同为 PENDING/UNAVAILABLE）。
INSERT IGNORE INTO t_abnormal_report
(id,farm_id,pen_id,batch_id,inspection_id,source,client_request_id,reporter_id,reported_at,report_date,
 sick_count,stock_snapshot,sick_rate,description,same_day_seq,status,ai_state,auto_retry_used,unavailable_since,
 current_diagnosis_id,urgency,is_major_epidemic,top_cause_name,notified_names,diagnosed_at,handled_at,closed_at,
 version,deleted,created_by,updated_by,created_at,updated_at)
VALUES(230101,1002,2202,3201,NULL,'DIRECT','sample-west-report-230101',10003,
 '2026-10-23 09:20:00','2026-10-23',93,9300,0.0100,
 '93 只鸡咳嗽、羽毛蓬乱，采食量下降，部分鸡只张口呼吸。',1,'PENDING','UNAVAILABLE',0,
 '2026-10-23 09:21:00',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,1,0,10003,10003,
 '2026-10-23 09:20:00','2026-10-23 09:21:00');

INSERT IGNORE INTO t_abnormal_report_symptom
(id,farm_id,report_id,symptom_id,symptom_name,sort_no,created_by,created_at,updated_at)
VALUES(230121,1002,230101,413,'减料',1,10003,'2026-10-23 09:20:00','2026-10-23 09:20:00'),
      (230122,1002,230101,417,'咳嗽',5,10003,'2026-10-23 09:20:00','2026-10-23 09:20:00'),
      (230123,1002,230101,422,'羽毛蓬乱',10,10003,'2026-10-23 09:20:00','2026-10-23 09:20:00');

-- 4. 饲料盘点：10-22 盘 210001 肉鸡全价料，账面 9070 千克、实盘 9050 千克，盘亏 20 千克 × 3.60。
--    t_stocktake_item 只有 feed_id，没有兽药明细列，因此按现有样例只盘饲料。
INSERT IGNORE INTO t_stocktake
(id,farm_id,stocktake_no,stocktake_date,item_count,gain_count,loss_count,gain_amount,loss_amount,
 client_request_id,version,created_by,updated_by,created_at,updated_at)
VALUES(230201,1002,'PD2610220001','2026-10-22',1,0,1,0.00,72.00,
 'sample-west-stocktake-230201',1,10002,10002,'2026-10-22 18:00:00','2026-10-22 18:00:00');

INSERT IGNORE INTO t_stocktake_item
(id,stocktake_id,farm_id,feed_id,book_qty,actual_qty,diff_qty,result,avg_price,diff_amount,flow_id,
 created_at,updated_at)
VALUES(230211,230201,1002,210001,9070.000,9050.000,-20.000,'LOSS',3.6000,-72.00,NULL,
 '2026-10-22 18:00:00','2026-10-22 18:00:00');

-- 5. 免疫计划：3201 10-25、3202 10-28 各补一条二免，复用 1002 现有禽用活疫苗目录与药品档案。
--    source=MANUAL 与现有 210131/210132 一致，不需要 t_immune_template_item 关联。
INSERT IGNORE INTO t_immune_plan
(id,farm_id,target_type,batch_id,pen_id,source,pre_entry,template_id,template_version,template_item_id,
 catalog_id,drug_id,vaccine_name,immune_round,plan_mode,plan_age_day,original_plan_date,plan_date,due_time,
 dose,dose_unit,method_code,status,postponed,manual_done,missed_notified,version,created_by,created_at,updated_at)
VALUES
(230301,1002,'BATCH',3201,NULL,'MANUAL',0,NULL,NULL,NULL,210101,210111,'禽用活疫苗（演示）','二免','BY_DATE',NULL,
 '2026-10-25','2026-10-25','18:00:00',1.000,'DOSE_PER_HEAD','WATER','PENDING',0,0,0,0,10002,
 '2026-10-20 09:10:00','2026-10-20 09:10:00'),
(230302,1002,'BATCH',3202,NULL,'MANUAL',0,NULL,NULL,NULL,210101,210111,'禽用活疫苗（演示）','二免','BY_DATE',NULL,
 '2026-10-28','2026-10-28','18:00:00',1.000,'DOSE_PER_HEAD','WATER','PENDING',0,0,0,0,10002,
 '2026-10-20 09:10:00','2026-10-20 09:10:00');

-- 6. 样例日期白名单：只登记本迁移新增且带演示日期的业务行，供启动时按 SAMPLE 锚点平移。
--    员工与栏舍关系（t_farm_member/t_member_pen）没有演示日期，不登记。
INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
VALUES
('t_ai_session',230001,'SAMPLE',@anchor),
('t_ai_session',230002,'SAMPLE',@anchor),
('t_ai_message',230011,'SAMPLE',@anchor),
('t_ai_message',230012,'SAMPLE',@anchor),
('t_ai_message',230013,'SAMPLE',@anchor),
('t_ai_message',230021,'SAMPLE',@anchor),
('t_ai_message',230022,'SAMPLE',@anchor),
('t_abnormal_report',230101,'SAMPLE',@anchor),
('t_abnormal_report_symptom',230121,'SAMPLE',@anchor),
('t_abnormal_report_symptom',230122,'SAMPLE',@anchor),
('t_abnormal_report_symptom',230123,'SAMPLE',@anchor),
('t_stocktake',230201,'SAMPLE',@anchor),
('t_stocktake_item',230211,'SAMPLE',@anchor),
('t_immune_plan',230301,'SAMPLE',@anchor),
('t_immune_plan',230302,'SAMPLE',@anchor);
