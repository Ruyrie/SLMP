-- U10 字典与 App Mock 对齐：App 的 dict_vaccinate_method.json、dict_vaccinate_poultry.json、
-- dict_disinfect_method.json 中所有项均为 is_system=true。
-- 接种方式 code 被 t_immune_template_item.vaccinate_method_code、t_drug_catalog.default_vaccinate_method_code
-- 引用；消毒方式 code 被 t_drug_catalog.disinfect_method_codes 引用，属于程序逻辑依赖，管理端不能停用。
-- V2_03 为 3A 的初始化脚本，已执行环境不直接修改，按约定另起新版本脚本。
UPDATE t_dict_item
SET is_system = 1
WHERE dict_type IN ('VACCINATE_METHOD', 'DISINFECT_METHOD');
