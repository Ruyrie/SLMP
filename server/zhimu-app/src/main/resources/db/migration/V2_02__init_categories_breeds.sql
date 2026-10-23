-- 管理端-03 平台初始化数据：品类与品种（dev / test 的 db/migration，Flyway 自动执行）
-- ID 与 App Mock（lib_common categories.json）和 05 号统一示例数据一致，禁止改成 1/11/21。
-- 1201 长大二元：妊娠期 114、待妊检 21、后代默认品种 1101 杜长大；has_std_weight=false、has_feed_std=true 由 V2_04 的标准存在性体现。
-- 2101 白羽肉鸡：孵化期 21。

INSERT INTO t_category (id, name, species, count_unit, is_breeding, sort_no, status, disable_reason,
                        created_by, updated_by, version, created_at, updated_at)
VALUES (101, '育肥猪', 'PIG', 'HEAD', 0, 1, 'ENABLED', NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (102, '种猪', 'PIG', 'HEAD', 1, 2, 'ENABLED', NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (201, '肉鸡', 'POULTRY', 'BIRD', 0, 3, 'ENABLED', NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (301, '肉牛', 'CATTLE', 'HEAD', 0, 4, 'ENABLED', NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

INSERT INTO t_breed (id, category_id, name, description, gestation_days, incubation_days, pregnancy_check_days,
                     offspring_breed_id, sort_no, status, disable_reason,
                     created_by, updated_by, version, created_at, updated_at)
VALUES (1101, 101, '杜长大', '杜洛克 × 长白 × 大白三元杂交', NULL, NULL, NULL, NULL, 1, 'ENABLED', NULL,
        0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (1102, 101, '长白', '长白猪，瘦肉型父本 / 母本', NULL, NULL, NULL, NULL, 2, 'ENABLED', NULL,
        0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (1201, 102, '长大二元', '长白 × 大白二元杂交母猪', 114, NULL, 21, 1101, 1, 'ENABLED', NULL,
        0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (2101, 201, '白羽肉鸡', '白羽快大型肉鸡', NULL, 21, NULL, NULL, 1, 'ENABLED', NULL,
        0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3101, 301, '西门塔尔', '西门塔尔肉牛杂交育肥', NULL, NULL, NULL, NULL, 1, 'ENABLED', NULL,
        0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');
