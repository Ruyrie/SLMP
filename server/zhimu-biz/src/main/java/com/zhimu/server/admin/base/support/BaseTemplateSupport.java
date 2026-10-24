package com.zhimu.server.admin.base.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.DictItemScopeMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.query.IdNameRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WA-D02 / D03 / D04 公共校验与读取：启用品种/品类、疫苗可用性、接种方式适用范围、管理员姓名。
 * 跨模块统计走 BaseAdminReadMapper 的只读 SQL；疫苗可用性复用 DrugCatalogQueryService（与 U11 同口径）。
 */
@Component
@RequiredArgsConstructor
public class BaseTemplateSupport {

    private final CategoryMapper categoryMapper;
    private final BreedMapper breedMapper;
    private final DictItemMapper dictItemMapper;
    private final DictItemScopeMapper dictItemScopeMapper;
    private final DrugCatalogMapper drugCatalogMapper;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final DictQueryService dictQueryService;
    private final BaseAdminReadMapper readMapper;

    public Category category(Long id) {
        return id == null ? null : categoryMapper.selectById(id);
    }

    public Category requireCategory(Long id) {
        Category category = category(id);
        if (category == null) {
            throw BaseErrors.notFound();
        }
        return category;
    }

    public Category requireEnabledCategory(Long id) {
        Category category = requireCategory(id);
        if (!"ENABLED".equals(category.getStatus())) {
            throw BaseErrors.field("category_id", "请选择启用的品类");
        }
        return category;
    }

    public Breed breed(Long id) {
        return id == null ? null : breedMapper.selectById(id);
    }

    public Breed requireEnabledBreed(Long id) {
        Breed breed = breed(id);
        if (breed == null) {
            throw BaseErrors.field("breed_id", "请选择品种");
        }
        if (!"ENABLED".equals(breed.getStatus())) {
            throw BaseErrors.field("breed_id", "请选择启用的品种");
        }
        return breed;
    }

    /** 品种所属品类的畜种；读不到返回 null */
    public String speciesOfBreed(Long breedId) {
        Breed breed = breed(breedId);
        if (breed == null) {
            return null;
        }
        Category category = category(breed.getCategoryId());
        return category == null ? null : category.getSpecies();
    }

    /** 字典类型下 code → 名称（含停用；导出/列表文案用） */
    public Map<String, String> dictNames(String dictType) {
        Map<String, String> map = new HashMap<>();
        for (DictQueryService.DictEntry entry : dictQueryService.listAll(dictType)) {
            map.put(entry.code(), entry.name());
        }
        return map;
    }

    public String dictName(String dictType, String code) {
        return code == null ? null : dictQueryService.nameOf(dictType, code);
    }

    /** 字典项启用且适用于该畜种；不满足时抛字段错误 */
    public DictItem requireApplicableDict(String dictType, String code, String species, String field,
                                          String message) {
        if (!StringUtils.hasText(code)) {
            throw BaseErrors.field(field, message);
        }
        DictItem item = dictItemMapper.selectOne(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, dictType)
                .eq(DictItem::getCode, code.trim().toUpperCase())
                .last("LIMIT 1"));
        if (item == null || !"ENABLED".equals(item.getStatus())) {
            throw BaseErrors.field(field, message);
        }
        List<DictItemScope> scopes = dictItemScopeMapper.selectList(Wrappers.<DictItemScope>lambdaQuery()
                .eq(DictItemScope::getDictItemId, item.getId()));
        if (!DictScopes.matches(scopes, species, null)) {
            throw BaseErrors.field(field, message);
        }
        return item;
    }

    /**
     * 疫苗必须存在、属于疫苗类别、启用、未列入禁用药，且该畜种 applicable = 1。
     * 禁用药返回 42203（与模块08/09 同文案）。
     */
    public DrugCatalogUsage requireVaccine(Long vaccineId, String species) {
        DrugCatalog catalog = vaccineId == null ? null : drugCatalogMapper.selectById(vaccineId);
        if (catalog == null || !"VACCINE".equals(catalog.getDrugCategory())) {
            throw BaseErrors.field("vaccine_id", "请选择疫苗");
        }
        if (!"ENABLED".equals(catalog.getStatus())) {
            throw BaseErrors.field("vaccine_id", "所选疫苗已停用，请重新选择");
        }
        if (drugCatalogQueryService.isBanned(vaccineId, species)) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.BANNED_DRUG, "该药品属于禁用兽药，不能使用");
        }
        DrugCatalogUsage usage = drugCatalogQueryService.usage(vaccineId, species);
        if (usage == null || !Boolean.TRUE.equals(usage.getApplicable())) {
            throw BaseErrors.field("vaccine_id", "所选疫苗不适用于该畜种");
        }
        return usage;
    }

    /** 管理员 ID → 姓名；用于 published_by_name / listed_by_name */
    public Map<Long, String> adminNames() {
        Map<Long, String> map = new HashMap<>();
        for (IdNameRow row : readMapper.adminNames()) {
            map.put(row.getId(), row.getName());
        }
        return map;
    }
}
