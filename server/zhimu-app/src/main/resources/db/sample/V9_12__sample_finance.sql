-- 模块10 示例数据（dev / test 的 db/sample，V9_01、V9_02、V9_04 之后执行；不建外键，允许单独执行）
-- 与 App Mock（module_finance/src/debug/assets/mock/finance）对齐；批次/栏舍由 V9_04 提供：
--   3001 ZM2610-031（3 号育肥舍 200）、3002 ZM2610-019（4 号育肥舍 150）、
--   3003 ZM2610-045（1 号保育舍 320）、3004 ZM2609-038（2 号/1 号保育舍 200）、
--   3010 ZM2609-012（5 号育肥舍，2026-10-13 结束，98 头出栏）。
-- Mock 中的批次 3009 统一落到 V9_04 已结束批次 3010（同为 ZM2609-012），保证核算与报表可关联。
-- 示例数据的“今天”为 2026-10-19（演示时钟，北京时区）。

-- 收支一级分类（全局）
INSERT INTO t_finance_category (id, direction, code, name, icon, sort_no, status, auto_source, mark_only, is_fallback,
                                disabled_at, disable_reason, version, created_by, updated_by, created_at, updated_at)
VALUES (101, 'EXPENSE', 'FEED', '饲料采购', 'inventory_2', 1, 'ENABLED', 'FEED_INBOUND', 1, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (102, 'EXPENSE', 'DRUG', '兽药疫苗', 'vaccines', 2, 'ENABLED', 'DRUG_INBOUND', 1, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (103, 'EXPENSE', 'UTILITY', '水电', 'bolt', 3, 'ENABLED', NULL, 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (104, 'EXPENSE', 'LABOR', '人工', 'engineering', 4, 'ENABLED', NULL, 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (105, 'EXPENSE', 'SEEDLING', '引种', 'add_box', 5, 'ENABLED', 'BATCH_ENTRY', 1, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (106, 'EXPENSE', 'REPAIR', '设备维修', 'build', 6, 'ENABLED', NULL, 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (107, 'EXPENSE', 'OTHER', '其他', 'category', 7, 'ENABLED', NULL, 0, 1, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (201, 'INCOME', 'SALE', '出栏销售', 'payments', 1, 'ENABLED', 'SALE_RECEIPT', 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (202, 'INCOME', 'BYPRODUCT', '副产品', 'storefront', 2, 'ENABLED', NULL, 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (203, 'INCOME', 'SUBSIDY', '补贴', 'account_balance', 3, 'ENABLED', NULL, 0, 0, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (204, 'INCOME', 'OTHER_INCOME', '其他', 'category', 4, 'ENABLED', NULL, 0, 1, NULL, NULL, 1, 0, 0,
        '2026-09-01 00:00:00', '2026-09-01 00:00:00');

-- 东山猪场支出子类
INSERT INTO t_expense_subcategory (id, farm_id, category_id, name, deleted, deleted_at, created_by, created_at, updated_at)
VALUES (1011, 1001, 101, '全价料', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1012, 1001, 101, '玉米', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1013, 1001, 101, '豆粕', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1031, 1001, 103, '电费', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1032, 1001, 103, '水费', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1041, 1001, 104, '临时工', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1061, 1001, 106, '料线电机', 0, NULL, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

-- 出栏单：9102 部分收款（批次 3010 ZM2609-012）、9106 未收款（批次 3002 ZM2610-019）、9105 已作废
INSERT INTO t_sale_order (id, farm_id, batch_id, sale_no, pen_id, pen_allocations, category_id, breed_id, sale_date,
                          quantity, total_weight, avg_weight, pricing_method, unit_price, ref_unit_price, sale_amount,
                          buyer_name, due_date, effective_due_date, received_amount, unpaid_amount, receipt_count,
                          payment_status, paid_off_date, pending_change_request_id, ended_batch, content_version,
                          void_reason, void_source, voided_at, voided_by, client_request_id, version,
                          created_by, updated_by, created_at, updated_at)
VALUES (9102, 1001, 3010, 'CL26101302', 2005,
        '[{"pen_id": 2005, "pen_name": "5 号育肥舍", "quantity": 98}]',
        101, 1101, '2026-10-13', 98, 11760.00, 120.0, 'BY_WEIGHT', 16.00, 15.60, 188160.00,
        '赤峰鑫源肉联厂', '2026-10-20', '2026-10-20', 100000.00, 88160.00, 2, 'PARTIAL', NULL, NULL, 1, 3,
        NULL, NULL, NULL, NULL, 'sample-sale-9102', 5, 10001, 10001, '2026-10-13 16:40:00', '2026-10-16 15:00:00'),
       (9106, 1001, 3002, 'CL26101906', 2004,
        '[{"pen_id": 2004, "pen_name": "4 号育肥舍", "quantity": 60}]',
        101, 1101, '2026-10-19', 60, 7020.00, 117.0, 'BY_WEIGHT', 15.60, 15.60, 109512.00,
        '赤峰鑫源肉联厂', '2026-10-26', '2026-10-26', 0.00, 109512.00, 0, 'UNPAID', NULL, NULL, 0, 0,
        NULL, NULL, NULL, NULL, 'sample-sale-9106', 0, 10001, 10001, '2026-10-19 16:40:00', '2026-10-19 16:40:00'),
       (9105, 1001, 3011, 'CL26092603', 2005,
        '[{"pen_id": 2005, "pen_name": "5 号育肥舍", "quantity": 120}]',
        101, 1101, '2026-09-26', 120, 14160.00, 118.0, 'BY_WEIGHT', 15.20, 15.00, 215232.00,
        '宁城宏达生猪收购站', '2026-10-03', '2026-10-03', 0.00, 0.00, 0, 'VOIDED', NULL, NULL, 0, 1,
        '数量登记错误，已重新登记', 'DIRECT', '2026-09-26 15:10:00', 10001, 'sample-sale-9105', 2,
        10001, 10001, '2026-09-26 09:00:00', '2026-09-26 15:10:00');

-- 出栏收款：9102 两笔，合计 100000
INSERT INTO t_sale_receipt (id, farm_id, sale_order_id, batch_id, amount, receipt_date, payment_method,
                            finance_record_id, status, voided_at, client_request_id, created_by, created_at, updated_at)
VALUES (5201, 1001, 9102, 3010, 60000.00, '2026-10-13', 'BANK_TRANSFER', 8208, 'NORMAL', NULL, 'sample-receipt-5201',
        10001, '2026-10-13 16:45:00', '2026-10-13 16:45:00'),
       (5202, 1001, 9102, 3010, 40000.00, '2026-10-16', 'WECHAT', 8207, 'NORMAL', NULL, 'sample-receipt-5202',
        10001, '2026-10-16 15:00:00', '2026-10-16 15:00:00');

-- 收支流水（与 Mock 8201–8209）
INSERT INTO t_finance_record (id, farm_id, record_type, source_type, source_id, source_no, sale_order_id, category_id,
                              subcategory_id, amount, record_date, batch_id, note, detail_text, voucher_count, status,
                              void_source, voided_at, voided_by, client_request_id, version, created_by, updated_by,
                              created_at, updated_at)
VALUES (8201, 1001, 'EXPENSE', 'FEED_INBOUND', 5101, 'RK2610190003', NULL, 101, 1011, 3200.00, '2026-10-19', NULL, NULL,
        '育肥全价料 1,000 千克', 0, 'NORMAL', NULL, NULL, NULL, NULL, 0, 0, 0, '2026-10-19 08:40:00', '2026-10-19 08:40:00'),
       (8202, 1001, 'EXPENSE', 'MANUAL', NULL, NULL, NULL, 104, 1041, 600.00, '2026-10-19', 3001, NULL, NULL, 0, 'NORMAL',
        NULL, NULL, NULL, 'sample-record-8202', 0, 10001, 10001, '2026-10-19 08:10:00', '2026-10-19 08:10:00'),
       (8203, 1001, 'INCOME', 'MANUAL', NULL, NULL, NULL, 203, NULL, 6000.00, '2026-10-18', NULL, '能繁母猪补贴', NULL, 1,
        'NORMAL', NULL, NULL, NULL, 'sample-record-8203', 0, 10001, 10001, '2026-10-18 16:30:00', '2026-10-18 16:30:00'),
       (8204, 1001, 'EXPENSE', 'DRUG_INBOUND', 6101, 'RK2610180002', NULL, 102, NULL, 1680.00, '2026-10-18', NULL, NULL,
        '猪瘟活疫苗 2025113 · 60 瓶', 0, 'NORMAL', NULL, NULL, NULL, NULL, 0, 0, 0, '2026-10-18 10:05:00', '2026-10-18 10:05:00'),
       (8205, 1001, 'EXPENSE', 'MANUAL', NULL, NULL, NULL, 103, 1031, 860.00, '2026-10-17', NULL, NULL, NULL, 0, 'VOID',
        'MANUAL', '2026-10-18 09:00:00', 10001, 'sample-record-8205', 0, 10001, 10001, '2026-10-17 18:00:00', '2026-10-18 09:00:00'),
       (8206, 1001, 'EXPENSE', 'MANUAL', NULL, NULL, NULL, 106, 1061, 1200.00, '2026-10-17', NULL, NULL, NULL, 0, 'NORMAL',
        NULL, NULL, NULL, 'sample-record-8206', 0, 10001, 10001, '2026-10-17 11:20:00', '2026-10-17 11:20:00'),
       (8207, 1001, 'INCOME', 'SALE_RECEIPT', 5202, 'CL26101302', 9102, 201, NULL, 40000.00, '2026-10-16', 3010, NULL,
        'ZM2609-012 · 微信', 0, 'NORMAL', NULL, NULL, NULL, NULL, 0, 0, 0, '2026-10-16 15:00:00', '2026-10-16 15:00:00'),
       (8208, 1001, 'INCOME', 'SALE_RECEIPT', 5201, 'CL26101302', 9102, 201, NULL, 60000.00, '2026-10-13', 3010, NULL,
        'ZM2609-012 · 银行转账', 0, 'NORMAL', NULL, NULL, NULL, NULL, 0, 0, 0, '2026-10-13 16:45:00', '2026-10-13 16:45:00'),
       (8209, 1001, 'EXPENSE', 'MANUAL', NULL, NULL, NULL, 107, NULL, 600.00, '2026-10-17', 3010, NULL, '运输费', 0, 'NORMAL',
        NULL, NULL, NULL, 'sample-record-8209', 0, 10001, 10001, '2026-10-17 09:00:00', '2026-10-17 09:00:00');

-- 批次核算：3001 在养 RUNNING、3002 在养 RUNNING、3010 已锁定 LOCKED（ZM2609-012）
INSERT INTO t_batch_settlement (id, farm_id, batch_id, status, settled_at, sale_income, cost_feed, feed_kg, cost_intro,
                                intro_count, intro_unit_price, cost_drug, cost_other, cost_total, profit, sold_count,
                                cost_per_head, profit_per_head, intro_weight_kg, sold_weight_kg, net_gain_kg, fcr,
                                calc_at, version, created_at, updated_at)
VALUES (6001, 1001, 3001, 'RUNNING', NULL, 0.00, 92000.00, 21624.00, 41000.00, 205, 200.00, 8200.00, 4800.00,
        146000.00, -146000.00, 0, 730.00, -730.00, 9225.00, 0.00, 8160.00, 2.65, '2026-10-19 06:00:00', 7,
        '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       (6002, 1001, 3002, 'RUNNING', NULL, 0.00, 62000.00, 14500.00, 7600.00, 152, 50.00, 5200.00, 3200.00,
        78000.00, -78000.00, 0, 520.00, -520.00, 5806.40, 0.00, 5460.00, 2.66, '2026-10-19 06:00:00', 2,
        '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       (6009, 1001, 3010, 'LOCKED', '2026-10-13 16:40:00', 188160.00, 120000.00, 25930.00, 50000.00, 100, 500.00,
        8000.00, 7000.00, 185000.00, 3160.00, 98, 1887.76, 32.24, 2500.00, 11760.00, 9260.00, 2.80,
        '2026-10-13 16:40:00', 2, '2026-10-13 16:40:00', '2026-10-17 10:00:00');

-- 补录待确认：运输费 600 元（对应流水 8209）
INSERT INTO t_batch_settlement_supplement (id, farm_id, batch_id, settlement_id, source_type, source_id, change_action,
                                           biz_date, title, cost_group, amount, qty_delta, change_request_id, status,
                                           confirmed_by, confirmed_at, cancel_reason, created_at, updated_at)
VALUES (401, 1001, 3010, 6009, 'FINANCE_RECORD', 8209, 'ADD', '2026-10-17', '运输费', 'OTHER', 600.00, NULL, NULL,
        'PENDING', NULL, NULL, NULL, '2026-10-17 09:00:00', '2026-10-17 09:00:00');

-- AI 出栏建议：7301 休药期内规则生成（与 Mock advice_3001 对齐）
INSERT INTO t_sale_advice (id, farm_id, batch_id, status, gen_mode, conclusion, window_start, window_end, sellable_date,
                           in_withdrawal, withdrawal_days_left, reason_withdrawal, reason_age, reason_weight, reason_price,
                           basis_insufficient, basis_note, input_snapshot_json, ai_model, prompt_version, error_message,
                           started_at, generated_at, created_by, created_at, updated_at)
VALUES (7301, 1001, 3001, 'SUCCESS', 'RULE', 'NOT_SELLABLE', '2026-10-29', '2026-11-05', '2026-10-29', 1, 10,
        '氟苯尼考粉末次用药 10月9日，休药期 20 天，10月29日起可出栏。', '96 日龄，存栏 200 头。',
        '10月19日称重，平均体重比同日龄标准低 12.5%。', '近 30 天每千克 14.40—15.60 元，平均 14.73 元；今天涨 4.0%。',
        0, NULL, '{"batch_id":3001,"sellable_date":"2026-10-29","current_count":200,"avg_weight":45.0}',
        'rule', 'v1', NULL, '2026-10-19 09:28:00', '2026-10-19 09:28:00', 10001,
        '2026-10-19 09:28:00', '2026-10-19 09:28:00'),
       (7302, 1001, 3002, 'SUCCESS', 'AI', 'KEEP_FEEDING', '2026-11-02', '2026-11-09', NULL, 0, NULL,
        NULL, '82 日龄，存栏 150 头。', '10月18日称重，平均体重 38.2 千克。', '近 30 天每千克 14.40—15.60 元。',
        0, NULL, '{"batch_id":3002,"current_count":150,"avg_weight":38.2}', 'qwen-plus', 'v1', NULL,
        '2026-10-19 09:30:00', '2026-10-19 09:30:00', 10001, '2026-10-19 09:30:00', '2026-10-19 09:30:00'),
       (7303, 1001, 3004, 'FAILED', 'AI', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, 0, NULL,
        '{"batch_id":3004}', 'qwen-plus', 'v1', '生成超时', '2026-10-19 09:32:00', NULL, 10001,
        '2026-10-19 09:32:00', '2026-10-19 09:33:00');

INSERT INTO t_sale_advice_risk (id, advice_id, sort_no, content, created_at)
VALUES (8101, 7301, 1, '单日涨幅较大，休药期满后价格可能回落。', '2026-10-19 09:28:00'),
       (8102, 7301, 2, '体重偏低，过早出栏会降低单头收入。', '2026-10-19 09:28:00');

-- 报表期次（每周日 02:00 生成；6001/6003/6005 为 Mock 中的示例期次）
INSERT INTO t_biz_report (id, farm_id, report_type, period_start, period_end, generated_at, notified, created_at, updated_at)
VALUES (6001, 1001, 'WEEKLY', '2026-10-12', '2026-10-18', '2026-10-19 02:00:00', 1, '2026-10-19 02:00:00', '2026-10-19 02:00:00'),
       (6002, 1001, 'WEEKLY', '2026-10-05', '2026-10-11', '2026-10-12 02:00:00', 1, '2026-10-12 02:00:00', '2026-10-12 02:00:00'),
       (6003, 1001, 'WEEKLY', '2026-09-28', '2026-10-04', '2026-10-05 02:00:00', 1, '2026-10-05 02:00:00', '2026-10-05 02:00:00'),
       (6004, 1001, 'MONTHLY', '2026-08-01', '2026-08-31', '2026-09-01 02:00:00', 1, '2026-09-01 02:00:00', '2026-09-01 02:00:00'),
       (6005, 1001, 'MONTHLY', '2026-09-01', '2026-09-30', '2026-10-01 02:00:00', 1, '2026-10-01 02:00:00', '2026-10-01 02:00:00');

-- 报表 AI 摘要（视图键：FARM/TO_DATE/2026-10-01/2026-10-19/FARM）
INSERT INTO t_biz_report_summary (id, view_key, scope_type, farm_id, user_id, report_type, period_start, period_end,
                                  range_type, range_ref_id, highlight, problem, suggestion, data_hash, status,
                                  error_message, ai_model, prompt_version, started_at, generated_at, created_by,
                                  created_at, updated_at)
VALUES (7001, 'FARM|TO_DATE|2026-10-01|2026-10-19|FARM|0|1001', 'FARM', 1001, NULL, 'TO_DATE', '2026-10-01', '2026-10-19',
        'FARM', NULL, '本期 2 个批次结束，合计盈利 17,008 元；结余 87,600 元，较上月同期增长 32.1%。',
        '死亡率 2.50%，比上月同期高 0.4 个百分点，期间死淘 30 头。', '核查死淘较多栏舍的通风、饲喂和巡检记录。',
        '3d4f0a2e8a9b6e1c6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e', 'SUCCESS', NULL, 'qwen-plus', 'v1',
        '2026-10-19 06:10:00', '2026-10-19 06:10:00', 0, '2026-10-19 06:10:00', '2026-10-19 06:10:00');

-- 行情筛选发布配置：3 个省份、2 个品类、出栏价/种苗价
INSERT INTO t_market_price_config (id, province_code, province_name, category_id, breed_id, price_type, status, sort_no,
                                   created_at, updated_at)
VALUES (1, '150000', '内蒙古', 101, 0, 'SLAUGHTER', 'ENABLED', 1, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (2, '150000', '内蒙古', 101, 1101, 'SLAUGHTER', 'ENABLED', 2, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (3, '150000', '内蒙古', 101, 0, 'SEEDLING', 'ENABLED', 3, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (4, '130000', '河北', 101, 0, 'SLAUGHTER', 'ENABLED', 4, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (5, '210000', '辽宁', 101, 0, 'SLAUGHTER', 'ENABLED', 5, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (6, '150000', '内蒙古', 201, 0, 'SLAUGHTER', 'ENABLED', 6, '2026-09-01 00:00:00', '2026-09-01 00:00:00'),
       (7, '150000', '内蒙古', 201, 2101, 'SLAUGHTER', 'ENABLED', 7, '2026-09-01 00:00:00', '2026-09-01 00:00:00');

-- 行情每日价格：内蒙古育肥猪出栏价 2026-09-20—10-19（10-01 无价格，折线断开；10-18 修正过）
INSERT INTO t_market_price (id, province_code, province_name, category_id, breed_id, price_type, price_date, price,
                            price_unit, price_unit_text, corrected, corrected_at, status, source, created_at, updated_at)
VALUES (5001, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-20', 14.70, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-20 06:00:00', '2026-09-20 06:00:00'),
       (5002, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-21', 14.62, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-21 06:00:00', '2026-09-21 06:00:00'),
       (5003, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-22', 14.55, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-22 06:00:00', '2026-09-22 06:00:00'),
       (5004, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-23', 14.50, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-23 06:00:00', '2026-09-23 06:00:00'),
       (5005, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-24', 14.45, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-24 06:00:00', '2026-09-24 06:00:00'),
       (5006, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-25', 14.40, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-25 06:00:00', '2026-09-25 06:00:00'),
       (5007, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-26', 14.48, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-26 06:00:00', '2026-09-26 06:00:00'),
       (5008, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-27', 14.52, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-27 06:00:00', '2026-09-27 06:00:00'),
       (5009, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-28', 14.55, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-28 06:00:00', '2026-09-28 06:00:00'),
       (5010, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-29', 14.58, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-29 06:00:00', '2026-09-29 06:00:00'),
       (5011, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-09-30', 14.60, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-09-30 06:00:00', '2026-09-30 06:00:00'),
       (5012, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-02', 14.62, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-02 06:00:00', '2026-10-02 06:00:00'),
       (5013, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-03', 14.64, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-03 06:00:00', '2026-10-03 06:00:00'),
       (5014, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-04', 14.66, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-04 06:00:00', '2026-10-04 06:00:00'),
       (5015, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-05', 14.68, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-05 06:00:00', '2026-10-05 06:00:00'),
       (5016, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-06', 14.70, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-06 06:00:00', '2026-10-06 06:00:00'),
       (5017, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-07', 14.72, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-07 06:00:00', '2026-10-07 06:00:00'),
       (5018, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-08', 14.75, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-08 06:00:00', '2026-10-08 06:00:00'),
       (5019, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-09', 14.78, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-09 06:00:00', '2026-10-09 06:00:00'),
       (5020, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-10', 14.80, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-10 06:00:00', '2026-10-10 06:00:00'),
       (5021, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-11', 14.85, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-11 06:00:00', '2026-10-11 06:00:00'),
       (5022, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-12', 14.90, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-12 06:00:00', '2026-10-12 06:00:00'),
       (5023, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-13', 14.86, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-13 06:00:00', '2026-10-13 06:00:00'),
       (5024, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-14', 14.88, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-14 06:00:00', '2026-10-14 06:00:00'),
       (5025, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-15', 14.90, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-15 06:00:00', '2026-10-15 06:00:00'),
       (5026, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-16', 14.90, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-16 06:00:00', '2026-10-16 06:00:00'),
       (5027, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-17', 14.90, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-17 06:00:00', '2026-10-17 06:00:00'),
       (5028, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-18', 15.00, 'YUAN_PER_KG', '元/千克', 1, '2026-10-19 08:30:00', 'PUBLISHED', 'SEED', '2026-10-18 06:00:00', '2026-10-19 08:30:00'),
       (5029, '150000', '内蒙古', 101, 0, 'SLAUGHTER', '2026-10-19', 15.60, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       -- 种苗价（育肥猪）
       (5101, '150000', '内蒙古', 101, 0, 'SEEDLING', '2026-10-17', 420.00, 'YUAN_PER_HEAD', '元/头', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-17 06:00:00', '2026-10-17 06:00:00'),
       (5102, '150000', '内蒙古', 101, 0, 'SEEDLING', '2026-10-18', 425.00, 'YUAN_PER_HEAD', '元/头', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-18 06:00:00', '2026-10-18 06:00:00'),
       (5103, '150000', '内蒙古', 101, 0, 'SEEDLING', '2026-10-19', 430.00, 'YUAN_PER_HEAD', '元/头', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       -- 河北、辽宁出栏价（供切换省份）
       (5201, '130000', '河北', 101, 0, 'SLAUGHTER', '2026-10-18', 15.10, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-18 06:00:00', '2026-10-18 06:00:00'),
       (5202, '130000', '河北', 101, 0, 'SLAUGHTER', '2026-10-19', 15.25, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       (5301, '210000', '辽宁', 101, 0, 'SLAUGHTER', '2026-10-18', 15.35, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-18 06:00:00', '2026-10-18 06:00:00'),
       (5302, '210000', '辽宁', 101, 0, 'SLAUGHTER', '2026-10-19', 15.42, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-19 06:00:00', '2026-10-19 06:00:00'),
       -- 肉鸡出栏价
       (5401, '150000', '内蒙古', 201, 0, 'SLAUGHTER', '2026-10-18', 4.35, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-18 06:00:00', '2026-10-18 06:00:00'),
       (5402, '150000', '内蒙古', 201, 0, 'SLAUGHTER', '2026-10-19', 4.40, 'YUAN_PER_KG', '元/千克', 0, NULL, 'PUBLISHED', 'SEED', '2026-10-19 06:00:00', '2026-10-19 06:00:00');

-- 导出记录（示例，文件由运行期导出服务写入）
-- 不插 t_biz_report_export：file_id 指向的文件不存在会造成下载失败，首次导出时自然产生。
