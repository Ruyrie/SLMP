-- 管理端-03 平台初始化数据：8 类字典与适用范围
-- ID 按任务书按类型分段：PEN 501–、DEATH 601–、SYMPTOM 401–、FEED 3101–、VACC 701–、DISINFECT 721–、
-- SCRAP 3201–、PAYMENT 3301–。App Mock 已固定的 ID 全部沿用（PEN 501–504、DEATH 601–604、SYMPTOM 401–408、FEED 3101–3105）。
-- VACCINATE_METHOD 饮水用 WATER（App Mock），不用 DRINKING；FEED_TYPE 为 9 项并集；PAYMENT_METHOD 是第 8 类。
-- t_dict_item_scope 的可空列：按畜种时 species 有值、category_id=0；按品类时 species=''、category_id 有值。

-- ===== 栏舍类型 PEN_TYPE（16 项，按畜种） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (501, 'PEN_TYPE', 'NURSERY', '保育舍', '断奶仔猪保育', NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (502, 'PEN_TYPE', 'FATTENING', '育肥舍', '育肥猪舍', NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (503, 'PEN_TYPE', 'SOW', '母猪舍', '妊娠母猪舍', NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (504, 'PEN_TYPE', 'FARROWING', '产房', '待产与哺乳母猪舍', NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (505, 'PEN_TYPE', 'BROODING', '育雏舍', '雏鸡育雏', NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (506, 'PEN_TYPE', 'BROILER', '肉鸡舍', '肉鸡育肥', NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (507, 'PEN_TYPE', 'BREEDER', '种鸡舍', '种鸡饲养', NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (508, 'PEN_TYPE', 'HATCHERY', '孵化室', '种蛋孵化', NULL, 0, 0, 0, 8, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (509, 'PEN_TYPE', 'BEEF_CATTLE', '育肥牛舍', '肉牛育肥', NULL, 0, 0, 0, 9, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (510, 'PEN_TYPE', 'COW_SHED', '母牛舍', '母牛饲养', NULL, 0, 0, 0, 10, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (511, 'PEN_TYPE', 'CALF_SHED', '犊牛舍', '犊牛培育', NULL, 0, 0, 0, 11, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (512, 'PEN_TYPE', 'CATTLE_ISOLATION', '隔离舍', '牛隔离观察', NULL, 0, 0, 0, 12, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (513, 'PEN_TYPE', 'FATTENING_SHEEP', '育肥羊舍', '肉羊育肥', NULL, 0, 0, 0, 13, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (514, 'PEN_TYPE', 'EWE_SHED', '母羊舍', '母羊饲养', NULL, 0, 0, 0, 14, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (515, 'PEN_TYPE', 'LAMB_SHED', '羔羊舍', '羔羊培育', NULL, 0, 0, 0, 15, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (516, 'PEN_TYPE', 'SHEEP_ISOLATION', '隔离舍', '羊隔离观察', NULL, 0, 0, 0, 16, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

INSERT INTO t_dict_item_scope (dict_item_id, scope_type, species, category_id, created_by, created_at, updated_at)
VALUES (501, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (502, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (503, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (504, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (505, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (506, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (507, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (508, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (509, 'SPECIES', 'CATTLE', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (510, 'SPECIES', 'CATTLE', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (511, 'SPECIES', 'CATTLE', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (512, 'SPECIES', 'CATTLE', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (513, 'SPECIES', 'SHEEP', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (514, 'SPECIES', 'SHEEP', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (515, 'SPECIES', 'SHEEP', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (516, 'SPECIES', 'SHEEP', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 死淘原因 DEATH_REASON（4 项，全部品类；OTHER 兜底） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (601, 'DEATH_REASON', 'SICK', '病死', NULL, NULL, 0, 0, 1, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (602, 'DEATH_REASON', 'CULL', '淘汰', NULL, NULL, 0, 0, 1, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (603, 'DEATH_REASON', 'ACCIDENT', '意外死亡', NULL, NULL, 0, 0, 1, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (604, 'DEATH_REASON', 'OTHER', '其他', '选择“其他”时需填写备注', NULL, 1, 1, 1, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 症状 SYMPTOM（38 项，按品类；401–408 与 App Mock 完全一致） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (401, 'SYMPTOM', 'DIARRHEA', '腹泻', NULL, NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (402, 'SYMPTOM', 'VOMITING', '呕吐', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (403, 'SYMPTOM', 'ANOREXIA', '食欲减退', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (404, 'SYMPTOM', 'DEPRESSION', '精神沉郁', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (405, 'SYMPTOM', 'FEVER', '发热', NULL, NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (406, 'SYMPTOM', 'COUGH', '咳嗽', NULL, NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (407, 'SYMPTOM', 'DYSPNEA', '呼吸困难', NULL, NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (408, 'SYMPTOM', 'SKIN_RED', '皮肤发红', NULL, NULL, 0, 0, 0, 8, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (409, 'SYMPTOM', 'CONSTIPATION', '便秘', NULL, NULL, 0, 0, 0, 9, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (410, 'SYMPTOM', 'LAMENESS', '跛行', NULL, NULL, 0, 0, 0, 10, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (411, 'SYMPTOM', 'CONVULSION', '抽搐', NULL, NULL, 0, 0, 0, 11, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (412, 'SYMPTOM', 'SALIVATION', '流涎', NULL, NULL, 0, 0, 0, 12, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (413, 'SYMPTOM', 'LOW_INTAKE', '减料', NULL, NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (414, 'SYMPTOM', 'LISTLESS', '精神萎靡', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (415, 'SYMPTOM', 'GASPY', '呼吸困难', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (416, 'SYMPTOM', 'RALES', '啰音', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (417, 'SYMPTOM', 'POULTRY_COUGH', '咳嗽', NULL, NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (418, 'SYMPTOM', 'POULTRY_DIARRHEA', '腹泻', NULL, NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (419, 'SYMPTOM', 'PARALYSIS', '瘫痪', NULL, NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (420, 'SYMPTOM', 'EGG_DROP', '产蛋下降', NULL, NULL, 0, 0, 0, 8, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (421, 'SYMPTOM', 'FACE_SWELL', '头面部肿胀', NULL, NULL, 0, 0, 0, 9, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (422, 'SYMPTOM', 'ROUGH_FEATHER', '羽毛蓬乱', NULL, NULL, 0, 0, 0, 10, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (423, 'SYMPTOM', 'OPEN_MOUTH', '张口呼吸', NULL, NULL, 0, 0, 0, 11, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (424, 'SYMPTOM', 'ATAXIA', '共济失调', NULL, NULL, 0, 0, 0, 12, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (425, 'SYMPTOM', 'CATTLE_ANOREXIA', '食欲减退', NULL, NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (426, 'SYMPTOM', 'RUMEN_DOWN', '反刍减少', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (427, 'SYMPTOM', 'CATTLE_FEVER', '发热', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (428, 'SYMPTOM', 'CATTLE_DIARRHEA', '腹泻', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (429, 'SYMPTOM', 'CATTLE_COUGH', '咳嗽', NULL, NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (430, 'SYMPTOM', 'CATTLE_DYSPNEA', '呼吸困难', NULL, NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (431, 'SYMPTOM', 'BLOAT', '瘤胃鼓气', NULL, NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (432, 'SYMPTOM', 'CATTLE_SALIVATION', '流涎', NULL, NULL, 0, 0, 0, 8, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (433, 'SYMPTOM', 'CATTLE_LAMENESS', '跛行', NULL, NULL, 0, 0, 0, 9, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (434, 'SYMPTOM', 'SKIN_NODULE', '皮肤结节', NULL, NULL, 0, 0, 0, 10, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (435, 'SYMPTOM', 'EYE_NOSE_DISCHARGE', '眼鼻分泌物', NULL, NULL, 0, 0, 0, 11, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (436, 'SYMPTOM', 'EMACIATION', '消瘦', NULL, NULL, 0, 0, 0, 12, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (437, 'SYMPTOM', 'CATTLE_CONSTIPATION', '便秘', NULL, NULL, 0, 0, 0, 13, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (438, 'SYMPTOM', 'CATTLE_CONVULSION', '抽搐', NULL, NULL, 0, 0, 0, 14, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

INSERT INTO t_dict_item_scope (dict_item_id, scope_type, species, category_id, created_by, created_at, updated_at)
VALUES (401, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (402, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (403, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (404, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (405, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (406, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (407, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (408, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (409, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (410, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (411, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (412, 'CATEGORY', '', 101, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (413, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (414, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (415, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (416, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (417, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (418, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (419, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (420, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (421, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (422, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (423, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (424, 'CATEGORY', '', 201, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (425, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (426, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (427, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (428, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (429, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (430, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (431, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (432, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (433, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (434, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (435, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (436, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (437, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (438, 'CATEGORY', '', 301, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 饲料类型 FEED_TYPE（9 项并集，全部品类；3101–3105 与 App Mock 一致） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (3101, 'FEED_TYPE', 'COMPLETE', '全价料', '营养全面，可以直接饲喂的配合饲料', NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3102, 'FEED_TYPE', 'NURSERY', '保育料', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3103, 'FEED_TYPE', 'CONCENTRATE', '浓缩料', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3104, 'FEED_TYPE', 'PREMIX', '预混料', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3105, 'FEED_TYPE', 'RAW', '原料', NULL, NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3106, 'FEED_TYPE', 'ENERGY', '能量原料', NULL, NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3107, 'FEED_TYPE', 'PROTEIN', '蛋白原料', NULL, NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3108, 'FEED_TYPE', 'ROUGHAGE', '青粗饲料', NULL, NULL, 0, 0, 0, 8, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3109, 'FEED_TYPE', 'ADDITIVE', '添加剂', NULL, NULL, 0, 0, 0, 9, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 接种方式 VACCINATE_METHOD（7 项，按畜种；饮水用 WATER） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (701, 'VACCINATE_METHOD', 'IM', '肌肉注射', NULL, NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (702, 'VACCINATE_METHOD', 'SC', '皮下注射', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (703, 'VACCINATE_METHOD', 'NASAL', '滴鼻', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (704, 'VACCINATE_METHOD', 'ORAL', '口服', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (705, 'VACCINATE_METHOD', 'EYE_NASAL', '滴鼻点眼', NULL, NULL, 0, 0, 0, 5, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (706, 'VACCINATE_METHOD', 'WATER', '饮水', NULL, NULL, 0, 0, 0, 6, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (707, 'VACCINATE_METHOD', 'SPRAY', '喷雾', NULL, NULL, 0, 0, 0, 7, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

INSERT INTO t_dict_item_scope (dict_item_id, scope_type, species, category_id, created_by, created_at, updated_at)
VALUES (701, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (702, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (702, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (703, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (704, 'SPECIES', 'PIG', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (705, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (706, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (707, 'SPECIES', 'POULTRY', 0, 0, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 消毒方式 DISINFECT_METHOD（4 项，全部品类） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (721, 'DISINFECT_METHOD', 'SPRAY', '喷雾', NULL, NULL, 0, 0, 0, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (722, 'DISINFECT_METHOD', 'FUMIGATE', '熏蒸', NULL, NULL, 0, 0, 0, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (723, 'DISINFECT_METHOD', 'WIPE', '擦拭', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (724, 'DISINFECT_METHOD', 'SOAK', '浸泡', NULL, NULL, 0, 0, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 报废原因 SCRAP_REASON（4 项，全部品类；PLATFORM_RECALL 系统项，OTHER 兜底） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (3201, 'SCRAP_REASON', 'PLATFORM_RECALL', '平台停用', NULL, NULL, 0, 0, 1, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3202, 'SCRAP_REASON', 'EXPIRED', '已过期', NULL, NULL, 0, 0, 1, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3203, 'SCRAP_REASON', 'DAMAGED', '破损', NULL, NULL, 0, 0, 0, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3204, 'SCRAP_REASON', 'OTHER', '其他', NULL, NULL, 1, 1, 0, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');

-- ===== 收款方式 PAYMENT_METHOD（第 8 类，4 项，全部品类；ID 3301– 不与 Mock 的 701–704 冲突） =====
INSERT INTO t_dict_item (id, dict_type, code, name, description, icon, remark_required, is_fallback, is_system,
                         sort_no, status, disable_reason, disabled_at, disabled_by,
                         created_by, updated_by, version, created_at, updated_at)
VALUES (3301, 'PAYMENT_METHOD', 'BANK_TRANSFER', '银行转账', NULL, NULL, 0, 0, 1, 1, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3302, 'PAYMENT_METHOD', 'WECHAT', '微信', NULL, NULL, 0, 0, 1, 2, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3303, 'PAYMENT_METHOD', 'ALIPAY', '支付宝', NULL, NULL, 0, 0, 1, 3, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00'),
       (3304, 'PAYMENT_METHOD', 'CASH', '现金', NULL, NULL, 0, 0, 1, 4, 'ENABLED', NULL, NULL, NULL, 0, 0, 1, '2026-10-01 00:00:00', '2026-10-01 00:00:00');
