package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.FarmMainCategoryReadMapper;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.DictScopes;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 字典查询（API-U10）：只返回启用项；species / category_id 过滤，未传时 PEN_TYPE / VACCINATE_METHOD
 * 按 X-Farm-Id 对应养殖场的主要品类推断畜种（只读 t_farm.main_category_id）。
 */
@Service
@RequiredArgsConstructor
public class DictQueryServiceImpl implements DictQueryService {

    private final DictItemMapper dictItemMapper;
    private final CategoryMapper categoryMapper;
    private final BaseDataReadSupport support;
    private final FarmMainCategoryReadMapper farmMainCategoryReadMapper;

    @Override
    public List<BaseDictItemVO> listForApp(String dictType, String species, Long categoryId, Long farmId) {
        String type = DictTypes.normalize(dictType);
        if (!DictTypes.isKnown(type)) {
            throw BaseErrors.notFound();
        }
        String effectiveSpecies = normalizeSpecies(species);
        Long effectiveCategoryId = categoryId;
        if (categoryId != null) {
            Category category = categoryMapper.selectById(categoryId);
            if (category == null) {
                throw BaseErrors.notFound();
            }
            effectiveSpecies = category.getSpecies();
        } else if (effectiveSpecies == null && farmId != null && DictTypes.SPECIES_SCOPED.contains(type)) {
            effectiveSpecies = speciesOfFarm(farmId);
        }
        List<DictItem> items = dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .eq(DictItem::getStatus, "ENABLED")
                .orderByAsc(DictItem::getSortNo)
                .orderByAsc(DictItem::getId));
        List<Long> ids = items.stream().map(DictItem::getId).toList();
        Map<Long, List<DictItemScope>> scopes = support.scopesByItem(ids);
        List<BaseDictItemVO> result = new ArrayList<>();
        boolean filterByScope = effectiveSpecies != null || effectiveCategoryId != null;
        for (DictItem item : items) {
            if (filterByScope && !DictScopes.matches(scopes.get(item.getId()), effectiveSpecies, effectiveCategoryId)) {
                continue;
            }
            result.add(AdminBaseConvert.baseDictItem(item));
        }
        result.sort(Comparator.comparing(BaseDictItemVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(BaseDictItemVO::getId));
        return result;
    }

    @Override
    public List<DictEntry> listAll(String dictType) {
        String type = DictTypes.normalize(dictType);
        if (!DictTypes.isKnown(type)) {
            throw BaseErrors.notFound();
        }
        List<DictEntry> result = new ArrayList<>();
        for (DictItem item : dictItemMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .orderByAsc(DictItem::getSortNo)
                .orderByAsc(DictItem::getId))) {
            result.add(new DictEntry(item.getId(), item.getDictType(), item.getCode(), item.getName(),
                    item.getDescription(), Boolean.TRUE.equals(item.getRemarkRequired()),
                    Boolean.TRUE.equals(item.getIsSystem()),
                    item.getSortNo() == null ? 0 : item.getSortNo(), "ENABLED".equals(item.getStatus())));
        }
        return result;
    }

    @Override
    public String nameOf(String dictType, String code) {
        if (dictType == null || code == null) {
            return null;
        }
        DictItem item = dictItemMapper.selectOne(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, DictTypes.normalize(dictType))
                .eq(DictItem::getCode, code)
                .last("LIMIT 1"));
        return item == null ? null : item.getName();
    }

    /** 养殖场主要品类 → 畜种；读不到时不过滤（返回全部启用项） */
    private String speciesOfFarm(Long farmId) {
        try {
            Long categoryId = farmMainCategoryReadMapper.selectMainCategoryId(farmId);
            if (categoryId == null) {
                return null;
            }
            Category category = categoryMapper.selectById(categoryId);
            return category == null ? null : category.getSpecies();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalizeSpecies(String species) {
        if (species == null || species.isBlank()) {
            return null;
        }
        return species.trim().toUpperCase();
    }
}
