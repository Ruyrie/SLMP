package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.convert.DrugAdminConvert;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugLotRecall;
import com.zhimu.server.admin.base.entity.DrugLotRecallFarm;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.DrugLotRecallFarmMapper;
import com.zhimu.server.admin.base.mapper.DrugLotRecallMapper;
import com.zhimu.server.admin.base.query.DrugLotRecallRow;
import com.zhimu.server.admin.base.query.RecallFarmRow;
import com.zhimu.server.admin.base.request.LotRecallRequest;
import com.zhimu.server.admin.base.service.DrugLotRecallAdminService;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.RecallFarmVO;
import com.zhimu.server.admin.base.vo.RecallPreviewVO;
import com.zhimu.server.biz.stock.api.DrugLotRecallPort;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ADM-D35–D38 批号停用：写 t_drug_lot_recall / t_drug_lot_recall_farm，并通过模块09 端口标记批号。 */
@Service
@RequiredArgsConstructor
public class DrugLotRecallAdminServiceImpl implements DrugLotRecallAdminService {

    private final DrugLotRecallMapper recallMapper;
    private final DrugLotRecallFarmMapper recallFarmMapper;
    private final BaseAdminReadMapper readMapper;
    private final DrugAdminSupport drugSupport;
    private final DrugLotRecallPort recallPort;
    private final DrugLotRecallNotifier notifier;
    private final BaseLogs logs;

    @Override
    public PageVO<DrugLotRecallVO> list(PageQuery query) {
        Page<DrugLotRecallRow> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        Page<DrugLotRecallRow> result = readMapper.selectRecallPage(page);
        Map<Long, String> adminNames = drugSupportAdminNames();
        List<DrugLotRecallVO> rows = new ArrayList<>();
        for (DrugLotRecallRow row : result.getRecords()) {
            rows.add(DrugAdminConvert.recall(row, row.getRecalledBy() == null ? null
                    : adminNames.get(row.getRecalledBy())));
        }
        return PageVO.of(rows, result.getTotal(), query.pageOrDefault(), query.sizeOrDefault());
    }

    @Override
    public RecallPreviewVO preview(Long catalogId, String lotNo) {
        DrugCatalog catalog = drugSupport.requireCatalog(catalogId);
        requireRecallable(catalog);
        String normalizedLot = normalizeLotNo(lotNo);
        RecallPreviewVO existing = existingPreview(catalogId, normalizedLot);
        if (existing != null) {
            return existing;
        }
        DrugLotRecallPort.RecallApplyResult result = recallPort.holds(catalogId, normalizedLot);
        RecallPreviewVO vo = new RecallPreviewVO();
        vo.setExpireDate(result.expireDate());
        vo.setQtyUnit(catalog.getMeasureUnit());
        vo.setQtyUnitText(BaseAdminTexts.measureUnit(catalog.getMeasureUnit()));
        vo.setHasStockRecord(!result.lots().isEmpty());
        BigDecimal total = BigDecimal.ZERO;
        int farms = 0;
        BigDecimal sampleQty = BigDecimal.ZERO;
        for (DrugLotRecallPort.RecallLot lot : result.lots()) {
            BigDecimal stock = lot.stockQty() == null ? BigDecimal.ZERO : lot.stockQty();
            if (stock.signum() <= 0) {
                continue;
            }
            farms++;
            total = total.add(stock);
            if (stock.compareTo(sampleQty) > 0) {
                sampleQty = stock;
                vo.setSampleFarmName(farmName(lot.farmId()));
                vo.setSampleFarmQty(stock);
            }
        }
        vo.setFarmCount(farms);
        vo.setTotalQty(total);
        return vo;
    }

    @Override
    @Transactional
    public DrugLotRecallVO recall(LotRecallRequest request) {
        Long adminId = AdminContext.requireAdminId();
        DrugCatalog catalog = drugSupport.requireCatalog(request.getCatalogId());
        requireRecallable(catalog);
        String lotNo = normalizeLotNo(request.getLotNo());
        if (!StringUtils.hasText(request.getReason())) {
            throw BaseErrors.reasonEmpty("请填写停用原因");
        }
        DrugLotRecall duplicate = recallMapper.selectOne(Wrappers.<DrugLotRecall>lambdaQuery()
                .eq(DrugLotRecall::getCatalogId, catalog.getId())
                .eq(DrugLotRecall::getLotNo, lotNo)
                .last("LIMIT 1"));
        if (duplicate != null) {
            throw BaseErrors.stateChanged("批号 " + lotNo + " 已停用，不能重复停用");
        }
        LocalDateTime now = BeijingTime.now();
        DrugLotRecall recall = new DrugLotRecall();
        recall.setCatalogId(catalog.getId());
        recall.setLotNo(lotNo);
        recall.setReason(request.getReason().trim());
        recall.setFarmCount(0);
        recall.setTotalQty(BigDecimal.ZERO);
        recall.setQtyUnit(catalog.getMeasureUnit());
        recall.setRecalledAt(now);
        recall.setRecalledBy(adminId);
        recallMapper.insert(recall);

        DrugLotRecallPort.RecallApplyResult result = recallPort.markRecalled(catalog.getId(), lotNo,
                recall.getId(), recall.getReason(), now);
        BigDecimal total = BigDecimal.ZERO;
        int farmCount = 0;
        List<DrugLotRecallFarm> farms = new ArrayList<>();
        Map<Long, Long> lotIdByFarm = new HashMap<>();
        for (DrugLotRecallPort.RecallLot lot : result.lots()) {
            BigDecimal stock = lot.stockQty() == null ? BigDecimal.ZERO : lot.stockQty();
            if (stock.signum() <= 0) {
                continue;
            }
            farmCount++;
            total = total.add(stock);
            lotIdByFarm.putIfAbsent(lot.farmId(), lot.lotId());
            DrugLotRecallFarm farm = new DrugLotRecallFarm();
            farm.setRecallId(recall.getId());
            farm.setFarmId(lot.farmId());
            farm.setQtyAtRecall(stock);
            farm.setQtyUnit(catalog.getMeasureUnit());
            farm.setScrapStatus("UNSCRAPPED");
            farm.setScrappedQty(BigDecimal.ZERO);
            recallFarmMapper.insert(farm);
            farms.add(farm);
        }
        recall.setFarmCount(farmCount);
        recall.setTotalQty(total);
        recall.setExpireDate(result.expireDate());
        recallMapper.updateById(recall);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_LOT_RECALL").opAction("RECALL").bizType("DRUG_LOT_RECALL")
                .bizId(recall.getId()).targetName(catalog.getName() + " " + lotNo)
                .searchText(catalog.getName() + " " + lotNo).reason(recall.getReason())
                .after(Map.of("catalog_id", catalog.getId(), "lot_no", lotNo,
                        "farm_count", farmCount, "total_qty", total))
                .build());
        String catalogName = catalog.getName();
        String reason = recall.getReason();
        AfterCommit.run(() -> notifier.notifyRecalled(recall.getId(), catalogName, lotNo, reason,
                lotIdByFarm));
        DrugLotRecallVO vo = DrugAdminConvert.recall(recall, catalog, 0, null);
        vo.setNotifiedFarmCount(farmCount);
        return vo;
    }

    @Override
    public PageVO<RecallFarmVO> farms(Long recallId, PageQuery query) {
        DrugLotRecall recall = recallId == null ? null : recallMapper.selectById(recallId);
        if (recall == null) {
            throw BaseErrors.notFound();
        }
        Page<RecallFarmRow> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        Page<RecallFarmRow> result = readMapper.selectRecallFarmPage(page, recallId);
        List<RecallFarmVO> rows = new ArrayList<>();
        for (RecallFarmRow row : result.getRecords()) {
            rows.add(DrugAdminConvert.recallFarm(row));
        }
        return PageVO.of(rows, result.getTotal(), query.pageOrDefault(), query.sizeOrDefault());
    }

    private Map<Long, String> drugSupportAdminNames() {
        Map<Long, String> names = new HashMap<>();
        for (var row : readMapper.adminNames()) {
            names.put(row.getId(), row.getName());
        }
        return names;
    }

    private static void requireRecallable(DrugCatalog catalog) {
        if (!"VET_DRUG".equals(catalog.getDrugCategory())
                && !"VACCINE".equals(catalog.getDrugCategory())) {
            throw BaseErrors.param("请选择兽药或疫苗");
        }
    }

    private static String normalizeLotNo(String lotNo) {
        if (!StringUtils.hasText(lotNo)) {
            throw BaseErrors.field("lot_no", "请填写生产批号");
        }
        String value = lotNo.trim();
        if (value.length() > 30) {
            throw BaseErrors.field("lot_no", "生产批号最多 30 个字");
        }
        return value;
    }

    private RecallPreviewVO existingPreview(Long catalogId, String lotNo) {
        DrugLotRecall existing = recallMapper.selectOne(Wrappers.<DrugLotRecall>lambdaQuery()
                .eq(DrugLotRecall::getCatalogId, catalogId)
                .eq(DrugLotRecall::getLotNo, lotNo)
                .last("LIMIT 1"));
        if (existing == null) {
            return null;
        }
        throw BaseErrors.stateChanged("批号 " + lotNo + " 已停用，不能重复停用");
    }

    private String farmName(Long farmId) {
        if (farmId == null) {
            return null;
        }
        for (var row : readMapper.farmNames(List.of(farmId))) {
            if (Objects.equals(row.getId(), farmId)) {
                return row.getName();
            }
        }
        return null;
    }
}
