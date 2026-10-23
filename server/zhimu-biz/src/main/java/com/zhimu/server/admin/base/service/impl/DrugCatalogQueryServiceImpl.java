package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.BannedDrugHit;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.mapper.BannedDrugHitMapper;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogUsageMapper;
import com.zhimu.server.admin.base.mapper.FarmMainCategoryReadMapper;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.admin.base.vo.DrugCatalogItemVO;
import com.zhimu.server.admin.base.vo.DrugCatalogResultVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 兽药疫苗目录查询与禁用药校验（API-U11）。
 * <p>
 * 目录只有 13 条平台初始化数据，搜索在库内按名称/别名过滤后内存分页；禁用药同样在内存中匹配，
 * 避免为 150 条目录建全文索引（管理端-03 2.3.7）。命中禁用药写 t_banned_drug_hit（只追加）。
 */
@Service
@RequiredArgsConstructor
public class DrugCatalogQueryServiceImpl implements DrugCatalogQueryService {

    /** U11 关键词命中禁用药的文案（App catalog_banned.json 逐字一致） */
    private static final String SEARCH_BANNED_SUFFIX = "不能入库";
    /** 模块08/09/11 保存类操作的文案（App err_42203_banned.json） */
    private static final String USE_BANNED_SUFFIX = "不能使用";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int MAX_KEYWORD = 40;
    private static final List<String> SPECIES_ORDER = List.of("PIG", "POULTRY", "CATTLE", "SHEEP");

    private final DrugCatalogMapper drugCatalogMapper;
    private final DrugCatalogUsageMapper drugCatalogUsageMapper;
    private final BannedDrugMapper bannedDrugMapper;
    private final BannedDrugHitMapper bannedDrugHitMapper;
    private final FarmMainCategoryReadMapper farmMainCategoryReadMapper;
    private final CategoryQueryService categoryQueryService;

    @Override
    public DrugCatalogResultVO search(String keyword, String species, String drugCategory, int page, int size) {
        String category = normalizeCategory(drugCategory);
        if (category == null) {
            throw BaseErrors.param("请选择药品类别");
        }
        String search = normalizeKeyword(keyword);
        DrugCatalogResultVO result = new DrugCatalogResultVO();
        List<BannedDrug> banned = listBannedDrugs();
        // 未传 species 时按当前养殖场主要品类解析（管理端-03 API-U11）；目录搜索本身不按畜种过滤，
        // 因为 App catalog_vet.json 包含不适用于猪的 7205，是否适用由模块08 的 usage.applicable 提示。
        resolveSpecies(species);
        if (search != null) {
            BannedDrug hit = findBanned(banned, search);
            if (hit != null) {
                // 关键词命中禁用药：不返回目录项，只返回 banned_hit，并写拦截记录（管理端-03 API-U11）
                recordHit(hit, "SEARCH", search, null);
                result.setBannedHit(hitVO(hit, SEARCH_BANNED_SUFFIX));
                return result;
            }
        }

        List<DrugCatalog> catalogs = drugCatalogMapper.selectList(Wrappers.<DrugCatalog>lambdaQuery()
                .eq(DrugCatalog::getStatus, "ENABLED")
                .eq(DrugCatalog::getDrugCategory, category)
                .and(search != null, wrapper -> wrapper
                        .like(DrugCatalog::getName, search)
                        .or()
                        .like(DrugCatalog::getAlias, search))
                .orderByAsc(DrugCatalog::getId));
        List<DrugCatalog> visible = new ArrayList<>();
        for (DrugCatalog catalog : catalogs) {
            if (!"ENABLED".equals(catalog.getStatus()) || !category.equals(catalog.getDrugCategory())) {
                continue;
            }
            if (Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null) {
                continue;
            }
            // 目录行未打禁用药标记但名称/别名命中禁用药清单时也不返回（防御初始化数据缺失）
            if (findBanned(banned, catalog.getName()) != null || findBanned(banned, catalog.getAlias()) != null) {
                continue;
            }
            visible.add(catalog);
        }

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizeSize(size);
        long from = (long) (normalizedPage - 1) * normalizedSize;
        int start = from >= visible.size() ? visible.size() : (int) from;
        int end = Math.min(start + normalizedSize, visible.size());
        for (DrugCatalog catalog : visible.subList(start, end)) {
            result.getItems().add(itemVO(catalog));
        }
        return result;
    }

    @Override
    public DrugCatalogInfo requireCatalog(Long id) {
        DrugCatalog catalog = id == null ? null : drugCatalogMapper.selectById(id);
        if (catalog == null) {
            throw BaseErrors.notFound();
        }
        return info(catalog);
    }

    @Override
    public boolean isBanned(Long catalogId, String species) {
        DrugCatalog catalog = catalogId == null ? null : drugCatalogMapper.selectById(catalogId);
        if (catalog == null) {
            return false;
        }
        // 禁用药按药品全局判定，不区分畜种；species 预留给后续按畜种扩展的禁用范围
        if (Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null) {
            return true;
        }
        List<BannedDrug> banned = listBannedDrugs();
        return findBanned(banned, catalog.getName()) != null || findBanned(banned, catalog.getAlias()) != null;
    }

    @Override
    public BannedDrugHitVO checkBanned(String text) {
        BannedDrug hit = findBanned(listBannedDrugs(), text);
        return hit == null ? null : hitVO(hit, USE_BANNED_SUFFIX);
    }

    @Override
    public List<BannedDrugHitVO> checkBannedDrugs(String text) {
        List<BannedDrugHitVO> result = new ArrayList<>();
        String value = normalizeText(text);
        if (value == null) {
            return result;
        }
        for (BannedDrug drug : listBannedDrugs()) {
            if (matches(drug, value)) {
                result.add(hitVO(drug, USE_BANNED_SUFFIX));
            }
        }
        return result;
    }

    @Override
    public void assertNotBanned(Long catalogId, String species) {
        DrugCatalog catalog = catalogId == null ? null : drugCatalogMapper.selectById(catalogId);
        if (catalog == null) {
            throw BaseErrors.notFound();
        }
        if (Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null) {
            throw bannedException(catalog.getName());
        }
        BannedDrug hit = findBanned(listBannedDrugs(), catalog.getName());
        if (hit == null) {
            hit = findBanned(listBannedDrugs(), catalog.getAlias());
        }
        if (hit != null) {
            throw bannedException(hit.getName());
        }
    }

    @Override
    public List<DrugCatalogUsage> usages(Long catalogId) {
        if (catalogId == null) {
            return List.of();
        }
        List<DrugCatalogUsage> usages = new ArrayList<>(drugCatalogUsageMapper.selectList(
                Wrappers.<DrugCatalogUsage>lambdaQuery().eq(DrugCatalogUsage::getCatalogId, catalogId)));
        usages.sort(Comparator.comparingInt((DrugCatalogUsage usage) -> {
            int index = SPECIES_ORDER.indexOf(usage.getSpecies());
            return index < 0 ? SPECIES_ORDER.size() : index;
        }));
        return usages;
    }

    @Override
    public DrugCatalogUsage usage(Long catalogId, String species) {
        String normalized = normalizeSpecies(species);
        if (catalogId == null || normalized == null || !SPECIES_ORDER.contains(normalized)) {
            return null;
        }
        return drugCatalogUsageMapper.selectOne(Wrappers.<DrugCatalogUsage>lambdaQuery()
                .eq(DrugCatalogUsage::getCatalogId, catalogId)
                .eq(DrugCatalogUsage::getSpecies, normalized)
                .last("LIMIT 1"));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private List<BannedDrug> listBannedDrugs() {
        List<BannedDrug> list = new ArrayList<>(bannedDrugMapper.selectList(Wrappers.<BannedDrug>lambdaQuery()
                .orderByAsc(BannedDrug::getSortNo).orderByAsc(BannedDrug::getId)));
        list.sort(Comparator.comparing(BannedDrug::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(BannedDrug::getId));
        return list;
    }

    /**
     * 文本命中第一条禁用药：名称或别名（顿号分隔）与文本互相包含。
     * 例：搜索“克仑特罗”命中 盐酸克仑特罗；“瘦肉精”命中别名。
     */
    private static BannedDrug findBanned(List<BannedDrug> banned, String text) {
        String value = normalizeText(text);
        if (value == null) {
            return null;
        }
        for (BannedDrug drug : banned) {
            if (matches(drug, value)) {
                return drug;
            }
        }
        return null;
    }

    private static boolean matches(BannedDrug drug, String text) {
        for (String term : terms(drug)) {
            if (term.length() >= 2 && (text.contains(term) || term.contains(text))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> terms(BannedDrug drug) {
        List<String> terms = new ArrayList<>();
        if (StringUtils.hasText(drug.getName())) {
            terms.add(drug.getName().trim());
        }
        if (StringUtils.hasText(drug.getAlias())) {
            for (String alias : drug.getAlias().split("[、,，;；]")) {
                if (StringUtils.hasText(alias)) {
                    terms.add(alias.trim());
                }
            }
        }
        return terms;
    }

    private void recordHit(BannedDrug drug, String source, String keyword, Long refId) {
        BannedDrugHit hit = new BannedDrugHit();
        hit.setBannedDrugId(drug.getId());
        hit.setHitSource(source);
        hit.setFarmId(FarmContext.farmId());
        hit.setUserId(UserContext.userId());
        hit.setKeyword(truncate(keyword, 40));
        hit.setRefId(refId);
        hit.setHitAt(BeijingTime.now());
        bannedDrugHitMapper.insert(hit);
    }

    private static BizException bannedException(String name) {
        String message = StringUtils.hasText(name)
                ? name + "为禁用药，" + USE_BANNED_SUFFIX
                : ErrorCode.BANNED_DRUG.getDefaultMessage();
        return new BizException(ErrorCode.BANNED_DRUG, message);
    }

    private static BannedDrugHitVO hitVO(BannedDrug drug, String suffix) {
        return BannedDrugHitVO.of(drug.getId(), drug.getName(), drug.getName() + "为禁用药，" + suffix);
    }

    private static DrugCatalogItemVO itemVO(DrugCatalog catalog) {
        DrugCatalogItemVO vo = new DrugCatalogItemVO();
        vo.setId(catalog.getId());
        vo.setName(catalog.getName());
        vo.setAlias(catalog.getAlias());
        vo.setDrugCategory(catalog.getDrugCategory());
        vo.setSpec(catalog.getSpec());
        vo.setMeasureUnit(catalog.getMeasureUnit());
        vo.setPackUnit(catalog.getPackUnit());
        vo.setPackSize(catalog.getPackSize());
        return vo;
    }

    private static DrugCatalogInfo info(DrugCatalog catalog) {
        return new DrugCatalogInfo(catalog.getId(), catalog.getDrugCategory(), catalog.getName(),
                catalog.getAlias(), catalog.getDosageForm(), catalog.getSpec(), catalog.getMeasureUnit(),
                catalog.getPackUnit(), catalog.getPackSize(), catalog.getVaccineDoseUnit(),
                catalog.getDefaultVaccinateMethodCode(), catalog.getStatus(),
                Boolean.TRUE.equals(catalog.getBanned()), catalog.getBannedDrugId());
    }

    /** 未传 species 时按当前养殖场主要品类推断（与 U10 一致）；无上下文/读不到返回 null */
    private String resolveSpecies(String species) {
        String normalized = normalizeSpecies(species);
        if (normalized != null) {
            return normalized;
        }
        Long farmId = FarmContext.farmId();
        if (farmId == null) {
            return null;
        }
        Long categoryId = farmMainCategoryReadMapper.selectMainCategoryId(farmId);
        if (categoryId == null) {
            return null;
        }
        CategoryInfo category = categoryQueryService.info(categoryId);
        return category == null ? null : category.species();
    }

    private static String normalizeCategory(String drugCategory) {
        if (!StringUtils.hasText(drugCategory)) {
            return null;
        }
        return drugCategory.trim().toUpperCase();
    }

    private static String normalizeSpecies(String species) {
        if (!StringUtils.hasText(species)) {
            return null;
        }
        return species.trim().toUpperCase();
    }

    private static String normalizeKeyword(String keyword) {
        String value = normalizeText(keyword);
        if (value != null && value.length() > MAX_KEYWORD) {
            throw BaseErrors.field("keyword", "搜索关键字最多 40 个字");
        }
        return value;
    }

    private static String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
