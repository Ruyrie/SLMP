package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.HerdTableInfo;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.service.impl.StockServiceImpl;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.ProductionOverviewVO;
import com.zhimu.server.biz.herd.vo.StockSummaryVO;
import com.zhimu.server.biz.inspect.service.InspectionService;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.today.api.TodoQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 存栏汇总口径：870 头、入栏 898、死淘 19、容量 1600、非种畜栏舍 5 个、阶段分组 350/520。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceImplTest {

    @Mock private HerdSupport support;
    @Mock private BatchMapper batchMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private TodoQueryService todoQueryService;
    @Mock private InspectionService inspectionService;

    private StockServiceImpl service;

    @BeforeEach
    void setUp() {
        HerdTableInfo.ensure();
        service = new StockServiceImpl(support, batchMapper, batchPenMapper, todoQueryService, inspectionService);
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        lenient().when(support.context()).thenReturn(FarmContext.get());
        lenient().when(support.farmUnit(1001L)).thenReturn("HEAD");
        lenient().when(support.unitText("HEAD")).thenReturn("头");
        lenient().when(support.category(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD",
                "头", false, true));
        lenient().when(support.category(102L)).thenReturn(new CategoryInfo(102L, "种猪", "PIG", "猪", "HEAD",
                "头", true, true));
        lenient().when(support.pensOfFarm(1001L)).thenReturn(pens());
        lenient().when(support.activeBatches(1001L)).thenReturn(activeBatches());
        lenient().when(support.batches(eq(1001L), eq("ALL"))).thenReturn(allBatches());
        lenient().when(support.countBatches(1001L, "ENDED")).thenReturn(12L);
        lenient().when(support.stageShortName(org.mockito.ArgumentMatchers.any(Batch.class)))
                .thenAnswer(invocation -> {
                    Batch batch = invocation.getArgument(0);
                    return batch.getId() == 3001L || batch.getId() == 3002L ? "育肥猪" : "保育猪";
                });
        lenient().when(todoQueryService.productionSummary())
                .thenReturn(new TodoQueryService.ProductionTodoSummary(6, List.of(
                        new TodoQueryService.WarningTodo(90105L, "FEED_LOW_STOCK", "育肥全价料库存不足",
                                "结存 500 千克 · 剩余可用 1 天", "库存不足", null, "M01"))));
        lenient().when(inspectionService.summary(any())).thenReturn(inspection(0, null));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    @Test
    void summaryMatchesAppMock() {
        StockSummaryVO vo = service.summary();

        assertThat(vo.getTotalStock()).isEqualTo(870);
        assertThat(vo.getActiveBatchCount()).isEqualTo(4);
        assertThat(vo.getWithdrawalBatchCount()).isZero();
        assertThat(vo.getEndedBatchCount()).isEqualTo(12);
        assertThat(vo.getEntryTotal()).isEqualTo(898);
        assertThat(vo.getDeathTotal()).isEqualTo(19);
        assertThat(vo.getCapacityTotal()).isEqualTo(1600);
        assertThat(vo.getCommercialPenCount()).isEqualTo(5);
        assertThat(vo.getMortalityRate()).isEqualByComparingTo(new BigDecimal("0.0212"));
        assertThat(vo.getOccupancyRate()).isEqualByComparingTo(new BigDecimal("0.5438"));
        assertThat(vo.getUnit()).isEqualTo("HEAD");
        assertThat(vo.getUnitText()).isEqualTo("头");
        assertThat(vo.getCategories()).hasSize(1);
        assertThat(vo.getCategories().get(0).getStock()).isEqualTo(870);
        assertThat(vo.getCategories().get(0).getBatchCount()).isEqualTo(4);
        assertThat(vo.getCategories().get(0).getStageGroups())
                .anySatisfy(group -> {
                    assertThat(group.getStageShortName()).isEqualTo("育肥猪");
                    assertThat(group.getStock()).isEqualTo(350);
                })
                .anySatisfy(group -> {
                    assertThat(group.getStageShortName()).isEqualTo("保育猪");
                    assertThat(group.getStock()).isEqualTo(520);
                });
    }

    @Test
    void overviewFillsTodoAndInspectionCounters() {
        when(inspectionService.summary(any())).thenReturn(inspection(1, "3 号育肥舍"));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getTodayTodoCount()).isEqualTo(6);
        assertThat(vo.getWarningCount()).isEqualTo(1);
        assertThat(vo.getReminderTotal()).isEqualTo(2);
        assertThat(vo.getReminders()).hasSize(2);
        assertThat(vo.getReminders().get(0).getReminderType()).isEqualTo("FEED_LOW_STOCK");
        assertThat(vo.getReminders().get(0).getTodoId()).isEqualTo(90105L);
        assertThat(vo.getReminders().get(1).getReminderType()).isEqualTo("URGENT_DIAGNOSIS");
        assertThat(vo.getReminders().get(1).getRoute()).isEqualTo("I04");
        assertThat(vo.getBadges().getFeedLowStock()).isTrue();
        // 08 未接入：休药中批次为 0
        assertThat(vo.getBadges().getWithdrawal()).isFalse();
    }

    @Test
    void withdrawalRemindersRemainVisibleWhenWarningListIsEmpty() {
        when(todoQueryService.productionSummary())
                .thenReturn(new TodoQueryService.ProductionTodoSummary(15, List.of()));
        when(support.withdrawalReleaseDates(1001L)).thenReturn(java.util.Map.of(
                3001L, com.zhimu.server.common.util.BeijingTime.today().plusDays(6),
                3002L, com.zhimu.server.common.util.BeijingTime.today().plusDays(11)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getWarningCount()).isZero();
        assertThat(vo.getReminderTotal()).isEqualTo(2);
        assertThat(vo.getReminders()).extracting(ProductionOverviewVO.ProductionReminderItemVO::getReminderType)
                .containsExactly("WITHDRAWAL", "WITHDRAWAL");
        assertThat(vo.getBadges().getWithdrawal()).isTrue();
    }

    @Test
    void inspectionSummaryDoesNotInventAdditionalWarningsAfterResolution() {
        when(todoQueryService.productionSummary())
                .thenReturn(new TodoQueryService.ProductionTodoSummary(0, List.of()));
        when(inspectionService.summary(any())).thenReturn(inspection(2, "3 号育肥舍"));
        assertThat(service.overview().getWarningCount()).isZero();
    }

    @Test
    void stockPensKeywordMatchesBatchNo() {
        when(support.pensOfFarm(1001L)).thenReturn(pens().subList(0, 5));
        Batch active = batch(3001L, 200, 5, 205);
        active.setBatchNo("ZM2610-031");
        when(support.activeBatches(1001L)).thenReturn(List.of(active));
        com.zhimu.server.biz.herd.entity.BatchPen row = new com.zhimu.server.biz.herd.entity.BatchPen();
        row.setBatchId(3001L);
        row.setPenId(2003L);
        row.setStockCount(200);
        when(batchPenMapper.selectList(any())).thenReturn(List.of(row));
        when(batchMapper.selectList(any())).thenReturn(List.of());
        lenient().when(support.penTypeText(any())).thenReturn("育肥舍");
        lenient().when(support.dayAge(any())).thenReturn(96);
        lenient().when(support.stageName(any())).thenReturn("育肥期");

        var page = service.pens(query("ZM2610-031"));

        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().get(0).getPenId()).isEqualTo(2003L);
        assertThat(page.getList().get(0).getFirstBatch().getBatchNo()).isEqualTo("ZM2610-031");
    }

    private static com.zhimu.server.biz.herd.query.StockPenQuery query(String keyword) {
        com.zhimu.server.biz.herd.query.StockPenQuery query =
                new com.zhimu.server.biz.herd.query.StockPenQuery();
        query.setKeyword(keyword);
        query.setPage(1);
        query.setSize(20);
        return query;
    }

    private static InspectionSummaryVO inspection(int abnormal, String firstPen) {
        InspectionSummaryVO vo = new InspectionSummaryVO();
        vo.setAbnormalCount(abnormal);
        vo.setFirstAbnormalPenName(firstPen);
        vo.setAllDone(abnormal == 0);
        return vo;
    }

    private static List<Pen> pens() {
        List<Pen> pens = new ArrayList<>();
        pens.add(pen(2001L, "BY-01", 360, 320, 101L, false));
        pens.add(pen(2002L, "BY-02", 400, 200, 101L, false));
        pens.add(pen(2003L, "YF-03", 220, 200, 101L, false));
        pens.add(pen(2004L, "YF-04", 220, 150, 101L, false));
        pens.add(pen(2005L, "YF-05", 400, 0, 101L, false));
        pens.add(pen(2006L, "ZC-01", 60, 0, 102L, true));
        pens.add(pen(2007L, "ZC-02", 40, 0, 102L, true));
        return pens;
    }

    private static Pen pen(Long id, String code, int capacity, int stock, Long categoryId, boolean ignored) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenCode(code);
        pen.setPenName(code);
        pen.setPenType("FATTENING");
        pen.setCategoryId(categoryId);
        pen.setCapacity(capacity);
        pen.setStockCount(stock);
        pen.setStatus("ENABLED");
        pen.setVersion(4);
        return pen;
    }

    private static List<Batch> activeBatches() {
        return List.of(batch(3001L, 200, 5, 205), batch(3002L, 150, 2, 152),
                batch(3003L, 320, 6, 326), batch(3004L, 200, 5, 215));
    }

    private static List<Batch> allBatches() {
        List<Batch> batches = new ArrayList<>(activeBatches());
        Batch ended = batch(3010L, 0, 1, 100);
        ended.setStatus("ENDED");
        batches.add(ended);
        return batches;
    }

    private static Batch batch(Long id, int current, int death, int entry) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-0" + id);
        batch.setCategoryId(101L);
        batch.setBreedId(1101L);
        batch.setPurpose("COMMERCIAL");
        batch.setSource("PURCHASED");
        batch.setEntryDate(java.time.LocalDate.of(2026, 10, 1));
        batch.setEntryAgeDays(78);
        batch.setEntryCount(entry);
        batch.setCurrentCount(current);
        batch.setDeathCount(death);
        batch.setSoldCount(0);
        batch.setStatus("ACTIVE");
        batch.setVersion(1);
        return batch;
    }
}
