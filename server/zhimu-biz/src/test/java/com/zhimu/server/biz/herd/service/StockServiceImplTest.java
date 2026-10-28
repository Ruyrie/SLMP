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
        lenient().when(inspectionService.dayAbnormal(any())).thenReturn(inspection(true));
        lenient().when(support.pensVisibleTo(any())).thenReturn(pens());
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
        when(inspectionService.dayAbnormal(any())).thenReturn(inspection(false, abnormalPen(2003L, "3 号育肥舍", null)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getTodayTodoCount()).isEqualTo(6);
        assertThat(vo.getWarningCount()).isEqualTo(1);
        assertThat(vo.getReminderTotal()).isEqualTo(2);
        assertThat(vo.getReminders()).hasSize(2);
        assertThat(vo.getReminders().get(0).getReminderType()).isEqualTo("FEED_LOW_STOCK");
        assertThat(vo.getReminders().get(0).getTodoId()).isEqualTo(90105L);
        assertThat(vo.getReminders().get(0).getReportId()).isNull();
        assertThat(vo.getReminders().get(1).getReminderType()).isEqualTo("URGENT_DIAGNOSIS");
        assertThat(vo.getReminders().get(1).getSubtitle()).isEqualTo("3 号育肥舍 · 1 个栏舍待处理");
        assertThat(vo.getReminders().get(1).getRoute()).isEqualTo("I04");
        assertThat(vo.getReminders().get(1).getIsWarning()).isTrue();
        assertThat(vo.getBadges().getFeedLowStock()).isTrue();
        // 08 未接入：休药中批次为 0
        assertThat(vo.getBadges().getWithdrawal()).isFalse();
    }

    @Test
    void openAbnormalReportsAreListedBeforeOtherWarningsAndDedupeInspectionSummary() {
        when(todoQueryService.productionSummary()).thenReturn(new TodoQueryService.ProductionTodoSummary(24, List.of(
                new TodoQueryService.WarningTodo(90105L, "FEED_LOW_STOCK", "育肥全价料库存不足",
                        "结存 500 千克 · 剩余可用 1 天", "库存不足", null, "M01"),
                new TodoQueryService.WarningTodo(90201L, "ABNORMAL_REPORT", "3 号育肥舍 · AI 诊断待处理",
                        "疑似猪流行性腹泻 · 发病 5 头 · ZM2610-031", "待处理", 3001L, "I06", 2003L, 7001L),
                new TodoQueryService.WarningTodo(90202L, "ABNORMAL_REPORT", "1 号保育舍 · 异常上报待诊断",
                        "发病 2 头", "待处理", null, "I06", 2001L, 7002L))));
        when(support.withdrawalReleaseDates(1001L)).thenReturn(java.util.Map.of(
                3002L, com.zhimu.server.common.util.BeijingTime.today().plusDays(6)));
        // 今日巡检异常：2003 已有未处理上报（按栏舍去重），2002 关联的上报 7002 已列出（按上报去重），只剩 2004
        when(inspectionService.dayAbnormal(any())).thenReturn(inspection(true,
                abnormalPen(2003L, "3 号育肥舍", 7001L),
                abnormalPen(2002L, "2 号保育舍", 7002L),
                abnormalPen(2004L, "4 号育肥舍", null)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getReminders()).extracting(ProductionOverviewVO.ProductionReminderItemVO::getReminderType)
                .containsExactly("WITHDRAWAL", "ABNORMAL_REPORT", "ABNORMAL_REPORT", "FEED_LOW_STOCK",
                        "URGENT_DIAGNOSIS");
        ProductionOverviewVO.ProductionReminderItemVO report = vo.getReminders().get(1);
        assertThat(report.getIsWarning()).isTrue();
        assertThat(report.getTitle()).isEqualTo("3 号育肥舍 · AI 诊断待处理");
        assertThat(report.getSubtitle()).isEqualTo("疑似猪流行性腹泻 · 发病 5 头 · ZM2610-031");
        assertThat(report.getTagText()).isEqualTo("待处理");
        assertThat(report.getRoute()).isEqualTo("I06");
        assertThat(report.getReportId()).isEqualTo(7001L);
        assertThat(report.getTodoId()).isEqualTo(90201L);
        assertThat(vo.getReminders().get(2).getReportId()).isEqualTo(7002L);
        assertThat(vo.getReminders().get(3).getReportId()).isNull();
        ProductionOverviewVO.ProductionReminderItemVO summary = vo.getReminders().get(4);
        assertThat(summary.getSubtitle()).isEqualTo("4 号育肥舍 · 1 个栏舍待处理");
        assertThat(summary.getIsWarning()).isFalse();
        assertThat(vo.getReminderTotal()).isEqualTo(5).isEqualTo(vo.getReminders().size());
        // 老字段保留：warning_count 与首页预警合集同口径（含异常上报）
        assertThat(vo.getTodayTodoCount()).isEqualTo(24);
        assertThat(vo.getWarningCount()).isEqualTo(3);
    }

    @Test
    void inspectionSummaryIsOmittedWhenEveryAbnormalPenHasAnOpenReport() {
        when(todoQueryService.productionSummary()).thenReturn(new TodoQueryService.ProductionTodoSummary(3, List.of(
                new TodoQueryService.WarningTodo(90201L, "ABNORMAL_REPORT", "3 号育肥舍 · 异常上报待诊断",
                        "发病 5 头", "待处理", 3001L, "I06", 2003L, 7001L))));
        when(inspectionService.dayAbnormal(any())).thenReturn(inspection(false, abnormalPen(2003L, "3 号育肥舍", 7001L)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getReminders()).extracting(ProductionOverviewVO.ProductionReminderItemVO::getReminderType)
                .containsExactly("ABNORMAL_REPORT");
        assertThat(vo.getReminderTotal()).isEqualTo(1);
    }

    @Test
    void overviewCountsActiveBatchesEnabledPensAndOccupiedPens() {
        List<Pen> pens = pens();
        pens.get(4).setStatus("DISABLED");
        when(support.pensVisibleTo(any())).thenReturn(pens);
        when(batchPenMapper.selectList(any())).thenReturn(List.of(
                batchPen(3001L, 2003L, 200), batchPen(3002L, 2004L, 150), batchPen(3003L, 2001L, 320),
                batchPen(3004L, 2002L, 200), batchPen(3004L, 2005L, 1)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getActiveBatchCount()).isEqualTo(4);
        assertThat(vo.getPenCount()).isEqualTo(6);
        // 2005 已停用，不计入占用
        assertThat(vo.getOccupiedPenCount()).isEqualTo(4);
    }

    @Test
    void staffOverviewCountsOnlyVisibleBatchesAndResponsiblePens() {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L, FarmRole.STAFF,
                Set.of(2003L, 2005L), FarmStatus.NORMAL));
        when(support.context()).thenReturn(FarmContext.get());
        when(support.pensVisibleTo(any())).thenReturn(List.of(pens().get(2), pens().get(4)));
        when(support.visibleToStaff(any(Batch.class), any()))
                .thenAnswer(invocation -> ((Batch) invocation.getArgument(0)).getId() == 3001L);
        when(batchPenMapper.selectList(any())).thenReturn(List.of(
                batchPen(3001L, 2003L, 200), batchPen(3002L, 2004L, 150)));

        ProductionOverviewVO vo = service.overview();

        assertThat(vo.getActiveBatchCount()).isEqualTo(1);
        assertThat(vo.getPenCount()).isEqualTo(2);
        assertThat(vo.getOccupiedPenCount()).isEqualTo(1);
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
        when(inspectionService.dayAbnormal(any())).thenReturn(inspection(true,
                abnormalPen(2003L, "3 号育肥舍", null), abnormalPen(2004L, "4 号育肥舍", null)));
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

    private static InspectionService.DayAbnormal inspection(boolean allDone, InspectionService.AbnormalPen... pens) {
        return new InspectionService.DayAbnormal(allDone, List.of(pens));
    }

    private static InspectionService.AbnormalPen abnormalPen(Long penId, String penName, Long reportId) {
        return new InspectionService.AbnormalPen(penId, penName, penId + 50000L, reportId);
    }

    private static com.zhimu.server.biz.herd.entity.BatchPen batchPen(Long batchId, Long penId, int stock) {
        com.zhimu.server.biz.herd.entity.BatchPen row = new com.zhimu.server.biz.herd.entity.BatchPen();
        row.setFarmId(1001L);
        row.setBatchId(batchId);
        row.setPenId(penId);
        row.setStockCount(stock);
        return row;
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
