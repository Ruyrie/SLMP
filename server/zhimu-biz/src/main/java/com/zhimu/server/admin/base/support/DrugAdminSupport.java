package com.zhimu.server.admin.base.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.request.DrugCatalogSaveRequest;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WA-D03 公共校验：类别、单位、剂量、休药期、禁用药同名、字典项启用。
 * 兽药 / 疫苗四个畜种各一行；消毒剂不设用量行（管理端-03 2.3.7）。
 */
@Component
@RequiredArgsConstructor
public class DrugAdminSupport {

    public static final Set<String> CATEGORIES = Set.of("VET_DRUG", "VACCINE", "DISINFECTANT");
    public static final List<String> SPECIES = List.of("PIG", "POULTRY", "CATTLE", "SHEEP");
    public static final Set<String> MEASURE_UNITS = Set.of("G", "ML", "L", "DOSE", "KG");
    public static final Set<String> VACCINE_DOSE_UNITS = Set.of("DOSE_PER_HEAD", "ML_PER_HEAD", "DOSE_PER_BIRD");
    public static final Set<String> DRUG_DOSE_UNITS = Set.of("G_PER_T_FEED", "G_PER_L_WATER", "ML_PER_10KG",
            "MG_PER_KG", "ML_PER_HEAD", "G_PER_HEAD");
    public static final Set<String> ADMIN_ROUTES = Set.of("MIX_FEED", "MIX_WATER", "IM", "SC", "IV", "ORAL",
            "EXTERNAL");

    private final DrugCatalogMapper catalogMapper;
    private final BannedDrugMapper bannedDrugMapper;
    private final DictItemMapper dictItemMapper;
    private final DictQueryService dictQueryService;

    public DrugCatalog requireCatalog(Long id) {
        DrugCatalog catalog = id == null ? null : catalogMapper.selectById(id);
        if (catalog == null) {
            throw BaseErrors.notFound();
        }
        return catalog;
    }

    public String requireCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw BaseErrors.field("drug_category", "请选择类别");
        }
        String value = category.trim().toUpperCase();
        if (!CATEGORIES.contains(value)) {
            throw BaseErrors.field("drug_category", "请选择正确的类别");
        }
        return value;
    }

    /** 目录名称不能与禁用药名称或别名相同 */
    public void assertNameNotBanned(String name) {
        for (BannedDrug banned : bannedDrugMapper.selectList(Wrappers.lambdaQuery())) {
            if (name.equals(banned.getName()) || terms(banned.getAlias()).contains(name)) {
                throw new BizException(ErrorCode.BANNED_DRUG, banned.getName() + "为禁用药，不能加入目录");
            }
        }
    }

    public void assertEnableAllowed(DrugCatalog catalog) {
        if (Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null) {
            throw new BizException(ErrorCode.BANNED_DRUG, "该药品已列为禁用药，不能启用");
        }
        for (BannedDrug banned : bannedDrugMapper.selectList(Wrappers.lambdaQuery())) {
            if (catalog.getName().equals(banned.getName())
                    || terms(banned.getAlias()).contains(catalog.getName())) {
                throw new BizException(ErrorCode.BANNED_DRUG, "该药品已列为禁用药，不能启用");
            }
        }
    }

    public Map<String, String> dictNames(String dictType) {
        Map<String, String> map = new HashMap<>();
        for (DictQueryService.DictEntry entry : dictQueryService.listAll(dictType)) {
            map.put(entry.code(), entry.name());
        }
        return map;
    }

    public void requireDictEnabled(String dictType, String code, String field, String message) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        DictItem item = dictItemMapper.selectOne(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, dictType)
                .eq(DictItem::getCode, code.trim().toUpperCase())
                .last("LIMIT 1"));
        if (item == null || !"ENABLED".equals(item.getStatus())) {
            throw BaseErrors.field(field, message);
        }
    }

    /**
     * 校验并返回按固定畜种顺序的用量实体（消毒剂返回空列表）。
     *
     * @param category 已规范化的类别
     */
    public List<DrugCatalogUsage> usages(DrugCatalogSaveRequest request, String category) {
        List<DrugCatalogUsage> result = new ArrayList<>();
        if ("DISINFECTANT".equals(category)) {
            return result;
        }
        if (request.getUsages() == null || request.getUsages().isEmpty()) {
            throw BaseErrors.field("usages", "请填写四个畜种的用量与休药期");
        }
        Map<String, DrugCatalogSaveRequest.Usage> bySpecies = new HashMap<>();
        for (DrugCatalogSaveRequest.Usage usage : request.getUsages()) {
            String species = usage.getSpecies() == null ? null : usage.getSpecies().trim().toUpperCase();
            if (!SPECIES.contains(species)) {
                throw BaseErrors.field("usages", "请选择正确的畜种");
            }
            if (bySpecies.put(species, usage) != null) {
                throw BaseErrors.field("usages", "畜种不能重复");
            }
        }
        if (!bySpecies.keySet().equals(new HashSet<>(SPECIES))) {
            throw BaseErrors.field("usages", "请填写四个畜种的用量与休药期");
        }
        for (String species : SPECIES) {
            DrugCatalogSaveRequest.Usage usage = bySpecies.get(species);
            if (!Boolean.TRUE.equals(usage.getApplicable())) {
                DrugCatalogUsage entity = new DrugCatalogUsage();
                entity.setSpecies(species);
                entity.setApplicable(false);
                entity.setWithdrawalDays(usage.getWithdrawalDays());
                result.add(entity);
                continue;
            }
            String route = usage.getAdminRoute() == null ? null : usage.getAdminRoute().trim().toUpperCase();
            if (!ADMIN_ROUTES.contains(route)) {
                throw BaseErrors.field("usages", "请选择给药方式");
            }
            if (usage.getRecommendedDose() == null || usage.getRecommendedDose().signum() <= 0) {
                throw BaseErrors.field("usages", "推荐剂量必须大于 0");
            }
            if (usage.getRecommendedDose().scale() > 3) {
                throw BaseErrors.field("usages", "推荐剂量最多 3 位小数");
            }
            String doseUnit = usage.getDoseUnit() == null ? null : usage.getDoseUnit().trim().toUpperCase();
            boolean validUnit = "VACCINE".equals(category) ? VACCINE_DOSE_UNITS.contains(doseUnit)
                    : DRUG_DOSE_UNITS.contains(doseUnit);
            if (!validUnit) {
                throw BaseErrors.field("usages", "请选择剂量单位");
            }
            if (usage.getCourseDays() == null || usage.getCourseDays() < 1 || usage.getCourseDays() > 60) {
                throw BaseErrors.field("usages", "推荐疗程必须在 1–60 天之间");
            }
            if (usage.getWithdrawalDays() == null || usage.getWithdrawalDays() < 0
                    || usage.getWithdrawalDays() > 999) {
                throw BaseErrors.field("usages", "休药期必须在 0–999 天之间");
            }
            DrugCatalogUsage entity = new DrugCatalogUsage();
            entity.setSpecies(species);
            entity.setApplicable(true);
            entity.setAdminRoute(route);
            entity.setRecommendedDose(usage.getRecommendedDose());
            entity.setDoseUnit(doseUnit);
            entity.setCourseDays(usage.getCourseDays());
            entity.setWithdrawalDays(usage.getWithdrawalDays());
            result.add(entity);
        }
        return result;
    }

    /** 基础字段校验（与是否已有档案无关） */
    public void validateBase(DrugCatalogSaveRequest request, String category) {
        String packUnit = request.getPackUnit() == null ? null : request.getPackUnit().trim();
        if (packUnit == null || packUnit.length() < 1 || packUnit.length() > 4) {
            throw BaseErrors.field("pack_unit", "请填写包装单位");
        }
        if (request.getPackSize() == null || request.getPackSize().signum() <= 0) {
            throw BaseErrors.field("pack_size", "每个包装折合数量必须大于 0");
        }
        if (request.getPackSize().scale() > 3) {
            throw BaseErrors.field("pack_size", "每个包装折合数量最多 3 位小数");
        }
        String measure = request.getMeasureUnit() == null ? null : request.getMeasureUnit().trim().toUpperCase();
        if (!MEASURE_UNITS.contains(measure)) {
            throw BaseErrors.field("measure_unit", "请选择记账单位");
        }
        if ("VACCINE".equals(category)) {
            if (!"DOSE".equals(measure)) {
                throw BaseErrors.field("measure_unit", "疫苗记账单位须为头份或羽份");
            }
            String doseUnit = request.getVaccineDoseUnit() == null ? null
                    : request.getVaccineDoseUnit().trim().toUpperCase();
            if (!VACCINE_DOSE_UNITS.contains(doseUnit)) {
                throw BaseErrors.field("vaccine_dose_unit", "请选择疫苗剂量单位");
            }
            requireDictEnabled(DictTypes.VACCINATE_METHOD, request.getDefaultVaccinateMethodCode(),
                    "default_vaccinate_method_code", "请选择正确的接种方式");
        }
        if ("DISINFECTANT".equals(category)) {
            if (request.getConcentration() != null && request.getConcentration().length() > 20) {
                throw BaseErrors.field("concentration", "推荐浓度最多 20 个字");
            }
            for (String code : request.getDisinfectMethodCodes() == null ? List.<String>of()
                    : request.getDisinfectMethodCodes()) {
                requireDictEnabled(DictTypes.DISINFECT_METHOD, code, "disinfect_method_codes",
                        "请选择正确的消毒方式");
            }
        }
    }

    public static BigDecimal normalizedDose(BigDecimal dose) {
        return dose == null ? null : dose.stripTrailingZeros();
    }

    private static Set<String> terms(String alias) {
        Set<String> terms = new HashSet<>();
        if (alias == null) {
            return terms;
        }
        for (String term : Arrays.stream(alias.split("[、,，;；]")).map(String::trim).toList()) {
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        return terms;
    }
}
