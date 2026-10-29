package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
import com.zhimu.server.biz.stock.request.DrugInboundRequest;
import com.zhimu.server.biz.stock.request.FeedInboundRequest;
import com.zhimu.server.biz.stock.request.InboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.service.StockInboundService;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.support.DrugLotStatuses;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockNumbers;
import com.zhimu.server.biz.stock.support.StockPartners;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.StockInboundVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 入库单实现（模块09 2.5 / 2.6 链 1）。
 * <p>
 * 饲料入库：校验 → 写单据 → 移动加权平均记账 → 写 INBOUND 流水 → 回填 flow_id → 最近供应商。
 * 药品入库：在饲料流程上增加批号（按 farm + drug + lot_no 建或复用）、有效期一致性校验、平台停用批号拦截；
 * 疫苗按“每瓶单价 ÷ 每瓶头份”折算记账单位成本。同步记账与库存写入共用事务，按入库单唯一生成自动支出。
 */
@Service
@RequiredArgsConstructor
public class StockInboundServiceImpl implements StockInboundService {

    private static final Pattern LOT_NO = Pattern.compile("^[A-Za-z0-9-]{1,30}$");

    private final StockInboundMapper inboundMapper;
    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final StockFlowMapper flowMapper;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockNumbers numbers;
    private final StockLookups lookups;
    private final StockPartners partners;
    private final StockSettingService settingService;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final FinanceSourceWriteService financeWriter;

    @Override
    @Transactional
    public StockInboundVO createFeedInbound(FeedInboundRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        Feed feed = lookups.requireFeed(request.getFeedId());
        LocalDate today = BeijingTime.today();
        if (request.getInboundDate().isAfter(today)) {
            throw StockErrors.field("inbound_date", "入库日期不能晚于今天");
        }
        validateFeedDates(request.getProductionDate(), request.getShelfLifeDate(), request.getInboundDate());
        if (!StringUtils.hasText(request.getSupplierName())) {
            throw StockErrors.field("supplier_name", "请填写供应商");
        }
        Measure measure = measure(request.getInputQty(), request.getInputUnit(), feed.getPackUnit(),
                feed.getPackSize(), "千克", false);
        BigDecimal amount = StockQty.amount(request.getInputQty().multiply(request.getUnitPrice()));
        BigDecimal measurePrice = measure.qty().signum() == 0 ? request.getUnitPrice()
                : amount.divide(measure.qty(), StockQty.PRICE_SCALE, RoundingMode.HALF_UP);

        StockInbound inbound = new StockInbound();
        inbound.setFarmId(farmId);
        inbound.setInboundNo(numbers.nextInboundNo(request.getInboundDate()));
        inbound.setMaterialType(StockCodes.MATERIAL_FEED);
        inbound.setFeedId(feed.getId());
        inbound.setInputQty(request.getInputQty());
        inbound.setInputUnit(measure.inputUnit());
        inbound.setQty(measure.qty());
        inbound.setUnitPrice(request.getUnitPrice());
        inbound.setMeasurePrice(measurePrice);
        inbound.setAmount(amount);
        inbound.setProductionDate(request.getProductionDate());
        inbound.setShelfLifeDate(request.getShelfLifeDate());
        inbound.setSupplierName(request.getSupplierName().trim());
        inbound.setInboundDate(request.getInboundDate());
        inbound.setIsSyncFinance(request.getSyncFinance() == null || request.getSyncFinance());
        inbound.setStatus(StockCodes.STATUS_NORMAL);
        inbound.setClientRequestId(requestId);
        inbound.setVersion(0);
        inbound.setCreatedBy(userId);
        inbound.setUpdatedBy(userId);
        inboundMapper.insert(inbound);

        StockLedger.Move move = ledger.inFeed(feed, measure.qty(), amount);
        StockFlow flow = flow(inbound, StockCodes.MATERIAL_FEED, feed.getId(), null, null,
                measure.qty(), measurePrice, amount, move, request.getInboundDate());
        inbound.setFlowId(flow.getId());
        syncFinance(inbound);
        inboundMapper.updateById(inbound);
        partners.touch(farmId, StockCodes.PARTNER_SUPPLIER, inbound.getSupplierName());
        lookups.log("CREATE", "INBOUND_CREATE", StockCodes.BIZ_INBOUND, inbound.getId(), feed.getName(), null,
                amount);
        return get(inbound.getId());
    }

    @Override
    @Transactional
    public StockInboundVO createDrugInbound(DrugInboundRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        Drug drug = lookups.requireDrug(request.getDrugId());
        if (drugCatalogQueryService.isBanned(drug.getCatalogId(), null)) {
            throw StockErrors.bannedInbound(drug.getName());
        }
        String lotNo = request.getLotNo().trim();
        if (!LOT_NO.matcher(lotNo).matches()) {
            throw StockErrors.field("lot_no", "批号只能是 1–30 位字母、数字、短横线");
        }
        LocalDate today = BeijingTime.today();
        if (request.getInboundDate().isAfter(today)) {
            throw StockErrors.field("inbound_date", "入库日期不能晚于今天");
        }
        if (request.getExpireDate().isBefore(request.getInboundDate())) {
            throw StockErrors.field("expire_date", "早于入库日期，不能入库");
        }
        Measure measure = measure(request.getInputQty(), request.getInputUnit(), drug.getPackUnit(),
                drug.getPackSize(), StockCodes.unitText(drug.getUnit()), true);
        if (StockCodes.UNIT_DOSE.equals(drug.getUnit())
                && measure.qty().stripTrailingZeros().scale() > 0) {
            throw StockErrors.field("input_qty", "头份必须是整数");
        }
        BigDecimal amount = StockQty.amount(request.getInputQty().multiply(request.getUnitPrice()));
        BigDecimal measurePrice = measure.qty().signum() == 0 ? request.getUnitPrice()
                : amount.divide(measure.qty(), StockQty.PRICE_SCALE, RoundingMode.HALF_UP);

        DrugLot lot = findLot(drug.getId(), lotNo);
        boolean createLot = lot == null;
        if (!createLot && (lot.getRecallId() != null || lot.getRecalledAt() != null)) {
            throw StockErrors.lotRecalledInbound(lotNo);
        }
        if (!createLot && lot.getExpireDate() != null && !lot.getExpireDate().equals(request.getExpireDate())) {
            throw StockErrors.field("expire_date", "批号 " + lotNo + " 已有库存，有效期为 "
                    + BeijingTime.format(lot.getExpireDate()) + "，请核对");
        }
        if (createLot) {
            lot = new DrugLot();
            lot.setFarmId(farmId);
            lot.setDrugId(drug.getId());
            lot.setCatalogId(drug.getCatalogId());
            lot.setCategory(drug.getCategory());
            lot.setLotNo(lotNo);
            lot.setExpireDate(request.getExpireDate());
            lot.setFirstInboundDate(request.getInboundDate());
            lot.setInboundCount(0);
            lot.setStockQty(BigDecimal.ZERO);
            lot.setStockAmount(BigDecimal.ZERO);
            lot.setAvgPrice(BigDecimal.ZERO);
            lot.setVersion(0);
            drugLotMapper.insert(lot);
        }

        StockInbound inbound = new StockInbound();
        inbound.setFarmId(farmId);
        inbound.setInboundNo(numbers.nextInboundNo(request.getInboundDate()));
        inbound.setMaterialType(StockCodes.MATERIAL_DRUG);
        inbound.setDrugId(drug.getId());
        inbound.setDrugLotId(lot.getId());
        inbound.setLotNo(lotNo);
        inbound.setInputQty(request.getInputQty());
        inbound.setInputUnit(measure.inputUnit());
        inbound.setQty(measure.qty());
        inbound.setUnitPrice(request.getUnitPrice());
        inbound.setMeasurePrice(measurePrice);
        inbound.setAmount(amount);
        inbound.setExpireDate(request.getExpireDate());
        inbound.setInboundDate(request.getInboundDate());
        inbound.setIsSyncFinance(request.getSyncFinance() == null || request.getSyncFinance());
        inbound.setStatus(StockCodes.STATUS_NORMAL);
        inbound.setClientRequestId(requestId);
        inbound.setVersion(0);
        inbound.setCreatedBy(userId);
        inbound.setUpdatedBy(userId);
        inboundMapper.insert(inbound);

        StockLedger.Move move = ledger.inLot(lot, measure.qty(), amount);
        drugLotMapper.touchInbound(lot.getId(), request.getExpireDate(), BeijingTime.now());
        StockFlow flow = flow(inbound, StockCodes.MATERIAL_DRUG, null, drug.getId(), lot.getId(),
                measure.qty(), measurePrice, amount, move, request.getInboundDate());
        inbound.setFlowId(flow.getId());
        syncFinance(inbound);
        inboundMapper.updateById(inbound);
        lookups.log("CREATE", "INBOUND_CREATE", StockCodes.BIZ_INBOUND, inbound.getId(), drug.getName(), null,
                amount);
        return get(inbound.getId());
    }

    @Override
    public StockInboundVO get(Long id) {
        StockInbound inbound = lookups.requireInbound(id);
        StockInboundVO vo = new StockInboundVO();
        vo.setInboundId(inbound.getId());
        vo.setInboundNo(inbound.getInboundNo());
        vo.setMaterialType(inbound.getMaterialType());
        vo.setFeedId(inbound.getFeedId());
        vo.setDrugId(inbound.getDrugId());
        vo.setDrugLotId(inbound.getDrugLotId());
        vo.setLotNo(inbound.getLotNo());
        vo.setInputQty(inbound.getInputQty());
        vo.setInputUnit(inbound.getInputUnit());
        vo.setUnitPrice(StockQty.price(inbound.getUnitPrice()));
        vo.setAmount(StockQty.amount(inbound.getAmount()));
        vo.setSupplierName(inbound.getSupplierName());
        vo.setProductionDate(BeijingTime.format(inbound.getProductionDate()));
        vo.setShelfLifeDate(BeijingTime.format(inbound.getShelfLifeDate()));
        vo.setExpireDate(BeijingTime.format(inbound.getExpireDate()));
        vo.setInboundDate(BeijingTime.format(inbound.getInboundDate()));
        vo.setSyncFinance(!Boolean.FALSE.equals(inbound.getIsSyncFinance()));
        vo.setFinanceRecordId(inbound.getFinanceRecordId());
        vo.setStatus(inbound.getStatus());
        vo.setStatusText(StockCodes.statusText(inbound.getStatus()));
        boolean hasOutbound = hasOutbound(inbound);
        vo.setHasOutbound(hasOutbound);
        vo.setCreatedAt(BeijingTime.format(inbound.getCreatedAt()));
        vo.setVersion(inbound.getVersion());
        vo.setCreatedBy(lookups.userBrief(inbound.getCreatedBy()));
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            Feed feed = feedMapper.selectById(inbound.getFeedId());
            vo.setMaterialName(feed == null ? null : feed.getName());
            vo.setQty(StockQty.feed(inbound.getQty()));
        } else {
            Drug drug = drugMapper.selectById(inbound.getDrugId());
            if (drug != null) {
                vo.setMaterialName(drug.getName());
                vo.setCategory(drug.getCategory());
                vo.setCategoryText(StockCodes.categoryText(drug.getCategory()));
                vo.setQty(StockQty.drug(inbound.getQty(), drug.getUnit(), drug.getPackUnit(),
                        drug.getPackSize()));
                int nearDays = settingService.nearDays(drug.getCategory());
                if (DrugLotStatuses.nearExpiry(inbound.getExpireDate(), nearDays)) {
                    vo.setNearExpiryDays(DrugLotStatuses.daysToExpire(inbound.getExpireDate()));
                }
            } else {
                vo.setQty(StockQty.drug(inbound.getQty(), null, null, null));
            }
        }
        if (StockCodes.STATUS_VOID.equals(inbound.getStatus())) {
            vo.setActions(StockActions.voided());
        } else if (!hasOutbound) {
            // 未发生出库的入库单可直接修改 / 作废（修改申请只针对已出库的单据）
            vo.setActions(StockActions.editable(BeijingTime.today()));
        } else {
            vo.setActions(StockActions.requestOnly(true));
        }
        return vo;
    }

    @Override
    @Transactional
    public StockInboundVO update(Long id, InboundUpdateRequest request) {
        StockInbound inbound = lookups.requireInbound(id);
        if (StockCodes.STATUS_VOID.equals(inbound.getStatus())) {
            throw StockErrors.voided("这张入库单已作废");
        }
        if (hasOutbound(inbound)) {
            throw StockErrors.changeRequestRequired("已发生出库，需要提交修改申请");
        }
        if (request.getVersion() == null || !request.getVersion().equals(inbound.getVersion())) {
            throw StockErrors.versionConflict();
        }
        LocalDate today = BeijingTime.today();
        if (request.getInboundDate().isAfter(today)) {
            throw StockErrors.field("inbound_date", "入库日期不能晚于今天");
        }
        BigDecimal newAmount = StockQty.amount(request.getInputQty().multiply(request.getUnitPrice()));
        BigDecimal oldQty = StockQty.zeroIfNull(inbound.getQty());
        BigDecimal oldAmount = StockQty.zeroIfNull(inbound.getAmount());
        String packUnit;
        BigDecimal packSize;
        String unitText;
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            Feed feed = lookups.requireFeed(inbound.getFeedId());
            packUnit = feed.getPackUnit();
            packSize = feed.getPackSize();
            unitText = "千克";
            validateFeedDates(request.getProductionDate(), request.getShelfLifeDate(), request.getInboundDate());
        } else {
            Drug drug = lookups.requireDrug(inbound.getDrugId());
            DrugLot lot = lookups.requireLot(inbound.getDrugLotId());
            packUnit = drug.getPackUnit();
            packSize = drug.getPackSize();
            unitText = StockCodes.unitText(drug.getUnit());
            if (request.getExpireDate() == null) {
                throw StockErrors.field("expire_date", "请选择有效期");
            }
            if (request.getExpireDate().isBefore(request.getInboundDate())) {
                throw StockErrors.field("expire_date", "早于入库日期，不能入库");
            }
            int inboundCount = lot.getInboundCount() == null ? 0 : lot.getInboundCount();
            if (!request.getExpireDate().equals(lot.getExpireDate())) {
                if (inboundCount > 1) {
                    throw StockErrors.field("expire_date", "该批号有多张入库单，有效期不能修改");
                }
                lot.setExpireDate(request.getExpireDate());
                drugLotMapper.updateById(lot);
            }
        }
        Measure measure = measure(request.getInputQty(), request.getInputUnit(), packUnit, packSize, unitText,
                StockCodes.MATERIAL_DRUG.equals(inbound.getMaterialType()));
        BigDecimal deltaQty = measure.qty().subtract(oldQty);
        BigDecimal deltaAmount = newAmount.subtract(oldAmount);
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            ledger.adjustFeed(lookups.requireFeed(inbound.getFeedId()), deltaQty, deltaAmount);
        } else {
            ledger.adjustLot(lookups.requireLot(inbound.getDrugLotId()), deltaQty, deltaAmount, unitText);
            inbound.setExpireDate(request.getExpireDate());
        }
        inbound.setInputQty(request.getInputQty());
        inbound.setInputUnit(measure.inputUnit());
        inbound.setQty(measure.qty());
        inbound.setUnitPrice(request.getUnitPrice());
        inbound.setAmount(newAmount);
        inbound.setMeasurePrice(measure.qty().signum() == 0 ? request.getUnitPrice()
                : newAmount.divide(measure.qty(), StockQty.PRICE_SCALE, RoundingMode.HALF_UP));
        inbound.setInboundDate(request.getInboundDate());
        inbound.setProductionDate(request.getProductionDate());
        inbound.setShelfLifeDate(request.getShelfLifeDate());
        if (StringUtils.hasText(request.getSupplierName())) {
            inbound.setSupplierName(request.getSupplierName().trim());
        }
        if (request.getSyncFinance() != null) {
            inbound.setIsSyncFinance(request.getSyncFinance());
        }
        inbound.setUpdatedBy(lookups.userId());
        syncFinance(inbound);
        inboundMapper.updateById(inbound);

        StockFlow flow = inbound.getFlowId() == null ? null : flowMapper.selectById(inbound.getFlowId());
        if (flow != null) {
            flow.setQty(measure.qty());
            flow.setUnitPrice(inbound.getMeasurePrice());
            flow.setAmount(newAmount);
            flow.setBizDate(request.getInboundDate());
            flow.setStockQtyAfter(StockQty.zeroIfNull(flow.getStockQtyAfter()).add(deltaQty));
            flow.setStockAmountAfter(StockQty.zeroIfNull(flow.getStockAmountAfter()).add(deltaAmount));
            flow.setRecalcAt(BeijingTime.now());
            flowMapper.updateById(flow);
        }
        if (StockCodes.isFeed(inbound.getMaterialType()) && StringUtils.hasText(inbound.getSupplierName())) {
            partners.touch(inbound.getFarmId(), StockCodes.PARTNER_SUPPLIER, inbound.getSupplierName());
        }
        lookups.log("UPDATE", "INBOUND_UPDATE", StockCodes.BIZ_INBOUND, inbound.getId(), inbound.getInboundNo(),
                oldAmount, newAmount);
        return get(inbound.getId());
    }

    @Override
    @Transactional
    public void voidInbound(Long id, VoidRequest request) {
        StockInbound inbound = lookups.requireInbound(id);
        if (StockCodes.STATUS_VOID.equals(inbound.getStatus())) {
            throw StockErrors.voided("这张入库单已作废");
        }
        if (request.getVersion() == null || !request.getVersion().equals(inbound.getVersion())) {
            throw StockErrors.versionConflict();
        }
        if (hasOutbound(inbound)) {
            throw StockErrors.changeRequestRequired("已发生出库，需要提交修改申请");
        }
        if (StockCodes.isFeed(inbound.getMaterialType())) {
            ledger.adjustFeed(lookups.requireFeed(inbound.getFeedId()),
                    StockQty.zeroIfNull(inbound.getQty()).negate(),
                    StockQty.zeroIfNull(inbound.getAmount()).negate());
        } else {
            DrugLot lot = lookups.requireLot(inbound.getDrugLotId());
            ledger.adjustLot(lot, StockQty.zeroIfNull(inbound.getQty()).negate(),
                    StockQty.zeroIfNull(inbound.getAmount()).negate(),
                    StockCodes.unitText(unitOf(lot)));
            int count = lot.getInboundCount() == null ? 0 : lot.getInboundCount();
            lot.setInboundCount(Math.max(count - 1, 0));
            drugLotMapper.updateById(lot);
        }
        if (inbound.getFlowId() != null) {
            StockFlow flow = flowMapper.selectById(inbound.getFlowId());
            if (flow != null) {
                flowWriter.voidFlow(flow, StockCodes.VOID_SOURCE);
            }
        }
        inbound.setStatus(StockCodes.STATUS_VOID);
        inbound.setVoidAt(BeijingTime.now());
        inbound.setVoidBy(lookups.userId());
        inbound.setUpdatedBy(lookups.userId());
        syncFinance(inbound);
        inboundMapper.updateById(inbound);
        lookups.log("VOID", "INBOUND_VOID", StockCodes.BIZ_INBOUND, inbound.getId(), inbound.getInboundNo(),
                inbound.getAmount(), null);
    }

    private void syncFinance(StockInbound inbound) {
        boolean feed = StockCodes.isFeed(inbound.getMaterialType());
        Feed feedInfo = feed ? feedMapper.selectById(inbound.getFeedId()) : null;
        Drug drugInfo = feed ? null : drugMapper.selectById(inbound.getDrugId());
        String name = feed ? (feedInfo == null ? "饲料" : feedInfo.getName()) : (drugInfo == null ? "药品" : drugInfo.getName());
        String unit = feed ? "千克" : StockCodes.unitText(drugInfo == null ? null : drugInfo.getUnit());
        String detail = name + " " + inbound.getQty().stripTrailingZeros().toPlainString() + " " + (unit == null ? "" : unit);
        inbound.setFinanceRecordId(financeWriter.syncInboundExpense(new FinanceSourceWriteService.InboundExpense(
                inbound.getFarmId(), feed ? FinanceConst.SOURCE_FEED : FinanceConst.SOURCE_DRUG,
                inbound.getId(), inbound.getInboundNo(), inbound.getAmount(), inbound.getInboundDate(), detail,
                !Boolean.FALSE.equals(inbound.getIsSyncFinance()) && StockCodes.STATUS_NORMAL.equals(inbound.getStatus()),
                lookups.userId())));
    }

    private String unitOf(DrugLot lot) {
        Drug drug = drugMapper.selectById(lot.getDrugId());
        return drug == null ? null : drug.getUnit();
    }

    private StockFlow flow(StockInbound inbound, String materialType, Long feedId, Long drugId, Long drugLotId,
                           BigDecimal qty, BigDecimal measurePrice, BigDecimal amount, StockLedger.Move move,
                           LocalDate bizDate) {
        StockFlow flow = new StockFlow();
        flow.setFarmId(inbound.getFarmId());
        flow.setMaterialType(materialType);
        flow.setFeedId(feedId);
        flow.setDrugId(drugId);
        flow.setDrugLotId(drugLotId);
        flow.setFlowType(StockCodes.FLOW_INBOUND);
        flow.setDirection(StockCodes.DIRECTION_IN);
        flow.setQty(StockQty.qty(qty));
        flow.setUnitPrice(measurePrice);
        flow.setAmount(amount);
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(StockCodes.BIZ_INBOUND);
        flow.setBizId(inbound.getId());
        // 经办人：登记入库的人（流水列表“经办人”列）
        flow.setOperatorId(inbound.getCreatedBy());
        flow.setOccurAt(BeijingTime.now());
        flow.setBizDate(bizDate);
        flow.setIsBatchCost(false);
        flow.setIsFeedUsage(false);
        return flowWriter.record(flow);
    }

    private boolean hasOutbound(StockInbound inbound) {
        Long flowId = inbound.getFlowId();
        if (flowId == null) {
            return false;
        }
        Long count = flowMapper.selectCount(Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getDirection, StockCodes.DIRECTION_OUT)
                .gt(StockFlow::getOriginFlowId, flowId)
                .eq(StockFlow::getStatus, StockCodes.STATUS_NORMAL)
                .and(StockCodes.isFeed(inbound.getMaterialType())
                        ? w -> w.eq(StockFlow::getFeedId, inbound.getFeedId())
                        : w -> w.eq(StockFlow::getDrugLotId, inbound.getDrugLotId())));
        return count != null && count > 0;
    }

    private DrugLot findLot(Long drugId, String lotNo) {
        return drugLotMapper.selectOne(Wrappers.<DrugLot>lambdaQuery()
                .eq(DrugLot::getDrugId, drugId)
                .eq(DrugLot::getLotNo, lotNo)
                .last("LIMIT 1"));
    }

    private static void validateFeedDates(LocalDate productionDate, LocalDate shelfLifeDate, LocalDate inboundDate) {
        if (productionDate != null && productionDate.isAfter(inboundDate)) {
            throw StockErrors.field("production_date", "生产日期不能晚于入库日期");
        }
        if (shelfLifeDate != null) {
            if (productionDate != null && !shelfLifeDate.isAfter(productionDate)) {
                throw StockErrors.field("shelf_life_date", "必须晚于生产日期");
            }
            if (shelfLifeDate.isBefore(inboundDate)) {
                throw StockErrors.field("shelf_life_date", "已过保质期，不能入库");
            }
        }
    }

    /** 录入数量换算为记账数量 */
    private record Measure(BigDecimal qty, String inputUnit) {
    }

    private static Measure measure(BigDecimal inputQty, String inputUnit, String packUnit, BigDecimal packSize,
                                   String unitText, boolean requireIntegerDose) {
        String unit = StringUtils.hasText(inputUnit) ? inputUnit.trim().toUpperCase() : null;
        if (unit == null) {
            unit = StockCodes.INPUT_MEASURE;
        }
        if (StockCodes.INPUT_PACK.equals(unit)) {
            if (packUnit == null || packSize == null || packSize.signum() <= 0) {
                throw StockErrors.field("input_unit", "该物资未设置包装，不能按包装录入");
            }
            BigDecimal qty = StockQty.qty(inputQty.multiply(packSize));
            if (requireIntegerDose && qty.stripTrailingZeros().scale() > 0) {
                throw StockErrors.field("input_qty", unitText + "必须是整数");
            }
            return new Measure(qty, StockCodes.INPUT_PACK);
        }
        if (!StockCodes.INPUT_MEASURE.equals(unit)) {
            throw StockErrors.field("input_unit", "录入单位只能是 MEASURE 或 PACK");
        }
        BigDecimal qty = StockQty.qty(inputQty);
        if (requireIntegerDose && qty.stripTrailingZeros().scale() > 0) {
            throw StockErrors.field("input_qty", unitText + "必须是整数");
        }
        return new Measure(qty, StockCodes.INPUT_MEASURE);
    }

    /** 显式使用 ErrorCode，避免 IDE 误删导入 */
    @SuppressWarnings("unused")
    private static BizException unused() {
        return new BizException(ErrorCode.SERVER_ERROR);
    }
}
