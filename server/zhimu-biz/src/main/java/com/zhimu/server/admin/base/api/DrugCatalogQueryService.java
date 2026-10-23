package com.zhimu.server.admin.base.api;

import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.admin.base.vo.DrugCatalogResultVO;

import java.util.List;

/**
 * 兽药疫苗目录查询（API-U11）与禁用药校验。跨模块（模块08/09/11）只通过本接口读目录、禁用药，不直接读 Mapper。
 * <p>
 * 禁用药口径：t_drug_catalog.banned / banned_drug_id 标记，或目录名称、别名命中 t_banned_drug 名称、别名。
 * 命中时模块08 返回 42203、模块09 写 t_banned_drug_hit 并返回 42203；U11 只返回 banned_hit 提示、不返回 items。
 */
public interface DrugCatalogQueryService {

    /**
     * API-U11 目录搜索。
     *
     * @param keyword      名称或别名包含匹配，可空，最长 40 字；命中禁用药时 items 为空并返回 banned_hit
     * @param species      畜种（PIG / POULTRY / CATTLE / SHEEP），可空；为空时取当前养殖场主要品类的畜种
     * @param drugCategory 类别（VET_DRUG / VACCINE / DISINFECTANT），必填
     * @param page         页码，从 1 开始，默认 1
     * @param size         每页条数，默认 20、最大 50
     */
    DrugCatalogResultVO search(String keyword, String species, String drugCategory, int page, int size);

    /** 按目录 ID 取；不存在抛 40401。停用目录仍可取出（模块09 已建档继续使用） */
    DrugCatalogInfo requireCatalog(Long id);

    /** 目录项是否已列入禁用药；目录不存在返回 false */
    boolean isBanned(Long catalogId, String species);

    /** 文本中是否命中禁用药（名称、别名包含匹配），返回第一条命中；未命中返回 null，不写拦截记录 */
    BannedDrugHitVO checkBanned(String text);

    /** 文本中全部禁用药命中（模块11 AI 问答自查用），未命中返回空列表 */
    List<BannedDrugHitVO> checkBannedDrugs(String text);

    /** 目录项命中禁用药时抛 42203（模块08 保存接种/用药记录、模块09 建档/入库前调用） */
    void assertNotBanned(Long catalogId, String species);

    /** 某目录项全部畜种用量与休药期，未配置返回空列表 */
    List<DrugCatalogUsage> usages(Long catalogId);

    /** 某目录项某畜种的用量；未配置返回 null */
    DrugCatalogUsage usage(Long catalogId, String species);
}
