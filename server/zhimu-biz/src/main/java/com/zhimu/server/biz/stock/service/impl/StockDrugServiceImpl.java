package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.request.DrugSaveRequest;
import com.zhimu.server.biz.stock.service.StockDrugService;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.support.DrugLotStatuses;
import com.zhimu.server.biz.stock.support.DrugUsables;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.DrugArchiveItemVO;
import com.zhimu.server.biz.stock.vo.DrugArchiveListResult;
import com.zhimu.server.biz.stock.vo.DrugDetailVO;
import com.zhimu.server.biz.stock.vo.DrugOptionListResult;
import com.zhimu.server.biz.stock.vo.DrugOptionVO;
import com.zhimu.server.biz.stock.vo.DrugStockOverviewVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 药品档案实现（模块09 2.5 / 2.7）：名称、类别、包装取平台目录快照；
 * 可用结存 = 未过期、未停用批号的结存合计；库存下限按录入单位（App 固定 PACK）换算后存记账单位。
 */
@Service
@RequiredArgsConstructor
public class StockDrugServiceImpl implements StockDrugService {

    private static final int MAX_KEYWORD = 40;

    private final DrugMapper drugMapper;
    private final DrugLotMapper lotMapper;
    private final DrugUsables usables;
    private final StockLookups lookups;
    private final StockSettingService settingService;
    private final DrugCatalogQueryService drugCatalogQueryService;

    @Override
    public DrugStockOverviewVO overview() {
        List<Drug> drugs = drugMapper.selectList(Wrappers.<Drug>lambdaQuery()
                .orderByAsc(Drug::getCategory).orderByAsc(Drug::getName));
        List<DrugLot> lots = lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .gt(DrugLot::getStockQty, BigDecimal.ZERO));
        DrugStockOverviewVO vo = new DrugStockOverviewVO();
        DrugStockOverviewVO.LotCounts counts = new DrugStockOverviewVO.LotCounts();
        counts.setAll(lots.size());
        int attention = 0;
        int normal = 0;
        for (DrugLot lot : lots) {
            String status = DrugLotStatuses.status(lot, settingService.nearDays(lot.getCategory()),
                    drugCatalogQueryService.isBanned(lot.getCatalogId(), null));
            switch (lot.getCategory() == null ? "" : lot.getCategory()) {
                case StockCodes.CAT_VACCINE -> counts.setVaccine(counts.getVaccine() + 1);
                case StockCodes.CAT_DISINFECTANT -> counts.setDisinfectant(counts.getDisinfectant() + 1);
                default -> counts.setVetDrug(counts.getVetDrug() + 1);
            }
            if (StockCodes.LOT_NORMAL.equals(status)) {
                normal++;
            } else {
                attention++;
            }
        }
        vo.setLotCounts(counts);
        var setting = settingService.current();
        DrugStockOverviewVO.NearDays nearDays = new DrugStockOverviewVO.NearDays();
        nearDays.setVet(setting.getNearDaysVet());
        nearDays.setVaccine(setting.getNearDaysVaccine());
        nearDays.setDisinfectant(setting.getNearDaysDisinfectant());
        vo.setNearDays(nearDays);
        vo.setAttentionCount(attention);
        vo.setNormalCount(normal);
        vo.setHasAnyLot(!lots.isEmpty());
        vo.setFarmReadonly(lookups.farmReadonly());

        Map<Long, BigDecimal> usable = usables.usableByDrug(drugs.stream().map(Drug::getId).toList());
        DrugStockOverviewVO.LowStockBanner banner = new DrugStockOverviewVO.LowStockBanner();
        for (Drug drug : drugs) {
            if (drug.getMinStock() == null) {
                continue;
            }
            BigDecimal total = usable.getOrDefault(drug.getId(), BigDecimal.ZERO);
            if (total.compareTo(drug.getMinStock()) >= 0) {
                continue;
            }
            DrugStockOverviewVO.LowDrug low = new DrugStockOverviewVO.LowDrug();
            low.setDrugId(drug.getId());
            low.setName(drug.getName());
            low.setUsable(StockQty.drug(total, drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
            low.setMinStock(DrugUsables.minStockDisplay(drug));
            banner.getDrugs().add(low);
            if (banner.getPreselectDrugId() == null) {
                banner.setPreselectDrugId(drug.getId());
            }
        }
        vo.setLowStockBanner(banner);
        return vo;
    }

    @Override
    public DrugArchiveListResult list(String keyword, String category) {
        List<Drug> drugs = drugMapper.selectList(Wrappers.<Drug>lambdaQuery()
                .like(StringUtils.hasText(keyword), Drug::getName, keyword == null ? null : keyword.trim())
                .eq(StringUtils.hasText(category), Drug::getCategory,
                        category == null ? null : category.trim().toUpperCase())
                .orderByAsc(Drug::getCategory)
                .orderByAsc(Drug::getName));
        Map<Long, BigDecimal> usable = usables.usableByDrug(drugs.stream().map(Drug::getId).toList());
        DrugArchiveListResult result = new DrugArchiveListResult();
        for (Drug drug : drugs) {
            BigDecimal total = usable.getOrDefault(drug.getId(), BigDecimal.ZERO);
            DrugArchiveItemVO item = new DrugArchiveItemVO();
            item.setDrugId(drug.getId());
            item.setCatalogId(drug.getCatalogId());
            item.setName(drug.getName());
            item.setCategory(drug.getCategory());
            item.setUsable(StockQty.drug(total, drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
            item.setMinStock(DrugUsables.minStockDisplay(drug));
            item.setLowStock(drug.getMinStock() != null && total.compareTo(drug.getMinStock()) < 0);
            item.setHasStock(total.signum() > 0);
            item.setBanned(drugCatalogQueryService.isBanned(drug.getCatalogId(), null));
            result.getList().add(item);
        }
        result.setTotal(result.getList().size());
        return result;
    }

    @Override
    public DrugOptionListResult options(String purpose, String keyword) {
        boolean inbound = !StockCodes.PURPOSE_OUTBOUND.equalsIgnoreCase(purpose);
        DrugOptionListResult result = new DrugOptionListResult();
        String search = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if (search != null) {
            if (search.length() > MAX_KEYWORD) {
                throw StockErrors.field("keyword", "搜索关键字最多 40 个字");
            }
            BannedDrugHitVO hit = drugCatalogQueryService.checkBanned(search);
            if (hit != null) {
                result.setBannedHint(hit.getName() + "为禁用药，" + (inbound ? "不能入库" : "不能使用"));
                return result;
            }
        }
        List<Drug> drugs = drugMapper.selectList(Wrappers.<Drug>lambdaQuery()
                .like(search != null, Drug::getName, search)
                .orderByAsc(Drug::getCategory)
                .orderByAsc(Drug::getName));
        Map<Long, BigDecimal> usable = usables.usableByDrug(drugs.stream().map(Drug::getId).toList());
        for (Drug drug : drugs) {
            if (drugCatalogQueryService.isBanned(drug.getCatalogId(), null)) {
                continue;
            }
            BigDecimal total = usable.getOrDefault(drug.getId(), BigDecimal.ZERO);
            if (!inbound && total.signum() <= 0) {
                continue;
            }
            DrugOptionVO option = new DrugOptionVO();
            option.setDrugId(drug.getId());
            option.setName(drug.getName());
            option.setCategory(drug.getCategory());
            option.setSpec(drug.getSpec());
            option.setUnit(drug.getUnit());
            option.setPackUnit(drug.getPackUnit());
            option.setPackSize(drug.getPackSize());
            option.setUsable(StockQty.drug(total, drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
            result.getList().add(option);
        }
        return result;
    }

    @Override
    public DrugDetailVO detail(Long id) {
        Drug drug = lookups.requireDrug(id);
        DrugCatalogInfo catalog = catalog(drug.getCatalogId());
        List<DrugLot> lots = lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .eq(DrugLot::getDrugId, drug.getId())
                .gt(DrugLot::getStockQty, BigDecimal.ZERO));
        BigDecimal usable = usables.usableOf(lots);
        DrugDetailVO vo = new DrugDetailVO();
        vo.setDrugId(drug.getId());
        vo.setCategory(drug.getCategory());
        vo.setCatalogId(drug.getCatalogId());
        vo.setName(drug.getName());
        vo.setCatalogSourceText("兽药目录");
        vo.setSpec(drug.getSpec());
        vo.setUnit(drug.getUnit());
        vo.setPackUnit(drug.getPackUnit());
        vo.getPackUnitOptions().add(drug.getPackUnit());
        vo.setPackSize(drug.getPackSize());
        vo.setPackSizeLocked(Boolean.TRUE.equals(drug.getIsUnitLocked()));
        vo.setMinStock(DrugUsables.minStockDisplay(drug));
        vo.setUsable(StockQty.drug(usable, drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
        vo.setUnitLocked(Boolean.TRUE.equals(drug.getIsUnitLocked()));
        vo.setBanned(catalog != null && drugCatalogQueryService.isBanned(drug.getCatalogId(), null));
        vo.setNearDays(settingService.nearDays(drug.getCategory()));
        vo.setVersion(drug.getVersion());
        vo.setUpdatedAt(BeijingTime.format(drug.getUpdatedAt()));
        vo.setUpdatedByName(drug.getUpdatedBy() == null ? null : lookups.userName(drug.getUpdatedBy()));
        return vo;
    }

    @Override
    @Transactional
    public DrugDetailVO create(DrugSaveRequest request) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        if (request.getCatalogId() == null) {
            throw StockErrors.field("catalog_id", "请选择名称");
        }
        DrugCatalogInfo catalog = drugCatalogQueryService.requireCatalog(request.getCatalogId());
        if (drugCatalogQueryService.isBanned(catalog.id(), null)) {
            throw StockErrors.bannedArchive(catalog.name());
        }
        if (existsCatalog(catalog.id(), null)) {
            throw StockErrors.duplicateDrug(catalog.name());
        }
        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim().toUpperCase()
                : catalog.drugCategory();
        String unit = StockCodes.isVaccine(category) ? StockCodes.UNIT_DOSE
                : StringUtils.hasText(request.getUnit()) ? request.getUnit().trim().toUpperCase()
                : catalog.measureUnit();
        String packUnit = StringUtils.hasText(request.getPackUnit()) ? request.getPackUnit().trim()
                : catalog.packUnit();
        BigDecimal packSize;
        if (StockCodes.isVaccine(category)) {
            packSize = catalog.packSize();
        } else {
            if (request.getPackSize() == null || request.getPackSize().signum() <= 0) {
                throw StockErrors.field("pack_size", "请填写每包装折合数");
            }
            packSize = request.getPackSize().setScale(3, RoundingMode.HALF_UP);
        }
        Drug drug = new Drug();
        drug.setFarmId(farmId);
        drug.setCategory(category);
        drug.setCatalogId(catalog.id());
        drug.setName(catalog.name());
        drug.setSpec(StringUtils.hasText(request.getSpec()) ? request.getSpec().trim() : catalog.spec());
        drug.setUnit(unit);
        drug.setPackUnit(packUnit);
        drug.setPackSize(packSize);
        applyMinStock(drug, request.getMinStock(), request.getMinStockUnit(), packSize);
        drug.setIsLowStock(false);
        drug.setIsUnitLocked(false);
        drug.setVersion(0);
        drug.setDeleted(0);
        drug.setCreatedBy(userId);
        drug.setUpdatedBy(userId);
        drugMapper.insert(drug);
        lookups.log("CREATE", "DRUG_CREATE", StockCodes.BIZ_DRUG, drug.getId(), drug.getName(), null,
                drug.getName());
        return detail(drug.getId());
    }

    @Override
    @Transactional
    public DrugDetailVO update(Long id, DrugSaveRequest request) {
        Drug drug = lookups.requireDrug(id);
        if (request.getVersion() == null || !request.getVersion().equals(drug.getVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        if (drugCatalogQueryService.isBanned(drug.getCatalogId(), null)) {
            throw new BizException(ErrorCode.BANNED_DRUG, "该药品已列为禁用药，档案只能查看");
        }
        boolean locked = Boolean.TRUE.equals(drug.getIsUnitLocked());
        String unit = drug.getUnit();
        if (StringUtils.hasText(request.getUnit())) {
            String requested = request.getUnit().trim().toUpperCase();
            if (!requested.equals(drug.getUnit())) {
                if (locked) {
                    throw StockErrors.field("unit", "已有出入库记录，单位不能修改");
                }
                unit = requested;
            }
        }
        String packUnit = StringUtils.hasText(request.getPackUnit()) ? request.getPackUnit().trim()
                : drug.getPackUnit();
        BigDecimal packSize = request.getPackSize() == null ? drug.getPackSize()
                : request.getPackSize().setScale(3, RoundingMode.HALF_UP);
        if (locked) {
            packUnit = drug.getPackUnit();
            packSize = drug.getPackSize();
        }
        drug.setUnit(unit);
        drug.setPackUnit(packUnit);
        drug.setPackSize(packSize);
        drug.setSpec(StringUtils.hasText(request.getSpec()) ? request.getSpec().trim() : drug.getSpec());
        if (request.getMinStock() != null) {
            applyMinStock(drug, request.getMinStock(), request.getMinStockUnit(), packSize);
        } else {
            drug.setMinStock(null);
            drug.setMinStockUnit(null);
        }
        drug.setUpdatedBy(lookups.userId());
        drugMapper.updateById(drug);
        lookups.log("UPDATE", "DRUG_UPDATE", StockCodes.BIZ_DRUG, drug.getId(), drug.getName(), null,
                drug.getMinStock());
        return detail(drug.getId());
    }

    /** 库存下限：按录入单位折算为记账单位存储（App 固定按 PACK 录入） */
    private static void applyMinStock(Drug drug, BigDecimal minStock, String minStockUnit, BigDecimal packSize) {
        if (minStock == null) {
            drug.setMinStock(null);
            drug.setMinStockUnit(null);
            return;
        }
        if (minStock.signum() < 0) {
            throw StockErrors.field("min_stock", "不能小于 0");
        }
        String unit = StringUtils.hasText(minStockUnit) ? minStockUnit.trim().toUpperCase() : StockCodes.INPUT_PACK;
        BigDecimal measure = StockCodes.INPUT_PACK.equals(unit) && packSize != null && packSize.signum() > 0
                ? minStock.multiply(packSize) : minStock;
        drug.setMinStock(StockQty.qty(measure));
        drug.setMinStockUnit(unit);
    }

    private boolean existsCatalog(Long catalogId, Long excludeId) {
        Long count = drugMapper.selectCount(Wrappers.<Drug>lambdaQuery()
                .eq(Drug::getCatalogId, catalogId)
                .ne(excludeId != null, Drug::getId, excludeId));
        return count != null && count > 0;
    }

    private DrugCatalogInfo catalog(Long catalogId) {
        try {
            return drugCatalogQueryService.requireCatalog(catalogId);
        } catch (BizException e) {
            return null;
        }
    }

    /** 供排序稳定（类别顺序：兽药、疫苗、消毒剂） */
    @SuppressWarnings("unused")
    private static final Comparator<String> CATEGORY_ORDER = Comparator.nullsLast(String::compareTo);

    @SuppressWarnings("unused")
    private static List<String> emptyList() {
        return new ArrayList<>();
    }
}
