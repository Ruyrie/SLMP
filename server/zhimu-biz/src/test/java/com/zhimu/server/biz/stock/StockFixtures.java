package com.zhimu.server.biz.stock;

import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** 模块09 单测公共夹具：演示时钟 2026-10-19 09:12（北京），东山猪场 1001。 */
public final class StockFixtures {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 2026-10-19 09:12:00 北京 */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T01:12:00Z"), ZONE);
    public static final Long FARM_ID = 1001L;
    public static final Long OWNER_ID = 10001L;

    private StockFixtures() {
    }

    public static void loginOwner() {
        login(OWNER_ID, 5001L, FarmRole.OWNER);
    }

    public static void loginManager() {
        login(10002L, 5002L, FarmRole.MANAGER);
    }

    public static void loginStaff(Long userId) {
        login(userId, 5003L, FarmRole.STAFF);
    }

    public static void login(Long userId, Long memberId, FarmRole role) {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "test-jti"));
        FarmContext.set(new FarmContext.FarmMember(FARM_ID, "东山猪场", userId, memberId, role, Set.of(),
                FarmStatus.NORMAL));
    }

    public static void logout() {
        UserContext.clear();
        FarmContext.clear();
    }

    public static void useClock() {
        BeijingTime.setClock(CLOCK);
    }

    public static void resetClock() {
        BeijingTime.setClock(null);
    }

    /** 饲料 5101 育肥全价料：500 千克、1,600 元、均价 3.2、结存版本 12（与 mock 一致） */
    public static Feed feed(long id, String name, String qty, String amount, String avgPrice, int version) {
        Feed feed = new Feed();
        feed.setId(id);
        feed.setFarmId(FARM_ID);
        feed.setName(name);
        feed.setFeedTypeCode("COMPLETE");
        feed.setSpec("40 千克/袋");
        feed.setUnit("KG");
        feed.setPackUnit("袋");
        feed.setPackSize(new BigDecimal("40.000"));
        feed.setCategoryId(101L);
        feed.setMinStock(new BigDecimal("800.00"));
        feed.setWarnDays(7);
        feed.setStockQty(new BigDecimal(qty));
        feed.setStockAmount(new BigDecimal(amount));
        feed.setAvgPrice(new BigDecimal(avgPrice));
        feed.setVersion(version);
        feed.setEditVersion(3);
        feed.setDeleted(0);
        feed.setCreatedAt(LocalDate.of(2026, 3, 5).atTime(9, 0));
        feed.setUpdatedAt(LocalDate.of(2026, 10, 10).atTime(9, 15));
        feed.setCreatedBy(OWNER_ID);
        feed.setUpdatedBy(OWNER_ID);
        return feed;
    }

    public static Drug drug(long id, String name, String category, long catalogId, String unit, String packUnit,
                            String packSize) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setFarmId(FARM_ID);
        drug.setName(name);
        drug.setCategory(category);
        drug.setCatalogId(catalogId);
        drug.setSpec("10 头份/瓶");
        drug.setUnit(unit);
        drug.setPackUnit(packUnit);
        drug.setPackSize(packSize == null ? null : new BigDecimal(packSize));
        drug.setMinStock(null);
        drug.setMinStockUnit(null);
        drug.setIsLowStock(false);
        drug.setIsUnitLocked(true);
        drug.setVersion(1);
        drug.setDeleted(0);
        drug.setCreatedBy(OWNER_ID);
        drug.setUpdatedBy(OWNER_ID);
        drug.setCreatedAt(LocalDate.of(2026, 9, 10).atTime(10, 0));
        drug.setUpdatedAt(LocalDate.of(2026, 9, 10).atTime(10, 0));
        return drug;
    }

    public static DrugLot lot(long id, long drugId, String lotNo, String expireDate, String qty, String amount,
                              String avgPrice, int version) {
        DrugLot lot = new DrugLot();
        lot.setId(id);
        lot.setFarmId(FARM_ID);
        lot.setDrugId(drugId);
        lot.setCatalogId(7001L);
        lot.setCategory("VACCINE");
        lot.setLotNo(lotNo);
        lot.setExpireDate(expireDate == null ? null : LocalDate.parse(expireDate));
        lot.setFirstInboundDate(LocalDate.of(2026, 9, 10));
        lot.setInboundCount(1);
        lot.setStockQty(new BigDecimal(qty));
        lot.setStockAmount(new BigDecimal(amount));
        lot.setAvgPrice(new BigDecimal(avgPrice));
        lot.setVersion(version);
        lot.setCreatedAt(LocalDate.of(2026, 9, 10).atTime(10, 0));
        lot.setUpdatedAt(LocalDate.of(2026, 9, 10).atTime(10, 0));
        return lot;
    }

    public static StockFlow feedFlow(long id, long feedId, String flowType, String direction, String qty,
                                     String unitPrice, String amount, String bizType, Long bizId) {
        StockFlow flow = new StockFlow();
        flow.setId(id);
        flow.setFarmId(FARM_ID);
        flow.setMaterialType("FEED");
        flow.setFeedId(feedId);
        flow.setFlowType(flowType);
        flow.setDirection(direction);
        flow.setQty(new BigDecimal(qty));
        flow.setUnitPrice(new BigDecimal(unitPrice));
        flow.setAmount(new BigDecimal(amount));
        flow.setStockQtyAfter(new BigDecimal("500.000"));
        flow.setStockAmountAfter(new BigDecimal("1600.00"));
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setStatus("NORMAL");
        flow.setOccurAt(LocalDate.of(2026, 10, 19).atTime(7, 12));
        flow.setBizDate(LocalDate.of(2026, 10, 19));
        flow.setOriginFlowId(id);
        flow.setIsBatchCost(false);
        flow.setIsFeedUsage(false);
        flow.setCreatedAt(LocalDate.of(2026, 10, 19).atTime(7, 12));
        flow.setUpdatedAt(LocalDate.of(2026, 10, 19).atTime(7, 12));
        return flow;
    }

    public static CostAllocationVO.Row allocationRow(long batchId, String batchNo, int headCount, String ratio,
                                                     String amount) {
        CostAllocationVO.Row row = new CostAllocationVO.Row();
        row.setBatchId(batchId);
        row.setBatchNo(batchNo);
        row.setHeadCount(headCount);
        row.setRatio(new BigDecimal(ratio));
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    public static List<String> memberNames() {
        return List.of("王建国", "刘芳", "李明", "张伟");
    }
}
