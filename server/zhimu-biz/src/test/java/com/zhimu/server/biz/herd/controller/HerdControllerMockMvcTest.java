package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.HerdTestMvc;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.service.PenService;
import com.zhimu.server.biz.herd.service.StockService;
import com.zhimu.server.biz.herd.vo.BatchDetailVO;
import com.zhimu.server.biz.herd.vo.BatchEntryResultVO;
import com.zhimu.server.biz.herd.vo.BatchEventDetailVO;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.BatchLookupVO;
import com.zhimu.server.biz.herd.vo.BatchPenItem;
import com.zhimu.server.biz.herd.vo.CategoryStockVO;
import com.zhimu.server.biz.herd.vo.DeathResultVO;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.MyPenItemVO;
import com.zhimu.server.biz.herd.vo.MyPensVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;
import com.zhimu.server.biz.herd.vo.PenStockItemVO;
import com.zhimu.server.biz.herd.vo.PenStockVO;
import com.zhimu.server.biz.herd.vo.ProductionOverviewVO;
import com.zhimu.server.biz.herd.vo.RequestCheckVO;
import com.zhimu.server.biz.herd.vo.StockSummaryVO;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.herd.vo.TransferResultVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.OptionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 27 个 App 接口映射的 standalone MockMvc 测试：路径全部命中，关键字段名按 mock 断言，
 * 并验证业务错误码转换为 HTTP 状态码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HerdControllerMockMvcTest {

    private static final String JSON = "application/json";
    private static final String IDEM = "Idempotency-Key";

    @Mock private StockService stockService;
    @Mock private PenService penService;
    @Mock private BatchService batchService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = HerdTestMvc.builder(
                new ProductionController(stockService),
                new StockController(stockService),
                new PenController(penService),
                new BatchController(batchService),
                new BatchEventController(batchService),
                new BatchEventWriteController(batchService)).build();
        stubSuccess();
    }

    @Test
    void l01ToL05RoutesReturnKeyFields() throws Exception {
        mvc.perform(get("/api/v1/production/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.farm_id").value(1001))
                .andExpect(jsonPath("$.data.farm_status").value("NORMAL"))
                .andExpect(jsonPath("$.data.today").value("2026-10-19"))
                .andExpect(jsonPath("$.data.stock_total").value(870))
                .andExpect(jsonPath("$.data.has_pen").value(true))
                .andExpect(jsonPath("$.data.unit").value("HEAD"))
                .andExpect(jsonPath("$.data.unit_text").value("头"));

        mvc.perform(get("/api/v1/my-pens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_count").value(2))
                .andExpect(jsonPath("$.data.pens[0].pen_id").value(2003))
                .andExpect(jsonPath("$.data.pens[0].batches[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data.pens[0].batches[0].day_age").value(96));

        mvc.perform(get("/api/v1/stock/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_stock").value(870))
                .andExpect(jsonPath("$.data.active_batch_count").value(4))
                .andExpect(jsonPath("$.data.death_total").value(19))
                .andExpect(jsonPath("$.data.mortality_rate").value(0.0212));

        mvc.perform(get("/api/v1/stock/pens").param("sort", "OCCUPANCY").param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].pen_id").value(2001))
                .andExpect(jsonPath("$.data.list[0].stock_count").value(320))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.has_more").value(false));

        mvc.perform(get("/api/v1/stock/categories").param("keyword", "猪"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category_id").value(101))
                .andExpect(jsonPath("$.data[0].stock").value(870));
    }

    @Test
    void l07ToL13AndL25RoutesReturnKeyFields() throws Exception {
        mvc.perform(get("/api/v1/pens").param("usage", "OPTIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pen_id").value(2001))
                .andExpect(jsonPath("$.data[0].stock_count").value(320));

        mvc.perform(get("/api/v1/pens/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_types[0].value").value("NURSERY"))
                .andExpect(jsonPath("$.data.categories[0].category_id").value(101));

        mvc.perform(get("/api/v1/pens/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_id").value(2001))
                .andExpect(jsonPath("$.data.stock_count").value(320))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        mvc.perform(post("/api/v1/pens").header(IDEM, "pen-1").contentType(JSON)
                        .content("{\"pen_code\":\"YF-06\",\"pen_name\":\"6 号育肥舍\",\"pen_type\":\"FATTENING\","
                                + "\"category_id\":101,\"capacity\":220}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_id").value(2001))
                .andExpect(jsonPath("$.data.pen_code").value("BY-01"));

        mvc.perform(put("/api/v1/pens/2001").contentType(JSON)
                        .content("{\"pen_name\":\"1 号保育舍\",\"pen_type\":\"NURSERY\",\"category_id\":101,"
                                + "\"capacity\":360,\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(4));

        mvc.perform(post("/api/v1/pens/2001/disable").contentType(JSON).content("{\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_id").value(2001));

        mvc.perform(post("/api/v1/pens/2001/enable").contentType(JSON).content("{\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_id").value(2001));

        mvc.perform(get("/api/v1/pens/2001/executors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].user_id").value(10001))
                .andExpect(jsonPath("$.data[0].is_me").value(true));
    }

    @Test
    void l06AndL14ToL19RoutesReturnKeyFields() throws Exception {
        mvc.perform(get("/api/v1/batches").param("status", "ACTIVE").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data.list[0].current_count").value(200))
                .andExpect(jsonPath("$.data.list[0].day_age").value(96))
                .andExpect(jsonPath("$.data.list[0].batch_no").value("ZM2610-031"));

        mvc.perform(get("/api/v1/batches/lookup").param("batch_no", "ZM2610-031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("OK"))
                .andExpect(jsonPath("$.data.batch_id").value(3001));

        mvc.perform(get("/api/v1/batches/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_id").value(3001))
                .andExpect(jsonPath("$.data.current_count").value(200))
                .andExpect(jsonPath("$.data.version").value(7))
                .andExpect(jsonPath("$.data.entry_date").value("2026-10-01"));

        mvc.perform(get("/api/v1/batches/3001/timeline").param("type", "DEATH").param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].biz_id").value(905))
                .andExpect(jsonPath("$.data.list[0].record_type").value("DEATH"));

        mvc.perform(get("/api/v1/batches/3001/pen-stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pen_id").value(2003))
                .andExpect(jsonPath("$.data[0].stock_count").value(200));

        mvc.perform(get("/api/v1/batch-events/905"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(905))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(get("/api/v1/batch-events/by-request/req-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false));
    }

    @Test
    void l18AndL20ToL28RoutesReturnKeyFields() throws Exception {
        mvc.perform(post("/api/v1/batches").header(IDEM, "batch-1").contentType(JSON)
                        .content("{\"pen_id\":2004,\"breed_id\":1101,\"source\":\"PURCHASED\","
                                + "\"entry_date\":\"2026-10-19\",\"entry_age_days\":70,\"entry_count\":90,"
                                + "\"entry_avg_weight\":28.5,\"intro_unit_price\":520,\"capacity_confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_id").value(3058))
                .andExpect(jsonPath("$.data.pen_id").value(2004))
                .andExpect(jsonPath("$.data.batch_no").value("ZM2610-058"))
                .andExpect(jsonPath("$.data.pen_stock_after").value(240));

        mvc.perform(put("/api/v1/batches/3001/entry").contentType(JSON)
                        .content("{\"pen_id\":2003,\"breed_id\":1101,\"source\":\"PURCHASED\","
                                + "\"entry_date\":\"2026-10-01\",\"entry_age_days\":78,\"entry_count\":205,"
                                + "\"entry_avg_weight\":45,\"intro_unit_price\":500,\"version\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_id").value(3001));

        mvc.perform(delete("/api/v1/batches/3001").param("version", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(post("/api/v1/batch-transfers").header(IDEM, "tr-1").contentType(JSON)
                        .content("{\"batch_id\":3001,\"from_pen_id\":2003,\"to_pen_id\":2004,\"quantity\":100,"
                                + "\"event_date\":\"2026-10-19\",\"capacity_confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(907))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.from_pen.stock_count").value(100))
                .andExpect(jsonPath("$.data.to_pen.stock_count").value(250));

        mvc.perform(put("/api/v1/batch-transfers/907").contentType(JSON)
                        .content("{\"from_pen_id\":2003,\"to_pen_id\":2004,\"quantity\":20,"
                                + "\"event_date\":\"2026-10-18\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(907))
                .andExpect(jsonPath("$.data.quantity").value(20));

        mvc.perform(post("/api/v1/batch-deaths").header(IDEM, "death-1").contentType(JSON)
                        .content("{\"batch_id\":3001,\"pen_id\":2003,\"quantity\":10,\"reason_code\":\"SICK\","
                                + "\"event_date\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(905))
                .andExpect(jsonPath("$.data.current_count").value(190))
                .andExpect(jsonPath("$.data.message").value("已登记死淘 10 头，存栏已更新"));

        mvc.perform(put("/api/v1/batch-deaths/905").contentType(JSON)
                        .content("{\"pen_id\":2003,\"quantity\":2,\"reason_code\":\"SICK\","
                                + "\"event_date\":\"2026-10-17\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(905))
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    @Test
    void businessErrorsBecomeHttpStatusAndData() throws Exception {
        when(batchService.create(any(), any())).thenThrow(new BizException(ErrorCode.CAPACITY_CONFIRM,
                "入栏后超过栏舍容量。4 号育肥舍容量 220 头，现有 180 头，入栏后共 240 头，超出 20 头。",
                Map.of("need_confirm", true, "pen_id", 2004L, "capacity", 220, "current", 180, "after", 240,
                        "over", 20)));
        mvc.perform(post("/api/v1/batches").header(IDEM, "batch-2").contentType(JSON)
                        .content("{\"pen_id\":2004}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42206))
                .andExpect(jsonPath("$.data.need_confirm").value(true))
                .andExpect(jsonPath("$.data.pen_id").value(2004));

        when(batchService.detail(3009L)).thenThrow(new BizException(ErrorCode.PEN_FORBIDDEN,
                "你没有这个批次的查看权限"));
        mvc.perform(get("/api/v1/batches/3009"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40304));

        when(penService.detail(2001L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/v1/pens/2001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    private void stubSuccess() {
        ProductionOverviewVO overview = new ProductionOverviewVO();
        overview.setFarmId(1001L);
        overview.setFarmName("东山猪场");
        overview.setFarmStatus("NORMAL");
        overview.setToday("2026-10-19");
        overview.setHasPen(true);
        overview.setStockTotal(870);
        overview.setTodayTodoCount(0);
        overview.setWarningCount(0);
        overview.setReminderTotal(0);
        overview.setUnit("HEAD");
        overview.setUnitText("头");
        overview.setReminders(List.of());
        lenient().when(stockService.overview()).thenReturn(overview);

        MyPensVO myPens = new MyPensVO();
        myPens.setFarmName("东山猪场");
        myPens.setPenCount(2);
        MyPenItemVO pen = new MyPenItemVO();
        pen.setPenId(2003L);
        pen.setPenCode("YF-03");
        pen.setPenName("3 号育肥舍");
        pen.setStockCount(200);
        pen.setCapacity(220);
        com.zhimu.server.biz.herd.vo.MyPenBatchVO penBatch = new com.zhimu.server.biz.herd.vo.MyPenBatchVO();
        penBatch.setBatchId(3001L);
        penBatch.setBatchNo("ZM2610-031");
        penBatch.setQtyInPen(200);
        penBatch.setDayAge(96);
        pen.setBatches(List.of(penBatch));
        myPens.setPens(List.of(pen));
        lenient().when(stockService.myPens()).thenReturn(myPens);

        StockSummaryVO summary = new StockSummaryVO();
        summary.setTotalStock(870);
        summary.setActiveBatchCount(4);
        summary.setEndedBatchCount(12);
        summary.setEntryTotal(898);
        summary.setDeathTotal(19);
        summary.setCapacityTotal(1600);
        summary.setCommercialPenCount(5);
        summary.setMortalityRate(new java.math.BigDecimal("0.0212"));
        summary.setOccupancyRate(new java.math.BigDecimal("0.5438"));
        summary.setUnit("HEAD");
        summary.setUnitText("头");
        summary.setCategories(List.of());
        lenient().when(stockService.summary()).thenReturn(summary);

        PenStockItemVO item = new PenStockItemVO();
        item.setPenId(2001L);
        item.setPenName("1 号保育舍");
        item.setStockCount(320);
        item.setDisplayStatus("IN_USE");
        lenient().when(stockService.pens(any())).thenReturn(new PageVO<>(List.of(item), 1, 1, 20));

        CategoryStockVO category = new CategoryStockVO();
        category.setCategoryId(101L);
        category.setCategoryName("育肥猪");
        category.setStock(870);
        lenient().when(stockService.categories(any())).thenReturn(List.of(category));

        PenOptionVO option = new PenOptionVO();
        option.setPenId(2001L);
        option.setPenName("1 号保育舍");
        option.setStockCount(320);
        lenient().when(penService.options(any())).thenReturn(List.of(option));

        PenFormOptionsVO formOptions = new PenFormOptionsVO();
        formOptions.setPenTypes(List.of(OptionVO.of("NURSERY", "保育舍")));
        PenFormOptionsVO.CategoryOption categoryOption = new PenFormOptionsVO.CategoryOption();
        categoryOption.setCategoryId(101L);
        categoryOption.setCategoryName("育肥猪");
        formOptions.setCategories(List.of(categoryOption));
        lenient().when(penService.formOptions()).thenReturn(formOptions);

        PenDetailVO penDetail = new PenDetailVO();
        penDetail.setPenId(2001L);
        penDetail.setPenCode("BY-01");
        penDetail.setPenName("1 号保育舍");
        penDetail.setStockCount(320);
        penDetail.setVersion(4);
        penDetail.setStatus("ENABLED");
        lenient().when(penService.detail(anyLong())).thenReturn(penDetail);
        lenient().when(penService.create(any())).thenReturn(penDetail);
        lenient().when(penService.update(anyLong(), any())).thenReturn(penDetail);
        lenient().when(penService.disable(anyLong(), any())).thenReturn(penDetail);
        lenient().when(penService.enable(anyLong(), any())).thenReturn(penDetail);

        ExecutorOptionVO executor = new ExecutorOptionVO();
        executor.setUserId(10001L);
        executor.setName("王建国");
        executor.setDisplayName("王建国（我）");
        executor.setRole("OWNER");
        executor.setIsMe(true);
        lenient().when(penService.executors(anyLong())).thenReturn(List.of(executor));

        BatchListItemVO batchItem = new BatchListItemVO();
        batchItem.setBatchId(3001L);
        batchItem.setBatchNo("ZM2610-031");
        batchItem.setCurrentCount(200);
        batchItem.setDayAge(96);
        PageVO<BatchListItemVO> batchPage = new PageVO<>(List.of(batchItem), 1, 1, 20);
        batchPage.setTabCounts(Map.of("ACTIVE", 1, "ENDED", 0, "ALL", 1));
        lenient().when(batchService.list(any())).thenReturn(batchPage);

        BatchLookupVO lookup = new BatchLookupVO();
        lookup.setResult(BatchLookupVO.OK);
        lookup.setBatchId(3001L);
        lookup.setBatchNo("ZM2610-031");
        lookup.setFarmId(1001L);
        lenient().when(batchService.lookup(any(), any())).thenReturn(lookup);

        BatchDetailVO detail = new BatchDetailVO();
        detail.setBatchId(3001L);
        detail.setCurrentCount(200);
        detail.setVersion(7);
        detail.setEntryDate("2026-10-01");
        lenient().when(batchService.detail(anyLong())).thenReturn(detail);
        lenient().when(batchService.updateEntry(anyLong(), any())).thenReturn(detail);

        TimelineItemVO timeline = new TimelineItemVO();
        timeline.setId(8102L);
        timeline.setBizId(905L);
        timeline.setRecordType("DEATH");
        lenient().when(batchService.timeline(anyLong(), any()))
                .thenReturn(new PageVO<>(List.of(timeline), 1, 1, 20));

        PenStockVO penStock = new PenStockVO();
        penStock.setPenId(2003L);
        penStock.setPenName("3 号育肥舍");
        penStock.setStockCount(200);
        lenient().when(batchService.penStocks(anyLong())).thenReturn(List.of(penStock));

        BatchEventDetailVO event = new BatchEventDetailVO();
        event.setEventId(905L);
        event.setQuantity(2);
        event.setVersion(2);
        lenient().when(batchService.eventDetail(anyLong())).thenReturn(event);

        RequestCheckVO check = new RequestCheckVO();
        check.setExists(false);
        lenient().when(batchService.checkByRequest(any())).thenReturn(check);

        BatchEntryResultVO entry = new BatchEntryResultVO();
        entry.setBatchId(3058L);
        entry.setPenId(2004L);
        entry.setBatchNo("ZM2610-058");
        entry.setPenStockAfter(240);
        lenient().when(batchService.create(any(), any())).thenReturn(entry);
        lenient().doNothing().when(batchService).delete(anyLong(), anyInt());

        TransferResultVO transfer = new TransferResultVO();
        transfer.setEventId(907L);
        transfer.setQuantity(100);
        BatchPenItem from = new BatchPenItem();
        from.setPenId(2003L);
        from.setPenName("3 号育肥舍");
        from.setStockCount(100);
        BatchPenItem to = new BatchPenItem();
        to.setPenId(2004L);
        to.setPenName("4 号育肥舍");
        to.setStockCount(250);
        transfer.setFromPen(from);
        transfer.setToPen(to);
        lenient().when(batchService.createTransfer(any(), any())).thenReturn(transfer);
        TransferResultVO transferEdit = new TransferResultVO();
        transferEdit.setEventId(907L);
        transferEdit.setQuantity(20);
        lenient().when(batchService.updateTransfer(anyLong(), any())).thenReturn(transferEdit);

        DeathResultVO death = new DeathResultVO();
        death.setEventId(905L);
        death.setQuantity(10);
        death.setCurrentCount(190);
        death.setMessage("已登记死淘 10 头，存栏已更新");
        lenient().when(batchService.createDeath(any(), any())).thenReturn(death);
        DeathResultVO deathEdit = new DeathResultVO();
        deathEdit.setEventId(905L);
        deathEdit.setQuantity(2);
        lenient().when(batchService.updateDeath(anyLong(), any())).thenReturn(deathEdit);
    }
}
