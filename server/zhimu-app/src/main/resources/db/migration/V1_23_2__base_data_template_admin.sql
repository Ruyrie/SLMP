-- 管理端-03 基础数据补口（3B 续）：WA-D02 免疫模板、WA-D03 兽药疫苗目录、WA-D04 巡检模板。
-- 只在已有表上补列，不新建表、不改已执行的 V1_23 / V1_23_1 / V2_05–V2_08。
-- t_drug_catalog 增加管理端列表顺序（管理端-03 2.5.5：按 sort_no、名称排序）；
-- 平台初始化数据 V2_05 未写该列，默认 0，按 id 保持原顺序；新建目录项取当前最大值 + 1。

ALTER TABLE t_drug_catalog
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT '管理端列表顺序，新增时排最后' AFTER banned_drug_id,
    ADD KEY idx_drug_catalog_sort (drug_category, sort_no);
