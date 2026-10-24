package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.convert.DrugAdminConvert;
import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.query.BannedDrugQuery;
import com.zhimu.server.admin.base.query.BannedHitRow;
import com.zhimu.server.admin.base.query.StockTotalRow;
import com.zhimu.server.admin.base.request.BannedDrugCreateRequest;
import com.zhimu.server.admin.base.request.BannedDrugUpdateRequest;
import com.zhimu.server.admin.base.service.BannedDrugAdminService;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.BannedDrugVO;
import com.zhimu.server.admin.base.vo.BannedHitRowVO;
import com.zhimu.server.admin.base.vo.BannedImpactVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-D30–D34 禁用药维护。 */
@Service
@RequiredArgsConstructor
public class BannedDrugAdminServiceImpl implements BannedDrugAdminService {

    private static final Set<String> CLASSES = Set.of("BETA_AGONIST", "ANTIBACTERIAL", "NITROFURAN",
            "HORMONE", "SEDATIVE", "PESTICIDE", "OTHER");

    private final BannedDrugMapper bannedDrugMapper;
    private final DrugCatalogMapper catalogMapper;
    private final BaseAdminReadMapper readMapper;
    private final BaseTemplateSupport support;
    private final DrugAdminSupport drugSupport;
    private final BaseLogs logs;

    @Override
    public PageVO<BannedDrugVO> list(BannedDrugQuery query) {
        Page<BannedDrug> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        String bannedClass = normalizeClass(query.getBannedClass(), false);
        Page<BannedDrug> result = bannedDrugMapper.selectPage(page, Wrappers.<BannedDrug>lambdaQuery()
                .eq(bannedClass != null, BannedDrug::getBannedClass, bannedClass)
                .and(keyword != null, wrapper -> wrapper.like(BannedDrug::getName, keyword)
                        .or().like(BannedDrug::getAlias, keyword))
                .orderByAsc(BannedDrug::getSortNo)
                .orderByAsc(BannedDrug::getId));
        Map<Long, String> adminNames = support.adminNames();
        List<BannedDrugVO> rows = new ArrayList<>();
        for (BannedDrug drug : result.getRecords()) {
            rows.add(DrugAdminConvert.banned(drug, drug.getListedBy() == null ? null
                    : adminNames.get(drug.getListedBy())));
        }
        return PageVO.of(rows, result.getTotal(), query.pageOrDefault(), query.sizeOrDefault());
    }

    @Override
    public BannedImpactVO impact(Long catalogId) {
        DrugCatalog catalog = drugSupport.requireCatalog(catalogId);
        BannedImpactVO vo = new BannedImpactVO();
        vo.setArchiveFarmCount(readMapper.countArchivedFarmsByCatalog(catalog.getId()));
        for (StockTotalRow row : readMapper.stockTotalsByCatalog(catalog.getId())) {
            BannedImpactVO.StockTotalVO total = new BannedImpactVO.StockTotalVO();
            total.setQty(row.getQty());
            total.setUnit(row.getUnit());
            total.setUnitText(BaseAdminTexts.measureUnit(row.getUnit()));
            vo.getStockTotals().add(total);
        }
        return vo;
    }

    @Override
    @Transactional
    public BannedDrugVO create(BannedDrugCreateRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String bannedClass = normalizeClass(request.getBannedClass(), true);
        if (!StringUtils.hasText(request.getReason())) {
            throw BaseErrors.reasonEmpty("请填写列入原因");
        }
        DrugCatalog catalog = null;
        String name;
        String alias;
        if (request.getCatalogId() != null) {
            catalog = drugSupport.requireCatalog(request.getCatalogId());
            if (Boolean.TRUE.equals(catalog.getBanned()) || catalog.getBannedDrugId() != null) {
                throw BaseErrors.stateChanged("该药品已列为禁用药");
            }
            name = catalog.getName();
            alias = StringUtils.hasText(request.getAlias()) ? request.getAlias().trim() : catalog.getAlias();
        } else {
            if (!StringUtils.hasText(request.getName())) {
                throw BaseErrors.field("name", "请填写名称");
            }
            name = request.getName().trim();
            alias = StringUtils.hasText(request.getAlias()) ? request.getAlias().trim() : null;
            BannedDrug existing = bannedDrugMapper.selectOne(Wrappers.<BannedDrug>lambdaQuery()
                    .eq(BannedDrug::getName, name).last("LIMIT 1"));
            if (existing != null) {
                throw BaseErrors.duplicate(name + "已在禁用药清单中");
            }
        }
        LocalDateTime now = BeijingTime.now();
        BannedDrug drug = new BannedDrug();
        drug.setName(name);
        drug.setAlias(alias);
        drug.setBannedClass(bannedClass);
        drug.setCatalogId(catalog == null ? null : catalog.getId());
        drug.setReason(request.getReason().trim());
        drug.setListedAt(now);
        drug.setListedBy(adminId);
        drug.setSortNo(nextSortNo());
        drug.setHitCount30d(0);
        drug.setUpdatedBy(adminId);
        drug.setVersion(0);
        bannedDrugMapper.insert(drug);
        if (catalog != null) {
            int rows = catalogMapper.update(null, Wrappers.<DrugCatalog>lambdaUpdate()
                    .eq(DrugCatalog::getId, catalog.getId())
                    .eq(DrugCatalog::getVersion, catalog.getVersion())
                    .set(DrugCatalog::getBanned, true)
                    .set(DrugCatalog::getBannedDrugId, drug.getId())
                    .set(DrugCatalog::getUpdatedBy, adminId)
                    .set(DrugCatalog::getUpdatedAt, now)
                    .set(DrugCatalog::getVersion, catalog.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_BANNED_DRUG").opAction("BAN").bizType("BANNED_DRUG")
                .bizId(drug.getId()).targetName(name).searchText(name).reason(drug.getReason())
                .after(Map.of("name", name, "banned_class", bannedClass,
                        "catalog_id", catalog == null ? 0L : catalog.getId()))
                .build());
        return DrugAdminConvert.banned(drug, support.adminNames().get(adminId));
    }

    @Override
    @Transactional
    public BannedDrugVO update(Long id, BannedDrugUpdateRequest request) {
        Long adminId = AdminContext.requireAdminId();
        BannedDrug drug = id == null ? null : bannedDrugMapper.selectById(id);
        if (drug == null) {
            throw BaseErrors.notFound();
        }
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), drug.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String bannedClass = normalizeClass(request.getBannedClass(), true);
        String alias = StringUtils.hasText(request.getAlias()) ? request.getAlias().trim() : null;
        String oldAlias = drug.getAlias();
        String oldClass = drug.getBannedClass();
        int rows = bannedDrugMapper.update(null, Wrappers.<BannedDrug>lambdaUpdate()
                .eq(BannedDrug::getId, id)
                .eq(BannedDrug::getVersion, drug.getVersion())
                .set(BannedDrug::getAlias, alias)
                .set(BannedDrug::getBannedClass, bannedClass)
                .set(BannedDrug::getUpdatedBy, adminId)
                .set(BannedDrug::getUpdatedAt, BeijingTime.now())
                .set(BannedDrug::getVersion, drug.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        drug.setAlias(alias);
        drug.setBannedClass(bannedClass);
        drug.setVersion(drug.getVersion() + 1);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_BANNED_DRUG").opAction("UPDATE").bizType("BANNED_DRUG")
                .bizId(id).targetName(drug.getName()).searchText(drug.getName())
                .before(Map.of("alias", oldAlias == null ? "" : oldAlias,
                        "banned_class", oldClass))
                .build());
        return DrugAdminConvert.banned(drug, drug.getListedBy() == null ? null
                : support.adminNames().get(drug.getListedBy()));
    }

    @Override
    public PageVO<BannedHitRowVO> hits(Long bannedDrugId, PageQuery query) {
        BannedDrug drug = bannedDrugId == null ? null : bannedDrugMapper.selectById(bannedDrugId);
        if (drug == null) {
            throw BaseErrors.notFound();
        }
        Page<BannedHitRow> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        Page<BannedHitRow> result = readMapper.selectHitPage(page, bannedDrugId);
        List<BannedHitRowVO> rows = new ArrayList<>();
        for (BannedHitRow row : result.getRecords()) {
            BannedHitRowVO vo = new BannedHitRowVO();
            vo.setHitAt(row.getHitAt());
            vo.setHitSource(row.getHitSource());
            vo.setHitSourceText(BaseAdminTexts.hitSource(row.getHitSource()));
            vo.setFarmName(row.getFarmName());
            vo.setUserName(row.getUserName());
            vo.setKeyword(row.getKeyword());
            rows.add(vo);
        }
        return PageVO.of(rows, result.getTotal(), query.pageOrDefault(), query.sizeOrDefault());
    }

    private int nextSortNo() {
        BannedDrug latest = bannedDrugMapper.selectOne(Wrappers.<BannedDrug>lambdaQuery()
                .orderByDesc(BannedDrug::getSortNo).orderByDesc(BannedDrug::getId).last("LIMIT 1"));
        return latest == null || latest.getSortNo() == null ? 1 : latest.getSortNo() + 1;
    }

    private static String normalizeClass(String bannedClass, boolean required) {
        if (!StringUtils.hasText(bannedClass)) {
            if (required) {
                throw BaseErrors.field("banned_class", "请选择禁用类别");
            }
            return null;
        }
        String value = bannedClass.trim().toUpperCase();
        if (!CLASSES.contains(value)) {
            throw BaseErrors.field("banned_class", "请选择正确的禁用类别");
        }
        return value;
    }
}
