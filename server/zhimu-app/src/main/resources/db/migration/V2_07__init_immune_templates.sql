-- 管理端-03 平台初始化数据（3B）：免疫程序模板、版本与项目。
-- 猪模板与 App module_health mock 完全一致：
--   育肥猪（杜长大 1101）：猪瘟 25/60/96、伪狂犬 35/89/117、口蹄疫 60/85/110；
--   mock 计划 8117（猪瘟·加强·96 日龄）、8110（口蹄疫·二免·85）、8121（伪狂犬·二免·89）、
--   8122（口蹄疫·三免·110）、8123（伪狂犬·加强·117）与入栏前项目 8101/8102/8103（25/35/60）。
-- 种猪模板含按日期项目（每年 3/6/9 月 15 日，提前 7 天生成），用于 module08 的种畜栏舍项。
-- 长白 1102 设基础三针（猪瘟 25、伪狂犬 35、口蹄疫 60），使 U09 has_immune_template 与
-- App lib_common categories.json 的 1102 has_immune_template=true 一致。
-- 肉牛模板使用口蹄疫灭活疫苗 7003（猪、牛、羊适用）。
-- 本批 V2_02 锁定的品种只有 1101/1102/1201/2101/3101，没有羊品种；7001–7004 目录也没有禽用疫苗，
-- 因此不落羊模板与禽模板（避免引用不存在的品种/疫苗），联调报告已注明。
-- 已发布版本不再修改；模块08 按品种取 PUBLISHED 版本生成免疫计划。

INSERT INTO t_immune_template (id, breed_id, name, online_version_no, has_draft, has_by_date, copied_from_id,
                               created_by, updated_by, version, created_at, updated_at)
VALUES (21, 1101, '杜长大育肥猪', 1, 0, 0, NULL, 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (22, 1201, '长大二元种猪', 1, 0, 1, NULL, 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (23, 3101, '西门塔尔肉牛', 1, 0, 0, NULL, 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (24, 1102, '长白育肥猪', 1, 0, 0, NULL, 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

INSERT INTO t_immune_template_version (id, template_id, version_no, status, draft_flag, change_note, item_count,
                                       published_by, published_at, created_by, updated_by, version,
                                       created_at, updated_at)
VALUES (211, 21, 1, 'PUBLISHED', NULL, '初始化育肥猪免疫程序：猪瘟、伪狂犬、口蹄疫', 9,
        1, '2026-09-01 10:00:00', 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (221, 22, 1, 'PUBLISHED', NULL, '初始化种猪免疫程序：春季猪瘟、夏季伪狂犬、秋季口蹄疫普免与细小配种前免疫', 4,
        1, '2026-09-01 10:00:00', 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (231, 23, 1, 'PUBLISHED', NULL, '初始化肉牛免疫程序：口蹄疫首免、二免、加强', 3,
        1, '2026-09-01 10:00:00', 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (241, 24, 1, 'PUBLISHED', NULL, '初始化长白免疫程序：猪瘟、伪狂犬、口蹄疫基础三针', 3,
        1, '2026-09-01 10:00:00', 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

INSERT INTO t_immune_template_item (id, template_id, version_id, source_item_id, change_type, sort_no,
                                    vaccine_id, dose_round, schedule_type, day_age, plan_month, plan_day,
                                    advance_days, dose, dose_unit, vaccinate_method_code, remark,
                                    created_by, created_at, updated_at)
VALUES (2101, 21, 211, NULL, 'NONE', 1, 7001, '首免', 'BY_DAY_AGE', 25, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', '入栏时日龄已超过的按入栏前已接种确认', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2102, 21, 211, NULL, 'NONE', 2, 7002, '首免', 'BY_DAY_AGE', 35, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2103, 21, 211, NULL, 'NONE', 3, 7001, '二免', 'BY_DAY_AGE', 60, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2104, 21, 211, NULL, 'NONE', 4, 7003, '首免', 'BY_DAY_AGE', 60, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2105, 21, 211, NULL, 'NONE', 5, 7003, '二免', 'BY_DAY_AGE', 85, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2106, 21, 211, NULL, 'NONE', 6, 7002, '二免', 'BY_DAY_AGE', 89, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2107, 21, 211, NULL, 'NONE', 7, 7001, '加强免疫', 'BY_DAY_AGE', 96, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', '出栏前 21 天内不安排', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2108, 21, 211, NULL, 'NONE', 8, 7003, '三免', 'BY_DAY_AGE', 110, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2109, 21, 211, NULL, 'NONE', 9, 7002, '加强', 'BY_DAY_AGE', 117, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2201, 22, 221, NULL, 'NONE', 1, 7001, '春季普免', 'BY_DATE', NULL, 3, 15, 7,
        1.00, 'DOSE_PER_HEAD', 'IM', '妊娠后期（产前 15 天内）的母猪顺延到产后 7 天接种', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2202, 22, 221, NULL, 'NONE', 2, 7002, '加强', 'BY_DATE', NULL, 6, 15, 7,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2203, 22, 221, NULL, 'NONE', 3, 7003, '秋季普免', 'BY_DATE', NULL, 9, 15, 7,
        2.00, 'DOSE_PER_HEAD', 'IM', '妊娠后期（产前 15 天内）的母猪顺延到产后 7 天接种', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2204, 22, 221, NULL, 'NONE', 4, 7004, '配种前', 'BY_DAY_AGE', 180, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', '后备母猪配种前 4 周完成', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2301, 23, 231, NULL, 'NONE', 1, 7003, '首免', 'BY_DAY_AGE', 60, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2302, 23, 231, NULL, 'NONE', 2, 7003, '二免', 'BY_DAY_AGE', 120, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2303, 23, 231, NULL, 'NONE', 3, 7003, '加强', 'BY_DAY_AGE', 180, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2401, 24, 241, NULL, 'NONE', 1, 7001, '首免', 'BY_DAY_AGE', 25, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', '入栏时日龄已超过的按入栏前已接种确认', 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2402, 24, 241, NULL, 'NONE', 2, 7002, '首免', 'BY_DAY_AGE', 35, NULL, NULL, NULL,
        1.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (2403, 24, 241, NULL, 'NONE', 3, 7003, '首免', 'BY_DAY_AGE', 60, NULL, NULL, NULL,
        2.00, 'DOSE_PER_HEAD', 'IM', NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00');
