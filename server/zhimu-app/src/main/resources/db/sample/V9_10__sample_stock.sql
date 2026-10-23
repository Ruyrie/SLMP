-- 模块09 第 3 批示例数据（dev / test 的 db/sample，V9_01、V9_02、V9_04 之后执行；不建外键，允许单独执行）
-- 与 App Mock（module_stock/src/debug/assets/mock/stock）对齐，养殖场 1001 东山猪场：
--   饲料 5101 育肥全价料（500 千克、预警）、5102 保育料（1,850 千克）、5103 教槽料（0，只在入库下拉里）；
--   药品 5001 猪瘟活疫苗、5003 口蹄疫灭活疫苗、5201 阿莫西林、5202 氟苯尼考、5301 聚维酮碘；
--   批号 5201（已停用）、6001/6002、6101、6103（已过期）、6111、6301（临期），共 7 个、ATTENTION 3 / NORMAL 4；
--   入库 6201/6202/6203/6204/6205/6207/6209/6210/6211、出库 6501/6511/6512、报损 6401、盘点 6290/6301；
--   流水 9201–9302（饲料 9 条）与 9500–9512（药品 8 条），与 mock 的 flow_id 一致；
--   往来单位、临期天数设置。
-- 说明：任务书写的“饲料 3101+、批号 7001+”与 App mock 冲突，按“App 契约优先”处理：
--   饲料沿用 mock 的 5101–5103（t_feed 自增从 5300 起），批号沿用 mock 的 5201–6301（t_drug_lot 自增从 7001 起）。
--   6402 报废单（App loss_6402.json）未落库：它会与 mock 中批号 5201 仍有 300 头份结存冲突；报废流程由接口演示。
-- 示例数据的“今天”为 2026-10-19（演示时钟，北京时区）；结存与流水为演示快照，不要求逐笔勾稽。
-- 不修改 V9_04 的栏舍与批次（2001–2007、3001–3004），只引用。

-- 饲料档案（结存、均价、近 7 天领用与预警缓存与 mock 一致）
INSERT INTO t_feed (id, farm_id, name, feed_type_code, spec, unit, pack_unit, pack_size, category_id, min_stock,
                    warn_days, stock_qty, stock_amount, avg_price, usage_7d_qty, usage_days, daily_use_qty,
                    remaining_basis, remaining_days, stat_date, is_low_stock, low_stock_reason, low_stock_at,
                    is_unit_locked, version, edit_version, deleted, created_by, updated_by, created_at, updated_at)
VALUES (5101, 1001, '育肥全价料', 'COMPLETE', '40 千克/袋', 'KG', '袋', 40.000, 101, 800.00,
        7, 500.000, 1600.00, 3.2000, 3360.000, 7, 480.00,
        'USAGE_7D', 1, '2026-10-19', 1, 'BELOW_DAYS', '2026-10-19 00:05:12',
        1, 12, 3, 0, 10001, 10001, '2026-03-05 09:00:00', '2026-10-10 09:15:00'),
       (5102, 1001, '保育料', 'NURSERY', '20 千克/袋', 'KG', '袋', 20.000, NULL, NULL,
        7, 1850.000, 7770.00, 4.2000, 1050.000, 7, 150.00,
        'USAGE_7D', 12, '2026-10-19', 0, NULL, NULL,
        1, 8, 1, 0, 10001, 10002, '2026-03-20 10:00:00', '2026-09-02 10:00:00'),
       (5103, 1001, '哺乳仔猪教槽料', 'NURSERY', '10 千克/袋', 'KG', '袋', 10.000, NULL, NULL,
        7, 0.000, 0.00, 0.0000, 0.000, 0, NULL,
        'NONE', NULL, NULL, 0, NULL, NULL,
        0, 1, 1, 0, 10001, 10001, '2026-09-10 10:00:00', '2026-09-10 10:00:00');

-- 药品档案（名称、类别、包装取自 V2_05 目录；库存下限按 App 的 PACK 录入折算为记账单位：
--   5201 15 袋 × 500 克 = 7,500 克；5202 10 袋 × 100 克 = 1,000 克）
INSERT INTO t_drug (id, farm_id, category, catalog_id, name, spec, unit, pack_unit, pack_size, min_stock,
                    min_stock_unit, is_low_stock, low_stock_at, is_unit_locked, version, deleted,
                    created_by, updated_by, created_at, updated_at)
VALUES (5001, 1001, 'VACCINE', 7001, '猪瘟活疫苗', '10 头份/瓶', 'DOSE', '瓶', 10.000, NULL,
        NULL, 0, NULL, 1, 2, 0, 10001, 10001, '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
       (5003, 1001, 'VACCINE', 7003, '口蹄疫灭活疫苗', '10 头份/瓶', 'DOSE', '瓶', 10.000, NULL,
        NULL, 0, NULL, 1, 1, 0, 10001, 10001, '2026-10-08 10:00:00', '2026-10-08 10:00:00'),
       (5201, 1001, 'VET_DRUG', 7201, '阿莫西林可溶性粉', '10% · 500 克/袋', 'G', '袋', 500.000, 7500.000,
        'PACK', 1, '2026-10-19 00:05:12', 1, 2, 0, 10001, 10001, '2026-09-01 10:00:00', '2026-10-05 09:00:00'),
       (5202, 1001, 'VET_DRUG', 7202, '氟苯尼考粉', '100 克/袋', 'G', '袋', 100.000, 1000.000,
        'PACK', 0, NULL, 1, 1, 0, 10001, 10001, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
       (5301, 1001, 'DISINFECTANT', 7301, '聚维酮碘溶液', '5 升/桶', 'L', '桶', 5.000, NULL,
        NULL, 0, NULL, 1, 1, 0, 10001, 10001, '2026-09-25 14:00:00', '2026-09-25 14:00:00');

-- 批号结存（7 个）：5201 已停用、6103 已过期、6301 临期，其余正常；结存与均价与 mock 一致
INSERT INTO t_drug_lot (id, farm_id, drug_id, catalog_id, category, lot_no, expire_date, first_inbound_date,
                        inbound_count, stock_qty, stock_amount, avg_price, recall_id, recalled_at, recall_reason,
                        recall_stock_qty, near_notified_at, expired_notified_at, version, created_at, updated_at)
VALUES (5201, 1001, 5003, 7003, 'VACCINE', '2025047', '2027-04-20', '2026-10-08',
        1, 300.000, 840.00, 2.8000, 9001, '2026-10-18 16:20:00', '生产企业召回，抽检效价不合格',
        300.000, NULL, NULL, 5, '2026-10-08 10:00:00', '2026-10-18 16:20:00'),
       (6001, 1001, 5001, 7001, 'VACCINE', '2025081', '2026-12-30', '2026-09-10',
        1, 400.000, 940.00, 2.3500, NULL, NULL, NULL,
        NULL, '2026-10-19 00:05:12', NULL, 5, '2026-09-10 10:00:00', '2026-10-19 00:05:12'),
       (6002, 1001, 5001, 7001, 'VACCINE', '2025113', '2027-03-15', '2026-10-16',
        1, 600.000, 1440.00, 2.4000, NULL, NULL, NULL,
        NULL, NULL, NULL, 5, '2026-10-16 15:20:00', '2026-10-16 15:20:00'),
       (6101, 1001, 5201, 7201, 'VET_DRUG', '2025091', '2027-08-31', '2026-09-01',
        1, 6000.000, 192.00, 0.0320, NULL, NULL, NULL,
        NULL, NULL, NULL, 5, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (6103, 1001, 5201, 7201, 'VET_DRUG', '2024118', '2026-10-12', '2025-10-20',
        1, 1500.000, 45.00, 0.0300, NULL, NULL, NULL,
        NULL, NULL, '2026-10-13 00:05:12', 5, '2025-10-20 10:00:00', '2026-10-13 00:05:12'),
       (6111, 1001, 5202, 7202, 'VET_DRUG', '2025062', '2027-06-30', '2026-10-01',
        1, 1800.000, 252.00, 0.1400, NULL, NULL, NULL,
        NULL, NULL, NULL, 5, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
       (6301, 1001, 5301, 7301, 'DISINFECTANT', '2025033', '2026-11-08', '2026-09-25',
        1, 40.000, 360.00, 9.0000, NULL, NULL, NULL,
        NULL, '2026-10-19 00:05:12', NULL, 5, '2026-09-25 14:00:00', '2026-10-19 00:05:12');

-- 入库单：6201/6202 饲料；6203–6211 药苗（药品按 PACK 录入，qty 为记账单位）
INSERT INTO t_stock_inbound (id, farm_id, inbound_no, material_type, feed_id, drug_id, drug_lot_id, lot_no,
                             input_qty, input_unit, qty, unit_price, measure_price, amount, production_date,
                             shelf_life_date, expire_date, supplier_name, inbound_date, is_sync_finance,
                             finance_record_id, flow_id, status, void_at, void_by, client_request_id, version,
                             created_by, updated_by, created_at, updated_at)
VALUES (6201, 1001, 'RK26106201', 'FEED', 5101, NULL, NULL, NULL,
        2000.000, 'MEASURE', 2000.000, 3.20, 3.2000, 6400.00, '2026-10-08',
        '2027-01-08', NULL, '松山区丰禾饲料经销部', '2026-10-12', 1,
        8601, 9240, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6201', 1,
        10001, 10001, '2026-10-12 09:00:00', '2026-10-12 09:00:00'),
       (6202, 1001, 'RK26106202', 'FEED', 5102, NULL, NULL, NULL,
        2000.000, 'MEASURE', 2000.000, 4.20, 4.2000, 8400.00, '2026-10-10',
        '2026-12-20', NULL, '松山区丰禾饲料经销部', '2026-10-18', 1,
        8601, 9290, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6202', 1,
        10001, 10001, '2026-10-18 09:30:00', '2026-10-18 09:30:00'),
       (6203, 1001, 'RK25106203', 'DRUG', NULL, 5201, 6103, '2024118',
        6.000, 'PACK', 3000.000, 15.00, 0.0300, 90.00, NULL,
        NULL, '2026-10-12', NULL, '2025-10-20', 1,
        8601, 9502, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6203', 1,
        10001, 10001, '2025-10-20 10:00:00', '2025-10-20 10:00:00'),
       (6204, 1001, 'RK26096204', 'DRUG', NULL, 5301, 6301, '2025033',
        10.000, 'PACK', 50.000, 45.00, 9.0000, 450.00, NULL,
        NULL, '2026-11-08', NULL, '2026-09-25', 1,
        8601, 9504, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6204', 1,
        10001, 10001, '2026-09-25 14:00:00', '2026-09-25 14:00:00'),
       (6205, 1001, 'RK26096205', 'DRUG', NULL, 5001, 6001, '2025081',
        50.000, 'PACK', 500.000, 23.50, 2.3500, 1175.00, NULL,
        NULL, '2026-12-30', NULL, '2026-09-10', 1,
        8601, 9503, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6205', 1,
        10001, 10001, '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
       (6207, 1001, 'RK26096207', 'DRUG', NULL, 5201, 6101, '2025091',
        16.000, 'PACK', 8000.000, 16.00, 0.0320, 256.00, NULL,
        NULL, '2027-08-31', NULL, '2026-09-01', 1,
        8601, 9506, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6207', 1,
        10001, 10001, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (6209, 1001, 'RK26106209', 'DRUG', NULL, 5202, 6111, '2025062',
        18.000, 'PACK', 1800.000, 14.00, 0.1400, 252.00, NULL,
        NULL, '2027-06-30', NULL, '2026-10-01', 1,
        8601, 9508, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6209', 1,
        10001, 10001, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
       (6210, 1001, 'RK26106210', 'DRUG', NULL, 5003, 5201, '2025047',
        40.000, 'PACK', 400.000, 28.00, 2.8000, 1120.00, NULL,
        NULL, '2027-04-20', NULL, '2026-10-08', 1,
        8601, 9500, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6210', 1,
        10001, 10001, '2026-10-08 10:00:00', '2026-10-08 10:00:00'),
       (6211, 1001, 'RK26106211', 'DRUG', NULL, 5001, 6002, '2025113',
        60.000, 'PACK', 600.000, 24.00, 2.4000, 1440.00, NULL,
        NULL, '2027-03-15', NULL, '2026-10-16', 1,
        8601, 9507, 'NORMAL', NULL, NULL, 'sample-stock-inbound-6211', 2,
        10001, 10001, '2026-10-16 15:20:00', '2026-10-16 15:25:00');

-- 出库单：6501 饲料其他出库（次日起只能申请修改）、6511 领用（关联 4 号育肥舍）、6512 退货
INSERT INTO t_stock_outbound (id, farm_id, outbound_no, material_type, out_type, feed_id, drug_id, drug_lot_id,
                              input_qty, input_unit, qty, unit_price, amount, max_qty, destination, target_type,
                              batch_id, pen_id, receiver_id, return_to, return_reason, remark, out_date, flow_id,
                              status, void_at, void_by, client_request_id, version, created_by, updated_by,
                              created_at, updated_at)
VALUES (6501, 1001, 'CK26106501', 'FEED', 'OTHER_OUT', 5102, NULL, NULL,
        50.000, 'MEASURE', 50.000, 4.2000, 210.00, 1900.000, 'RETURN_SUPPLIER', NULL,
        NULL, NULL, NULL, NULL, NULL, '包装破损，退回经销部', '2026-10-16', 9270,
        'NORMAL', NULL, NULL, 'sample-stock-outbound-6501', 1, 10001, 10001,
        '2026-10-16 10:05:00', '2026-10-16 10:05:00'),
       (6511, 1001, 'CK26106511', 'DRUG', 'USE', NULL, 5301, 6301,
        2.000, 'PACK', 10.000, 9.0000, 90.00, 8.000, NULL, 'PEN',
        NULL, 2004, 10004, NULL, NULL, '伤口处理和器具消毒', '2026-10-19', 9511,
        'NORMAL', NULL, NULL, 'sample-stock-outbound-6511', 1, 10001, 10001,
        '2026-10-19 09:40:00', '2026-10-19 09:40:00'),
       (6512, 1001, 'CK26106512', 'DRUG', 'RETURN', NULL, 5201, 6101,
        4.000, 'PACK', 2000.000, 0.0320, 64.00, 16.000, NULL, NULL,
        NULL, NULL, NULL, '松山区康牧兽药店', 'PACKAGE_DAMAGED', '外包装受潮破损', '2026-10-19', 9512,
        'NORMAL', NULL, NULL, 'sample-stock-outbound-6512', 1, 10001, 10001,
        '2026-10-19 10:20:00', '2026-10-19 10:20:00');

-- 报损 6401（饲料霉变 10 千克 × 3.20 = 32.00）
INSERT INTO t_stock_loss (id, farm_id, loss_type, feed_id, drug_id, drug_lot_id, input_qty, input_unit, qty,
                          reason, remark, loss_date, unit_price, amount, flow_id, status, client_request_id,
                          version, created_by, updated_by, created_at, updated_at)
VALUES (6401, 1001, 'FEED_LOSS', 5101, NULL, NULL, 10.000, 'MEASURE', 10.000,
        'MILDEW', '3 号育肥舍料塔底部结块', '2026-10-15', 3.2000, 32.00, 9260, 'NORMAL',
        'sample-stock-loss-6401', 1, 10001, 10001, '2026-10-15 16:30:00', '2026-10-15 16:30:00');

-- 盘点单 6301（10 月 1 日，盘亏 5101 12 千克、盘盈 5102 6 千克）与 6290（9 月 1 日，全部相符）
INSERT INTO t_stocktake (id, farm_id, stocktake_no, stocktake_date, item_count, gain_count, loss_count,
                         gain_amount, loss_amount, client_request_id, version, created_by, updated_by,
                         created_at, updated_at)
VALUES (6301, 1001, 'PD2610010001', '2026-10-01', 2, 1, 1,
        25.20, 38.40, 'sample-stocktake-6301', 1, 10001, 10001,
        '2026-10-01 18:00:00', '2026-10-01 18:00:00'),
       (6290, 1001, 'PD2609010001', '2026-09-01', 1, 0, 0,
        0.00, 0.00, 'sample-stocktake-6290', 1, 10002, 10002,
        '2026-09-01 18:00:00', '2026-09-01 18:00:00');

INSERT INTO t_stocktake_item (id, stocktake_id, farm_id, feed_id, book_qty, actual_qty, diff_qty, result,
                              avg_price, diff_amount, flow_id, created_at, updated_at)
VALUES (1, 6301, 1001, 5101, 500.000, 488.000, -12.000, 'LOSS',
        3.2000, -38.40, 9201, '2026-10-01 18:00:00', '2026-10-01 18:00:00'),
       (2, 6301, 1001, 5102, 1850.000, 1856.000, 6.000, 'GAIN',
        4.2000, 25.20, 9202, '2026-10-01 18:00:00', '2026-10-01 18:00:00'),
       (3, 6290, 1001, 5101, 400.000, 400.000, 0.000, 'MATCH',
        3.2000, 0.00, NULL, '2026-09-01 18:00:00', '2026-09-01 18:00:00');

-- 饲料流水 9 条（与 mock 的 flow_id / occur_at / 结存快照一致；origin_flow_id = 自身 id）
INSERT INTO t_stock_flow (id, farm_id, material_type, feed_id, drug_id, drug_lot_id, flow_type, direction, qty,
                          unit_price, amount, stock_qty_after, stock_amount_after, biz_type, biz_id, batch_id,
                          pen_id, operator_id, related_desc, occur_at, biz_date, is_batch_cost, is_feed_usage,
                          origin_flow_id, status, void_reason, void_at, recalc_at, created_at, updated_at)
VALUES (9201, 1001, 'FEED', 5101, NULL, NULL, 'STOCKTAKE_LOSS', 'OUT', 12.000,
        3.2000, 38.40, 488.000, 1561.60, 'STOCKTAKE', 6301, NULL,
        NULL, 10001, '10月1日盘点', '2026-10-01 18:00:00', '2026-10-01', 0, 0,
        9201, 'NORMAL', NULL, NULL, NULL, '2026-10-01 18:00:00', '2026-10-01 18:00:00'),
       (9202, 1001, 'FEED', 5102, NULL, NULL, 'STOCKTAKE_GAIN', 'IN', 6.000,
        4.2000, 25.20, 1856.000, 7795.20, 'STOCKTAKE', 6301, NULL,
        NULL, 10001, '10月1日盘点', '2026-10-01 18:00:00', '2026-10-01', 0, 0,
        9202, 'NORMAL', NULL, NULL, NULL, '2026-10-01 18:00:00', '2026-10-01 18:00:00'),
       (9240, 1001, 'FEED', 5101, NULL, NULL, 'INBOUND', 'IN', 2000.000,
        3.2000, 6400.00, 2110.000, 6752.00, 'INBOUND', 6201, NULL,
        NULL, 10001, '松山区丰禾饲料经销部', '2026-10-12 09:00:00', '2026-10-12', 0, 0,
        9240, 'NORMAL', NULL, NULL, NULL, '2026-10-12 09:00:00', '2026-10-12 09:00:00'),
       (9260, 1001, 'FEED', 5101, NULL, NULL, 'LOSS', 'OUT', 10.000,
        3.2000, 32.00, 2100.000, 6720.00, 'LOSS', 6401, NULL,
        NULL, 10001, '霉变', '2026-10-15 16:30:00', '2026-10-15', 0, 0,
        9260, 'NORMAL', NULL, NULL, NULL, '2026-10-15 16:30:00', '2026-10-15 16:30:00'),
       (9270, 1001, 'FEED', 5102, NULL, NULL, 'OTHER_OUT', 'OUT', 50.000,
        4.2000, 210.00, 900.000, 3780.00, 'OUTBOUND', 6501, NULL,
        NULL, 10001, '退回供应商', '2026-10-16 10:05:00', '2026-10-16', 0, 0,
        9270, 'NORMAL', NULL, NULL, NULL, '2026-10-16 10:05:00', '2026-10-16 10:05:00'),
       (9288, 1001, 'FEED', 5101, NULL, NULL, 'FEED_USE', 'OUT', 140.000,
        3.2000, 448.00, 658.000, 2105.60, 'FEED_RECORD', 7001, 3001,
        2003, 10003, 'ZM2610-031 · 3 号育肥舍 晚餐', '2026-10-18 17:08:00', '2026-10-18', 1, 1,
        9288, 'NORMAL', NULL, NULL, NULL, '2026-10-18 17:08:00', '2026-10-18 17:08:00'),
       (9290, 1001, 'FEED', 5102, NULL, NULL, 'INBOUND', 'IN', 2000.000,
        4.2000, 8400.00, 2150.000, 9030.00, 'INBOUND', 6202, NULL,
        NULL, 10001, '松山区丰禾饲料经销部', '2026-10-18 09:30:00', '2026-10-18', 0, 0,
        9290, 'NORMAL', NULL, NULL, NULL, '2026-10-18 09:30:00', '2026-10-18 09:30:00'),
       (9301, 1001, 'FEED', 5101, NULL, NULL, 'FEED_USE', 'OUT', 158.000,
        3.2000, 505.60, 500.000, 1600.00, 'FEED_RECORD', 7106, 3001,
        2003, 10003, 'ZM2610-031 · 3 号育肥舍 早餐', '2026-10-19 07:12:00', '2026-10-19', 1, 1,
        9301, 'NORMAL', NULL, NULL, NULL, '2026-10-19 07:12:00', '2026-10-19 07:12:00'),
       (9302, 1001, 'FEED', 5102, NULL, NULL, 'FEED_USE', 'OUT', 150.000,
        4.2000, 630.00, 1850.000, 7770.00, 'FEED_RECORD', 7120, 3003,
        2001, 10004, 'ZM2610-045 · 1 号保育舍 早餐', '2026-10-19 07:20:00', '2026-10-19', 1, 1,
        9302, 'NORMAL', NULL, NULL, NULL, '2026-10-19 07:20:00', '2026-10-19 07:20:00');

-- 药品流水 8 条（flow_id 与 mock 一致；药品流水的 stock_qty_after 由接口输出 null，这里仍存真实结存便于核对）
INSERT INTO t_stock_flow (id, farm_id, material_type, feed_id, drug_id, drug_lot_id, flow_type, direction, qty,
                          unit_price, amount, stock_qty_after, stock_amount_after, biz_type, biz_id, batch_id,
                          pen_id, operator_id, related_desc, occur_at, biz_date, is_batch_cost, is_feed_usage,
                          origin_flow_id, status, void_reason, void_at, recalc_at, created_at, updated_at)
VALUES (9500, 1001, 'DRUG', NULL, 5003, 5201, 'INBOUND', 'IN', 400.000,
        2.8000, 1120.00, 400.000, 1120.00, 'INBOUND', 6210, NULL,
        NULL, 10001, NULL, '2026-10-08 10:00:00', '2026-10-08', 0, 0,
        9500, 'NORMAL', NULL, NULL, NULL, '2026-10-08 10:00:00', '2026-10-08 10:00:00'),
       (9501, 1001, 'DRUG', NULL, 5003, 5201, 'VACCINE_USE', 'OUT', 100.000,
        2.8000, 280.00, 300.000, 840.00, 'IMMUNE_RECORD', 8812, 3003,
        2001, 10004, NULL, '2026-10-12 09:30:00', '2026-10-12', 1, 0,
        9501, 'NORMAL', NULL, NULL, NULL, '2026-10-12 09:30:00', '2026-10-12 09:30:00'),
       (9502, 1001, 'DRUG', NULL, 5201, 6103, 'INBOUND', 'IN', 3000.000,
        0.0300, 90.00, 3000.000, 90.00, 'INBOUND', 6203, NULL,
        NULL, 10001, NULL, '2025-10-20 10:00:00', '2025-10-20', 0, 0,
        9502, 'NORMAL', NULL, NULL, NULL, '2025-10-20 10:00:00', '2025-10-20 10:00:00'),
       (9503, 1001, 'DRUG', NULL, 5001, 6001, 'INBOUND', 'IN', 500.000,
        2.3500, 1175.00, 500.000, 1175.00, 'INBOUND', 6205, NULL,
        NULL, 10001, NULL, '2026-09-10 10:00:00', '2026-09-10', 0, 0,
        9503, 'NORMAL', NULL, NULL, NULL, '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
       (9504, 1001, 'DRUG', NULL, 5301, 6301, 'INBOUND', 'IN', 50.000,
        9.0000, 450.00, 50.000, 450.00, 'INBOUND', 6204, NULL,
        NULL, 10001, NULL, '2026-09-25 14:00:00', '2026-09-25', 0, 0,
        9504, 'NORMAL', NULL, NULL, NULL, '2026-09-25 14:00:00', '2026-09-25 14:00:00'),
       (9505, 1001, 'DRUG', NULL, 5301, 6301, 'DISINFECT_USE', 'OUT', 10.000,
        9.0000, 90.00, 40.000, 360.00, 'DISINFECT_RECORD', 8701, NULL,
        2002, 10003, NULL, '2026-10-17 08:30:00', '2026-10-17', 1, 0,
        9505, 'NORMAL', NULL, NULL, NULL, '2026-10-17 08:30:00', '2026-10-17 08:30:00'),
       (9506, 1001, 'DRUG', NULL, 5201, 6101, 'INBOUND', 'IN', 8000.000,
        0.0320, 256.00, 8000.000, 256.00, 'INBOUND', 6207, NULL,
        NULL, 10001, NULL, '2026-09-01 10:00:00', '2026-09-01', 0, 0,
        9506, 'NORMAL', NULL, NULL, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (9507, 1001, 'DRUG', NULL, 5001, 6002, 'INBOUND', 'IN', 600.000,
        2.4000, 1440.00, 600.000, 1440.00, 'INBOUND', 6211, NULL,
        NULL, 10001, NULL, '2026-10-16 15:20:00', '2026-10-16', 0, 0,
        9507, 'NORMAL', NULL, NULL, NULL, '2026-10-16 15:20:00', '2026-10-16 15:20:00'),
       (9508, 1001, 'DRUG', NULL, 5202, 6111, 'INBOUND', 'IN', 1800.000,
        0.1400, 252.00, 1800.000, 252.00, 'INBOUND', 6209, NULL,
        NULL, 10001, NULL, '2026-10-01 10:00:00', '2026-10-01', 0, 0,
        9508, 'NORMAL', NULL, NULL, NULL, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
       (9511, 1001, 'DRUG', NULL, 5301, 6301, 'MANUAL_USE', 'OUT', 10.000,
        9.0000, 90.00, 30.000, 270.00, 'OUTBOUND', 6511, NULL,
        2004, 10004, NULL, '2026-10-19 09:40:00', '2026-10-19', 1, 0,
        9511, 'NORMAL', NULL, NULL, NULL, '2026-10-19 09:40:00', '2026-10-19 09:40:00'),
       (9512, 1001, 'DRUG', NULL, 5201, 6101, 'RETURN', 'OUT', 2000.000,
        0.0320, 64.00, 6000.000, 192.00, 'OUTBOUND', 6512, NULL,
        NULL, 10001, '松山区康牧兽药店', '2026-10-19 10:20:00', '2026-10-19', 0, 0,
        9512, 'NORMAL', NULL, NULL, NULL, '2026-10-19 10:20:00', '2026-10-19 10:20:00');

-- 批次物资成本明细：饲喂领用与领用 / 消毒（与 mock 的 allocations 一致；4 号育肥舍只有 3002 在栏）
INSERT INTO t_batch_material_cost (id, farm_id, batch_id, pen_id, flow_id, source_type, source_id, cost_type, qty,
                                   head_count, ratio, amount, biz_date, status, created_at, updated_at)
VALUES (8001, 1001, 3001, 2003, 9288, 'FEED_RECORD', 7001, 'FEED', 140.000,
        200, 1.0000, 448.00, '2026-10-18', 'NORMAL', '2026-10-18 17:08:00', '2026-10-18 17:08:00'),
       (8002, 1001, 3001, 2003, 9301, 'FEED_RECORD', 7106, 'FEED', 158.000,
        200, 1.0000, 505.60, '2026-10-19', 'NORMAL', '2026-10-19 07:12:00', '2026-10-19 07:12:00'),
       (8003, 1001, 3003, 2001, 9302, 'FEED_RECORD', 7120, 'FEED', 150.000,
        320, 1.0000, 630.00, '2026-10-19', 'NORMAL', '2026-10-19 07:20:00', '2026-10-19 07:20:00'),
       (8004, 1001, 3003, 2001, 9501, 'IMMUNE_RECORD', 8812, 'DRUG', 100.000,
        320, 1.0000, 280.00, '2026-10-12', 'NORMAL', '2026-10-12 09:30:00', '2026-10-12 09:30:00'),
       (8005, 1001, 3004, 2002, 9505, 'DISINFECT_RECORD', 8701, 'DRUG', 10.000,
        200, 1.0000, 90.00, '2026-10-17', 'NORMAL', '2026-10-17 08:30:00', '2026-10-17 08:30:00'),
       (8006, 1001, 3002, 2004, 9511, 'OUTBOUND', 6511, 'DRUG', 10.000,
        150, 1.0000, 90.00, '2026-10-19', 'NORMAL', '2026-10-19 09:40:00', '2026-10-19 09:40:00');

-- 往来单位（最近供应商 / 退回对象，API-M12）
INSERT INTO t_stock_partner (id, farm_id, kind, name, use_count, last_used_at, created_at, updated_at)
VALUES (8101, 1001, 'SUPPLIER', '松山区丰禾饲料经销部', 2, '2026-10-18 09:30:00',
        '2026-10-12 09:00:00', '2026-10-18 09:30:00'),
       (8102, 1001, 'SUPPLIER', '正大饲料东山站', 1, '2026-09-20 10:00:00',
        '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
       (8103, 1001, 'SUPPLIER', '金穗饲料商行', 1, '2026-09-05 10:00:00',
        '2026-09-05 10:00:00', '2026-09-05 10:00:00'),
       (8104, 1001, 'RETURN_TO', '松山区康牧兽药店', 1, '2026-10-19 10:20:00',
        '2026-10-19 10:20:00', '2026-10-19 10:20:00'),
       (8105, 1001, 'RETURN_TO', '北山动保经销部', 1, '2026-09-18 15:00:00',
        '2026-09-18 15:00:00', '2026-09-18 15:00:00');

-- 临期天数设置（无记录时接口按 30 天返回；这里落一行便于演示 version）
INSERT INTO t_stock_setting (id, farm_id, near_days_vet, near_days_vaccine, near_days_disinfectant, version,
                             created_by, updated_by, created_at, updated_at)
VALUES (8201, 1001, 30, 30, 30, 1, 10001, 10001, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- 自增起点：新饲料从 5300 起、新批号从 7001 起、新单据 / 流水避开示例 ID
ALTER TABLE t_feed AUTO_INCREMENT = 5300;
ALTER TABLE t_drug AUTO_INCREMENT = 5400;
ALTER TABLE t_drug_lot AUTO_INCREMENT = 7001;
ALTER TABLE t_stock_inbound AUTO_INCREMENT = 6300;
ALTER TABLE t_stock_outbound AUTO_INCREMENT = 6600;
ALTER TABLE t_stock_loss AUTO_INCREMENT = 6500;
ALTER TABLE t_stocktake AUTO_INCREMENT = 6400;
ALTER TABLE t_stocktake_item AUTO_INCREMENT = 100;
ALTER TABLE t_stock_flow AUTO_INCREMENT = 9600;
ALTER TABLE t_batch_material_cost AUTO_INCREMENT = 8100;
ALTER TABLE t_stock_partner AUTO_INCREMENT = 8200;
ALTER TABLE t_stock_setting AUTO_INCREMENT = 8300;
