package com.zhimu.server.admin.base.convert;

import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.entity.DrugLotRecall;
import com.zhimu.server.admin.base.query.DrugLotRecallRow;
import com.zhimu.server.admin.base.query.RecallFarmRow;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.vo.BannedDrugVO;
import com.zhimu.server.admin.base.vo.DrugCatalogDetailVO;
import com.zhimu.server.admin.base.vo.DrugCatalogRowVO;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.DrugUsageVO;
import com.zhimu.server.admin.base.vo.RecallFarmVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 兽药疫苗目录、禁用药、批号停用实体 / 只读行 → VO。 */
public final class DrugAdminConvert {

    private static final List<String> SPECIES_ORDER = List.of("PIG", "POULTRY", "CATTLE", "SHEEP");

    private DrugAdminConvert() {
    }

    public static DrugUsageVO usage(DrugCatalogUsage usage) {
        if (usage == null) {
            return null;
        }
        DrugUsageVO vo = new DrugUsageVO();
        vo.setSpecies(usage.getSpecies());
        vo.setSpeciesText(BaseAdminTexts.species(usage.getSpecies()));
        vo.setApplicable(Boolean.TRUE.equals(usage.getApplicable()));
        vo.setAdminRoute(usage.getAdminRoute());
        vo.setAdminRouteText(BaseAdminTexts.adminRoute(usage.getAdminRoute()));
        vo.setRecommendedDose(usage.getRecommendedDose());
        vo.setDoseUnit(usage.getDoseUnit());
        vo.setDoseUnitText(BaseAdminTexts.drugDoseUnit(usage.getDoseUnit()));
        vo.setCourseDays(usage.getCourseDays());
        vo.setWithdrawalDays(usage.getWithdrawalDays());
        return vo;
    }

    public static List<DrugUsageVO> usages(List<DrugCatalogUsage> usages) {
        List<DrugCatalogUsage> sorted = new ArrayList<>(usages == null ? List.of() : usages);
        sorted.sort(Comparator.comparingInt(usage -> {
            int index = SPECIES_ORDER.indexOf(usage.getSpecies());
            return index < 0 ? SPECIES_ORDER.size() : index;
        }));
        List<DrugUsageVO> result = new ArrayList<>();
        for (DrugCatalogUsage usage : sorted) {
            result.add(usage(usage));
        }
        return result;
    }

    public static List<DrugCatalogRowVO.DisinfectMethodVO> disinfectMethods(String codes,
                                                                            Map<String, String> names) {
        List<DrugCatalogRowVO.DisinfectMethodVO> result = new ArrayList<>();
        if (codes == null || codes.isBlank()) {
            return result;
        }
        for (String raw : codes.split(",")) {
            String code = raw.trim();
            if (code.isEmpty()) {
                continue;
            }
            DrugCatalogRowVO.DisinfectMethodVO vo = new DrugCatalogRowVO.DisinfectMethodVO();
            vo.setCode(code);
            vo.setName(names == null ? null : names.getOrDefault(code, code));
            result.add(vo);
        }
        return result;
    }

    public static DrugCatalogRowVO row(DrugCatalog catalog, List<DrugCatalogUsage> usages,
                                       int archiveFarmCount, Map<String, String> methodNames,
                                       Map<String, String> disinfectNames, String lastChangedByName) {
        DrugCatalogRowVO vo = new DrugCatalogRowVO();
        fillCommon(vo, catalog, usages, methodNames, disinfectNames);
        vo.setArchiveFarmCount(archiveFarmCount);
        vo.setLastChangedByName(lastChangedByName);
        vo.setLastChangedAt(com.zhimu.server.common.util.BeijingTime.format(catalog.getUpdatedAt()));
        return vo;
    }

    public static DrugCatalogDetailVO detail(DrugCatalog catalog, List<DrugCatalogUsage> usages,
                                             int archiveFarmCount, int activeBatchUsedCount,
                                             Map<String, String> methodNames,
                                             Map<String, String> disinfectNames, String lastChangedByName) {
        DrugCatalogDetailVO vo = new DrugCatalogDetailVO();
        vo.setId(catalog.getId());
        vo.setDrugCategory(catalog.getDrugCategory());
        vo.setDrugCategoryText(BaseAdminTexts.drugCategory(catalog.getDrugCategory()));
        vo.setName(catalog.getName());
        vo.setAlias(catalog.getAlias());
        vo.setDosageForm(catalog.getDosageForm());
        vo.setSpec(catalog.getSpec());
        vo.setMeasureUnit(catalog.getMeasureUnit());
        vo.setMeasureUnitText(BaseAdminTexts.measureUnit(catalog.getMeasureUnit()));
        vo.setPackUnit(catalog.getPackUnit());
        vo.setPackSize(catalog.getPackSize());
        vo.setVaccineDoseUnit(catalog.getVaccineDoseUnit());
        vo.setVaccineDoseUnitText(BaseAdminTexts.vaccineDoseUnit(catalog.getVaccineDoseUnit()));
        vo.setDefaultVaccinateMethodCode(catalog.getDefaultVaccinateMethodCode());
        vo.setDefaultVaccinateMethodText(catalog.getDefaultVaccinateMethodCode() == null ? null
                : methodNames == null ? null : methodNames.get(catalog.getDefaultVaccinateMethodCode()));
        vo.setConcentration(catalog.getConcentration());
        vo.setDisinfectMethods(disinfectMethods(catalog.getDisinfectMethodCodes(), disinfectNames));
        vo.setUsages(usages(usages));
        vo.setStatus(catalog.getStatus());
        vo.setStatusText(AdminBaseConvert.statusText(catalog.getStatus()));
        vo.setDisableReason(catalog.getDisableReason());
        vo.setBanned(Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null);
        vo.setArchiveFarmCount(archiveFarmCount);
        vo.setLastChangeNote(catalog.getLastChangeNote());
        vo.setLastChangedByName(lastChangedByName);
        vo.setLastChangedAt(catalog.getUpdatedAt());
        vo.setActiveBatchUsedCount(activeBatchUsedCount);
        vo.setVersion(catalog.getVersion());
        vo.setSortNo(catalog.getSortNo());
        return vo;
    }

    private static void fillCommon(DrugCatalogRowVO vo, DrugCatalog catalog, List<DrugCatalogUsage> usages,
                                   Map<String, String> methodNames, Map<String, String> disinfectNames) {
        vo.setId(catalog.getId());
        vo.setDrugCategory(catalog.getDrugCategory());
        vo.setDrugCategoryText(BaseAdminTexts.drugCategory(catalog.getDrugCategory()));
        vo.setName(catalog.getName());
        vo.setAlias(catalog.getAlias());
        vo.setDosageForm(catalog.getDosageForm());
        vo.setSpec(catalog.getSpec());
        vo.setMeasureUnit(catalog.getMeasureUnit());
        vo.setMeasureUnitText(BaseAdminTexts.measureUnit(catalog.getMeasureUnit()));
        vo.setPackUnit(catalog.getPackUnit());
        vo.setPackSize(catalog.getPackSize());
        vo.setVaccineDoseUnit(catalog.getVaccineDoseUnit());
        vo.setVaccineDoseUnitText(BaseAdminTexts.vaccineDoseUnit(catalog.getVaccineDoseUnit()));
        vo.setDefaultVaccinateMethodCode(catalog.getDefaultVaccinateMethodCode());
        vo.setDefaultVaccinateMethodText(catalog.getDefaultVaccinateMethodCode() == null ? null
                : methodNames == null ? null : methodNames.get(catalog.getDefaultVaccinateMethodCode()));
        vo.setConcentration(catalog.getConcentration());
        vo.setDisinfectMethods(disinfectMethods(catalog.getDisinfectMethodCodes(), disinfectNames));
        vo.setUsages(usages(usages));
        vo.setStatus(catalog.getStatus());
        vo.setStatusText(AdminBaseConvert.statusText(catalog.getStatus()));
        vo.setBanned(Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null);
        vo.setSortNo(catalog.getSortNo());
    }

    public static BannedDrugVO banned(BannedDrug drug, String listedByName) {
        BannedDrugVO vo = new BannedDrugVO();
        vo.setId(drug.getId());
        vo.setName(drug.getName());
        vo.setAlias(drug.getAlias());
        vo.setBannedClass(drug.getBannedClass());
        vo.setBannedClassText(BaseAdminTexts.bannedClass(drug.getBannedClass()));
        vo.setCatalogId(drug.getCatalogId());
        vo.setReason(drug.getReason());
        vo.setListedAt(drug.getListedAt());
        vo.setListedByName(listedByName);
        vo.setHitCount30d(drug.getHitCount30d());
        vo.setVersion(drug.getVersion());
        return vo;
    }

    public static DrugLotRecallVO recall(DrugLotRecall recall, DrugCatalog catalog, int scrappedFarmCount,
                                         String recalledByName) {
        DrugLotRecallVO vo = new DrugLotRecallVO();
        vo.setId(recall.getId());
        vo.setCatalogId(recall.getCatalogId());
        vo.setCatalogName(catalog == null ? null : catalog.getName());
        vo.setDrugCategory(catalog == null ? null : catalog.getDrugCategory());
        vo.setDrugCategoryText(catalog == null ? null : BaseAdminTexts.drugCategory(catalog.getDrugCategory()));
        vo.setLotNo(recall.getLotNo());
        vo.setExpireDate(recall.getExpireDate());
        vo.setReason(recall.getReason());
        vo.setFarmCount(recall.getFarmCount());
        vo.setTotalQty(recall.getTotalQty());
        vo.setQtyUnit(recall.getQtyUnit());
        vo.setQtyUnitText(BaseAdminTexts.measureUnit(recall.getQtyUnit()));
        vo.setScrappedFarmCount(scrappedFarmCount);
        vo.setRecalledAt(recall.getRecalledAt());
        vo.setRecalledByName(recalledByName);
        return vo;
    }

    public static DrugLotRecallVO recall(DrugLotRecallRow row, String recalledByName) {
        DrugLotRecallVO vo = new DrugLotRecallVO();
        vo.setId(row.getId());
        vo.setCatalogId(row.getCatalogId());
        vo.setCatalogName(row.getCatalogName());
        vo.setDrugCategory(row.getDrugCategory());
        vo.setDrugCategoryText(BaseAdminTexts.drugCategory(row.getDrugCategory()));
        vo.setLotNo(row.getLotNo());
        vo.setExpireDate(row.getExpireDate());
        vo.setReason(row.getReason());
        vo.setFarmCount(row.getFarmCount());
        vo.setTotalQty(row.getTotalQty());
        vo.setQtyUnit(row.getQtyUnit());
        vo.setQtyUnitText(BaseAdminTexts.measureUnit(row.getQtyUnit()));
        vo.setScrappedFarmCount(row.getScrappedFarmCount());
        vo.setRecalledAt(row.getRecalledAt());
        vo.setRecalledByName(recalledByName);
        return vo;
    }

    public static RecallFarmVO recallFarm(RecallFarmRow row) {
        RecallFarmVO vo = new RecallFarmVO();
        vo.setFarmId(row.getFarmId());
        vo.setFarmName(row.getFarmName());
        vo.setQtyAtRecall(row.getQtyAtRecall());
        vo.setQtyUnit(row.getQtyUnit());
        vo.setQtyUnitText(BaseAdminTexts.measureUnit(row.getQtyUnit()));
        vo.setScrapStatus(row.getScrapStatus());
        vo.setScrapStatusText(BaseAdminTexts.scrapStatus(row.getScrapStatus()));
        vo.setScrappedQty(row.getScrappedQty());
        vo.setScrappedAt(row.getScrappedAt());
        return vo;
    }
}
