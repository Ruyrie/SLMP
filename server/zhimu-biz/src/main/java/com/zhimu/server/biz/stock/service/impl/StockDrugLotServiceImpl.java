package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
import com.zhimu.server.biz.stock.service.StockDrugLotService;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.support.DrugLotStatuses;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.DrugLotDetailVO;
import com.zhimu.server.biz.stock.vo.DrugLotItemVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionsVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批号实现（模块09 2.5 / 2.7）：
 * 只显示结存大于 0 的批号；LOT_STATUS 按当天派生（RECALLED &gt; BANNED &gt; EXPIRED &gt; NEAR_EXPIRY &gt; NORMAL）；
 * ATTENTION 排序 已停用 → 已过期 → 临期、同类有效期升序；NORMAL 按药品名、有效期升序。
 * 选择批号时可用批号按先到期先出（有效期 → 首次入库日期 → id），第一个标“优先出库”并作默认。
 */
@Service
@RequiredArgsConstructor
public class StockDrugLotServiceImpl implements StockDrugLotService {

    private static final Map<String, Integer> ATTENTION_RANK = Map.of(
            StockCodes.LOT_RECALLED, 0,
            StockCodes.LOT_BANNED, 1,
            StockCodes.LOT_EXPIRED, 2,
            StockCodes.LOT_NEAR_EXPIRY, 3);

    private final DrugLotMapper lotMapper;
    private final DrugMapper drugMapper;
    private final StockInboundMapper inboundMapper;
    private final StockFlowMapper flowMapper;
    private final StockLookups lookups;
    private final StockSettingService settingService;
    private final DrugCatalogQueryService drugCatalogQueryService;

    @Override
    public PageVO<DrugLotItemVO> page(String group, String category, String keyword, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);
        List<DrugLot> lots = lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .gt(DrugLot::getStockQty, BigDecimal.ZERO));
        Map<Long, Drug> drugs = drugs(lots);
        String search = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String categoryFilter = StringUtils.hasText(category) ? category.trim().toUpperCase() : null;

        List<DrugLot> attention = new ArrayList<>();
        List<DrugLot> normal = new ArrayList<>();
        for (DrugLot lot : lots) {
            Drug drug = drugs.get(lot.getDrugId());
            if (categoryFilter != null && !categoryFilter.equals(lot.getCategory())) {
                continue;
            }
            if (search != null && !matches(lot, drug, search)) {
                continue;
            }
            String status = status(lot);
            if (StockCodes.LOT_NORMAL.equals(status)) {
                normal.add(lot);
            } else {
                attention.add(lot);
            }
        }
        attention.sort(Comparator
                .comparingInt((DrugLot lot) -> ATTENTION_RANK.getOrDefault(status(lot), 9))
                .thenComparing(DrugLot::getExpireDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DrugLot::getId));
        normal.sort(Comparator
                .comparing((DrugLot lot) -> nameOf(drugs.get(lot.getDrugId())))
                .thenComparing(DrugLot::getExpireDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DrugLot::getId));

        List<DrugLot> ordered = new ArrayList<>();
        String normalizedGroup = StringUtils.hasText(group) ? group.trim().toUpperCase() : StockCodes.LOT_GROUP_ALL;
        if (StockCodes.LOT_GROUP_ATTENTION.equals(normalizedGroup)) {
            ordered.addAll(attention);
        } else if (StockCodes.LOT_GROUP_NORMAL.equals(normalizedGroup)) {
            ordered.addAll(normal);
        } else {
            ordered.addAll(attention);
            ordered.addAll(normal);
        }
        Map<String, Integer> tabCounts = new HashMap<>();
        tabCounts.put(StockCodes.LOT_GROUP_ATTENTION, attention.size());
        tabCounts.put(StockCodes.LOT_GROUP_NORMAL, normal.size());

        List<DrugLotItemVO> items = new ArrayList<>();
        int from = (pageNo - 1) * pageSize;
        for (int i = from; i < Math.min(from + pageSize, ordered.size()); i++) {
            items.add(item(ordered.get(i), drugs.get(ordered.get(i).getDrugId()), true));
        }
        return PageVO.of(items, ordered.size(), pageNo, pageSize).withTabCounts(tabCounts);
    }

    @Override
    public DrugLotDetailVO detail(Long id) {
        DrugLot lot = lookups.requireLot(id);
        Drug drug = drugMapper.selectById(lot.getDrugId());
        String status = status(lot);
        DrugLotDetailVO vo = new DrugLotDetailVO();
        fillItem(vo, lot, drug, status);
        BigDecimal avg = StockQty.zeroIfNull(lot.getAvgPrice());
        vo.setStockAmount(StockQty.amount(lot.getStockAmount()));
        vo.setAvgPrice(StockQty.price(avg));
        vo.setPackPrice(drug == null || drug.getPackSize() == null ? null
                : StockQty.price(avg.multiply(drug.getPackSize())));
        vo.setInboundCount(lot.getInboundCount() == null ? 0 : lot.getInboundCount());
        vo.setHasOutbound(flowMapper.selectCount(Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getDrugLotId, lot.getId())
                .eq(StockFlow::getDirection, StockCodes.DIRECTION_OUT)
                .eq(StockFlow::getStatus, StockCodes.STATUS_NORMAL)) > 0);
        vo.setDrugBanned(drugCatalogQueryService.isBanned(lot.getCatalogId(), null));
        boolean stock = StockQty.zeroIfNull(lot.getStockQty()).signum() > 0;
        boolean usable = DrugLotStatuses.usable(lot, vo.isDrugBanned());
        vo.setActions(StockActions.lot(stock, usable && stock));
        vo.setRecalledAt(BeijingTime.format(lot.getRecalledAt()));
        if (lot.getRecalledAt() != null) {
            vo.setRecallOperatorText("平台管理员");
            vo.setRecallReason(lot.getRecallReason());
        }
        vo.setStockVersion(lot.getVersion());
        StockInbound first = firstInbound(lot.getId());
        if (first != null && drug != null) {
            DrugLotDetailVO.FirstInbound inbound = new DrugLotDetailVO.FirstInbound();
            inbound.setInboundId(first.getId());
            inbound.setInboundDate(BeijingTime.format(first.getInboundDate()));
            inbound.setQty(StockQty.drug(first.getQty(), drug.getUnit(), drug.getPackUnit(),
                    drug.getPackSize()));
            inbound.setUnitPrice(StockQty.price(first.getUnitPrice()));
            vo.setFirstInbound(inbound);
        }
        return vo;
    }

    @Override
    public DrugLotOptionsVO options(Long drugId, String purpose, BigDecimal needQty) {
        Drug drug = lookups.requireDrug(drugId);
        boolean returned = StockCodes.PURPOSE_RETURN.equalsIgnoreCase(purpose);
        List<DrugLot> lots = lotsOfDrug(drugId);
        List<DrugLotOptionVO> selectable = new ArrayList<>();
        List<DrugLotOptionVO> unselectable = new ArrayList<>();
        Long defaultLotId = null;
        boolean firstUsable = true;
        for (DrugLot lot : lots) {
            DrugLotOptionVO vo = option(lot, drug, needQty);
            boolean usable = StockCodes.LOT_NORMAL.equals(vo.getLotStatus())
                    || StockCodes.LOT_NEAR_EXPIRY.equals(vo.getLotStatus());
            if (usable) {
                // 先到期先出：第一个可用批号标“优先出库”并作默认批号（退货不默认）
                vo.setPriorityOut(firstUsable);
                if (firstUsable && !returned) {
                    defaultLotId = lot.getId();
                }
                firstUsable = false;
                selectable.add(vo);
            } else {
                vo.setPriorityOut(false);
                unselectable.add(vo);
            }
        }
        DrugLotOptionsVO vo = new DrugLotOptionsVO();
        vo.setSelectable(selectable);
        vo.setUnselectable(unselectable);
        vo.setDefaultLotId(defaultLotId);
        return vo;
    }

    @Override
    public List<DrugLotOption> optionRecords(Long drugId, String purpose, BigDecimal needQty) {
        DrugLotOptionsVO options = options(drugId, purpose, needQty);
        List<DrugLotOption> records = new ArrayList<>();
        for (DrugLotOptionVO vo : options.getSelectable()) {
            records.add(record(vo));
        }
        for (DrugLotOptionVO vo : options.getUnselectable()) {
            records.add(record(vo));
        }
        return records;
    }

    // ------------------------------------------------------------------ 内部

    private static DrugLotOption record(DrugLotOptionVO vo) {
        return new DrugLotOption(vo.getLotId(), vo.getLotNo(),
                vo.getExpireDate() == null ? null : java.time.LocalDate.parse(vo.getExpireDate()),
                vo.getDaysToExpire(), vo.getLotStatus(), vo.getLotStatusText(), vo.getStock(),
                vo.isPriorityOut(), vo.isInsufficient(), vo.isCanGoScrap(), vo.getAvgPrice(),
                vo.getStockVersion());
    }

    private DrugLotOptionVO option(DrugLot lot, Drug drug, BigDecimal needQty) {
        DrugLotOptionVO vo = new DrugLotOptionVO();
        String status = status(lot);
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setExpireDate(BeijingTime.format(lot.getExpireDate()));
        vo.setDaysToExpire(DrugLotStatuses.daysToExpire(lot.getExpireDate()));
        vo.setLotStatus(status);
        vo.setLotStatusText(StockCodes.lotStatusText(status));
        vo.setStock(StockQty.drug(lot.getStockQty(), drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
        boolean usable = DrugLotStatuses.usable(lot, StockCodes.LOT_BANNED.equals(status));
        boolean insufficient = needQty != null
                && StockQty.zeroIfNull(lot.getStockQty()).compareTo(needQty) < 0;
        vo.setInsufficient(insufficient);
        vo.setCanGoScrap(!usable && StockQty.zeroIfNull(lot.getStockQty()).signum() > 0);
        vo.setAvgPrice(StockQty.price(lot.getAvgPrice()));
        vo.setStockVersion(lot.getVersion());
        return vo;
    }

    private List<DrugLot> lotsOfDrug(Long drugId) {
        return lotMapper.selectList(Wrappers.<DrugLot>lambdaQuery()
                .eq(DrugLot::getDrugId, drugId)
                .gt(DrugLot::getStockQty, BigDecimal.ZERO)
                .orderByAsc(DrugLot::getExpireDate)
                .orderByAsc(DrugLot::getFirstInboundDate)
                .orderByAsc(DrugLot::getId));
    }

    private DrugLotItemVO item(DrugLot lot, Drug drug, boolean markPriority) {
        DrugLotItemVO vo = new DrugLotItemVO();
        String status = status(lot);
        fillItem(vo, lot, drug, status);
        if (markPriority) {
            vo.setPriorityOut(DrugLotStatuses.usable(lot, StockCodes.LOT_BANNED.equals(status))
                    && isFirstUsable(lot, drug));
        }
        return vo;
    }

    private void fillItem(DrugLotItemVO vo, DrugLot lot, Drug drug, String status) {
        vo.setLotId(lot.getId());
        vo.setDrugId(lot.getDrugId());
        vo.setDrugName(drug == null ? null : drug.getName());
        vo.setCategory(lot.getCategory());
        vo.setCategoryText(StockCodes.categoryText(lot.getCategory()));
        vo.setLotNo(lot.getLotNo());
        vo.setExpireDate(BeijingTime.format(lot.getExpireDate()));
        vo.setStock(drug == null
                ? StockQty.drug(lot.getStockQty(), null, null, null)
                : StockQty.drug(lot.getStockQty(), drug.getUnit(), drug.getPackUnit(), drug.getPackSize()));
        vo.setLotStatus(status);
        vo.setLotStatusText(StockCodes.lotStatusText(status));
        vo.setDaysToExpire(DrugLotStatuses.daysToExpire(lot.getExpireDate()));
        vo.setStatusNote(DrugLotStatuses.statusNote(status));
        vo.setPriorityOut(false);
    }

    private boolean isFirstUsable(DrugLot lot, Drug drug) {
        List<DrugLot> lots = lotsOfDrug(lot.getDrugId());
        for (DrugLot candidate : lots) {
            if (DrugLotStatuses.usable(candidate, false)) {
                return candidate.getId().equals(lot.getId());
            }
        }
        return false;
    }

    private String status(DrugLot lot) {
        return DrugLotStatuses.status(lot, settingService.nearDays(lot.getCategory()),
                drugCatalogQueryService.isBanned(lot.getCatalogId(), null));
    }

    private StockInbound firstInbound(Long lotId) {
        return inboundMapper.selectOne(Wrappers.<StockInbound>lambdaQuery()
                .eq(StockInbound::getDrugLotId, lotId)
                .orderByAsc(StockInbound::getId)
                .last("LIMIT 1"));
    }

    private Map<Long, Drug> drugs(List<DrugLot> lots) {
        List<Long> ids = lots.stream().map(DrugLot::getDrugId).distinct().toList();
        Map<Long, Drug> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Drug drug : drugMapper.selectList(Wrappers.<Drug>lambdaQuery().in(Drug::getId, ids))) {
            map.put(drug.getId(), drug);
        }
        return map;
    }

    private static boolean matches(DrugLot lot, Drug drug, String keyword) {
        if (lot.getLotNo() != null && lot.getLotNo().contains(keyword)) {
            return true;
        }
        return drug != null && drug.getName() != null && drug.getName().contains(keyword);
    }

    private static String nameOf(Drug drug) {
        return drug == null || drug.getName() == null ? "" : drug.getName();
    }

    /** 供测试 / 展示：金额 2 位、单价 4 位 */
    @SuppressWarnings("unused")
    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
