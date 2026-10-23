package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.StockFixtures;
import com.zhimu.server.biz.stock.StockTestMvc;
import com.zhimu.server.biz.stock.request.DrugInboundRequest;
import com.zhimu.server.biz.stock.request.DrugOutboundRequest;
import com.zhimu.server.biz.stock.request.DrugSaveRequest;
import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.request.FeedInboundRequest;
import com.zhimu.server.biz.stock.request.FeedLossRequest;
import com.zhimu.server.biz.stock.request.FeedOtherOutRequest;
import com.zhimu.server.biz.stock.request.FeedSaveRequest;
import com.zhimu.server.biz.stock.request.InboundUpdateRequest;
import com.zhimu.server.biz.stock.request.OutboundUpdateRequest;
import com.zhimu.server.biz.stock.request.StockSettingRequest;
import com.zhimu.server.biz.stock.request.StocktakeConfirmRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.service.StockDrugLotService;
import com.zhimu.server.biz.stock.service.StockDrugService;
import com.zhimu.server.biz.stock.service.StockFeedService;
import com.zhimu.server.biz.stock.service.StockFlowService;
import com.zhimu.server.biz.stock.service.StockInboundService;
import com.zhimu.server.biz.stock.service.StockLossService;
import com.zhimu.server.biz.stock.service.StockOutboundService;
import com.zhimu.server.biz.stock.service.StockRequestService;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.service.StocktakeService;
import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.biz.stock.vo.DrugArchiveItemVO;
import com.zhimu.server.biz.stock.vo.DrugArchiveListResult;
import com.zhimu.server.biz.stock.vo.DrugDetailVO;
import com.zhimu.server.biz.stock.vo.DrugLotDetailVO;
import com.zhimu.server.biz.stock.vo.DrugLotItemVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionsVO;
import com.zhimu.server.biz.stock.vo.DrugOptionListResult;
import com.zhimu.server.biz.stock.vo.DrugOptionVO;
import com.zhimu.server.biz.stock.vo.DrugStockOverviewVO;
import com.zhimu.server.biz.stock.vo.FeedDetailVO;
import com.zhimu.server.biz.stock.vo.FeedOptionVO;
import com.zhimu.server.biz.stock.vo.FeedStockItemVO;
import com.zhimu.server.biz.stock.vo.FeedStockOverviewVO;
import com.zhimu.server.biz.stock.vo.RequestStatusVO;
import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.biz.stock.vo.StockInboundVO;
import com.zhimu.server.biz.stock.vo.StockLossVO;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
import com.zhimu.server.biz.stock.vo.StockSettingVO;
import com.zhimu.server.biz.stock.vo.StocktakeSheetVO;
import com.zhimu.server.biz.stock.vo.StocktakeVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.BatchBriefVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.QtyVO;
import com.zhimu.server.common.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模块09 全部 39 个接口的路径、查询参数、请求体与响应字段（字段取自 App mock
 * module_stock/src/debug/assets/mock/stock/*.json），以及 42201 / 42204 / 42203 / 40902 / 40307 的状态码映射。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockControllerMockMvcTest {

    private static final String JSON = "application/json";
    private static final String IDEM = "Idempotency-Key";

    @Mock private StockFeedService feedService;
    @Mock private StockDrugService drugService;
    @Mock private StockDrugLotService lotService;
    @Mock private StockFlowService flowService;
    @Mock private StockInboundService inboundService;
    @Mock private StockLossService lossService;
    @Mock private StockOutboundService outboundService;
    @Mock private StocktakeService stocktakeService;
    @Mock private StockSettingService settingService;
    @Mock private StockRequestService requestService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = StockTestMvc.builder(new StockFeedController(feedService), new StockDrugController(drugService,
                        settingService, requestService), new StockDrugLotController(lotService),
                new StockFlowController(flowService), new StockInboundController(inboundService),
                new StockLossController(lossService), new StockOutboundController(outboundService),
                new StocktakeController(stocktakeService)).build();
        StockFixtures.useClock();
        StockFixtures.loginOwner();
        stubSuccess();
    }

    @AfterEach
    void tearDown() {
        StockFixtures.resetClock();
        StockFixtures.logout();
    }

    // ================================================================== 饲料

    @Test
    void m01ToM06FeedRoutesMatchAppContract() throws Exception {
        // M01 饲料库存概览
        mvc.perform(get("/api/v1/feed-stock/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.banner.kind").value("DAYS"))
                .andExpect(jsonPath("$.data.banner.preselect_feed_id").value(5101))
                .andExpect(jsonPath("$.data.feed_count").value(2))
                .andExpect(jsonPath("$.data.warn_count").value(1))
                .andExpect(jsonPath("$.data.stock_amount").value(9370))
                .andExpect(jsonPath("$.data.usage_7d_total").value(4410))
                .andExpect(jsonPath("$.data.usage_7d_items[0].feed_id").value(5101))
                .andExpect(jsonPath("$.data.recent_flows[0].flow_id").value(9301))
                .andExpect(jsonPath("$.data.can_other_out").value(true))
                .andExpect(jsonPath("$.data.has_feed").value(true))
                .andExpect(jsonPath("$.data.farm_readonly").value(false));

        // M02 饲料结存列表
        mvc.perform(get("/api/v1/feeds").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].feed_id").value(5101))
                .andExpect(jsonPath("$.data.list[0].name").value("育肥全价料"))
                .andExpect(jsonPath("$.data.list[0].feed_type_code").value("COMPLETE"))
                .andExpect(jsonPath("$.data.list[0].stock.value").value(500))
                .andExpect(jsonPath("$.data.list[0].remaining_days").value(1))
                .andExpect(jsonPath("$.data.list[0].usage_qty").value(3360))
                .andExpect(jsonPath("$.data.list[0].avg_price").value(3.2))
                .andExpect(jsonPath("$.data.list[0].low_stock").value(true))
                .andExpect(jsonPath("$.data.list[0].low_stock_reason_text").value("可用天数不足"))
                .andExpect(jsonPath("$.data.has_more").value(false));

        // M03 饲料下拉（purpose INBOUND / OTHER_OUT）
        mvc.perform(get("/api/v1/feeds/options").param("purpose", "INBOUND").param("keyword", "育肥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].feed_id").value(5101))
                .andExpect(jsonPath("$.data[0].stock.value").value(500))
                .andExpect(jsonPath("$.data[0].remaining_days").value(1))
                .andExpect(jsonPath("$.data[0].selectable").value(true))
                .andExpect(jsonPath("$.data[0].stock_version").value(12));

        // M04 饲料档案详情
        mvc.perform(get("/api/v1/feeds/5101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feed_id").value(5101))
                .andExpect(jsonPath("$.data.feed_type_text").value("全价料"))
                .andExpect(jsonPath("$.data.pack_unit").value("袋"))
                .andExpect(jsonPath("$.data.pack_size").value(40))
                .andExpect(jsonPath("$.data.unit_locked").value(true))
                .andExpect(jsonPath("$.data.edit_version").value(3))
                .andExpect(jsonPath("$.data.updated_by_name").value("王建国"));

        // M05 新增饲料
        mvc.perform(post("/api/v1/feeds").contentType(JSON).content("""
                        {"name":"育肥全价料","feed_type_code":"COMPLETE","spec":"40 千克/袋",
                         "pack_unit":"袋","pack_size":40,"category_id":101,"min_stock":800,"warn_days":7}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feed_id").value(5101));

        // M06 修改饲料档案（带 edit_version）
        mvc.perform(put("/api/v1/feeds/5101").contentType(JSON).content("""
                        {"name":"育肥全价料","feed_type_code":"COMPLETE","edit_version":3}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edit_version").value(3));
    }

    // ================================================================== 入库

    @Test
    void m08ToM12InboundAndPartnerRoutesMatchAppContract() throws Exception {
        mvc.perform(post("/api/v1/feed-inbounds").header(IDEM, "req-6201").contentType(JSON).content("""
                        {"feed_id":5101,"input_qty":2000,"input_unit":"MEASURE","unit_price":3.20,
                         "production_date":"2026-10-08","shelf_life_date":"2027-01-08",
                         "supplier_name":"松山区丰禾饲料经销部","inbound_date":"2026-10-12","sync_finance":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inbound_id").value(6201))
                .andExpect(jsonPath("$.data.inbound_no").value("RK26106201"))
                .andExpect(jsonPath("$.data.material_type").value("FEED"))
                .andExpect(jsonPath("$.data.qty.value").value(2000))
                .andExpect(jsonPath("$.data.amount").value(6400))
                .andExpect(jsonPath("$.data.sync_finance").value(true))
                .andExpect(jsonPath("$.data.status_text").value("正常"))
                .andExpect(jsonPath("$.data.has_outbound").value(false));

        mvc.perform(get("/api/v1/stock-inbounds/6201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inbound_id").value(6201))
                .andExpect(jsonPath("$.data.actions.can_edit").value(true));

        mvc.perform(put("/api/v1/stock-inbounds/6211").contentType(JSON).content("""
                        {"input_qty":60,"input_unit":"PACK","unit_price":24.00,
                         "inbound_date":"2026-10-16","expire_date":"2027-03-15","version":2}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/v1/stock-inbounds/6201/void").contentType(JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/stock-partners/recent").param("kind", "SUPPLIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("松山区丰禾饲料经销部"));
    }

    // ================================================================== 流水

    @Test
    void m13AndM28FlowRoutesMatchAppContract() throws Exception {
        mvc.perform(get("/api/v1/feed-flows").param("feed_id", "5101").param("flow_group", "ALL")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].flow_id").value(9301))
                .andExpect(jsonPath("$.data.list[0].flow_type").value("FEED_USE"))
                .andExpect(jsonPath("$.data.list[0].flow_type_text").value("饲喂领用"))
                .andExpect(jsonPath("$.data.list[0].direction").value("OUT"))
                .andExpect(jsonPath("$.data.list[0].qty.value").value(158))
                .andExpect(jsonPath("$.data.list[0].stock_qty_after").value(500))
                .andExpect(jsonPath("$.data.list[0].biz_type").value("FEED_RECORD"))
                .andExpect(jsonPath("$.data.list[0].biz_id").value(7106))
                .andExpect(jsonPath("$.data.list[0].unit_price").value(3.2))
                .andExpect(jsonPath("$.data.list[0].amount").value(505.6));

        mvc.perform(get("/api/v1/drug-flows").param("drug_lot_id", "5201").param("flow_group", "ALL")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].flow_id").value(9500))
                .andExpect(jsonPath("$.data.list[0].material_type").value("DRUG"))
                .andExpect(jsonPath("$.data.list[0].lot_no").value("2025047"))
                .andExpect(jsonPath("$.data.list[0].biz_type").value("INBOUND"));
    }

    // ================================================================== 盘点

    @Test
    void m14ToM17StocktakeRoutesMatchAppContract() throws Exception {
        mvc.perform(get("/api/v1/stocktakes/sheet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocktake_date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.operator.user_id").value(10001))
                .andExpect(jsonPath("$.data.items[0].feed_id").value(5101))
                .andExpect(jsonPath("$.data.items[0].book_qty").value(500))
                .andExpect(jsonPath("$.data.items[0].stock_version").value(12))
                .andExpect(jsonPath("$.data.items[0].low_stock").value(true))
                .andExpect(jsonPath("$.data.items[0].avg_price").value(3.2));

        mvc.perform(post("/api/v1/stocktakes").header(IDEM, "req-pd").contentType(JSON).content("""
                        {"items":[{"feed_id":5101,"book_qty":500,"stock_version":12,"actual_qty":488},
                                  {"feed_id":5102,"book_qty":1850,"stock_version":8,"actual_qty":1856}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocktake_id").value(6301))
                .andExpect(jsonPath("$.data.item_count").value(2))
                .andExpect(jsonPath("$.data.gain_count").value(1))
                .andExpect(jsonPath("$.data.loss_count").value(1))
                .andExpect(jsonPath("$.data.gain_amount").value(25.2))
                .andExpect(jsonPath("$.data.loss_amount").value(38.4))
                .andExpect(jsonPath("$.data.items[0].result").value("LOSS"))
                .andExpect(jsonPath("$.data.items[0].result_text").value("盘亏"))
                .andExpect(jsonPath("$.data.items[0].diff_amount").value(-38.4));

        mvc.perform(get("/api/v1/stocktakes").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].stocktake_id").value(6301));

        mvc.perform(get("/api/v1/stocktakes/6301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocktake_no").value("PD2610010001"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    // ================================================================== 报损 / 报废

    @Test
    void m18M19M31LossAndScrapRoutesMatchAppContract() throws Exception {
        mvc.perform(post("/api/v1/feed-losses").header(IDEM, "req-loss").contentType(JSON).content("""
                        {"feed_id":5101,"input_qty":10,"input_unit":"MEASURE","reason":"MILDEW",
                         "remark":"3 号育肥舍料塔底部结块","stock_version":12}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loss_id").value(6401))
                .andExpect(jsonPath("$.data.loss_type").value("FEED_LOSS"))
                .andExpect(jsonPath("$.data.loss_type_text").value("报损"))
                .andExpect(jsonPath("$.data.qty.value").value(10))
                .andExpect(jsonPath("$.data.reason_text").value("霉变"))
                .andExpect(jsonPath("$.data.remaining.value").value(500))
                .andExpect(jsonPath("$.data.loss_date").value("2026-10-15"))
                .andExpect(jsonPath("$.data.amount").value(32));

        mvc.perform(get("/api/v1/stock-losses/6401"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loss_id").value(6401))
                .andExpect(jsonPath("$.data.actions.can_request_change").value(true));

        mvc.perform(post("/api/v1/drug-scraps").header(IDEM, "req-scrap").contentType(JSON).content("""
                        {"drug_lot_id":6301,"input_qty":5,"input_unit":"PACK","reason":"DAMAGED",
                         "remark":"外包装受潮","stock_version":5}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loss_type").value("DRUG_SCRAP"))
                .andExpect(jsonPath("$.data.lot_no").value("2025033"));
    }

    // ================================================================== 出库

    @Test
    void m20ToM23AndM32M33OutboundRoutesMatchAppContract() throws Exception {
        mvc.perform(post("/api/v1/feed-other-outbounds").header(IDEM, "req-6501").contentType(JSON).content("""
                        {"feed_id":5102,"input_qty":50,"input_unit":"MEASURE","destination":"RETURN_SUPPLIER",
                         "remark":"包装破损，退回经销部","out_date":"2026-10-16","stock_version":8}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbound_id").value(6501))
                .andExpect(jsonPath("$.data.out_type").value("OTHER_OUT"))
                .andExpect(jsonPath("$.data.out_type_text").value("其他出库"))
                .andExpect(jsonPath("$.data.destination").value("RETURN_SUPPLIER"))
                .andExpect(jsonPath("$.data.destination_text").value("退回供应商"))
                .andExpect(jsonPath("$.data.qty.value").value(50))
                .andExpect(jsonPath("$.data.max_qty").value(1900))
                .andExpect(jsonPath("$.data.amount").value(210));

        mvc.perform(get("/api/v1/stock-outbounds/6511"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbound_id").value(6511))
                .andExpect(jsonPath("$.data.pen.pen_id").value(2004))
                .andExpect(jsonPath("$.data.receiver.user_id").value(10004))
                .andExpect(jsonPath("$.data.allocations[0].batch_id").value(3002));

        mvc.perform(put("/api/v1/stock-outbounds/6512").contentType(JSON).content("""
                        {"input_qty":3,"input_unit":"PACK","return_to":"松山区康牧兽药店",
                         "return_reason":"PACKAGE_DAMAGED","out_date":"2026-10-19","version":1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbound_id").value(6512));

        mvc.perform(post("/api/v1/stock-outbounds/6512/void").contentType(JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(post("/api/v1/drug-outbounds").header(IDEM, "req-6511").contentType(JSON).content("""
                        {"out_type":"USE","drug_id":5301,"drug_lot_id":6301,"input_qty":2,
                         "input_unit":"PACK","target_type":"PEN","pen_id":2004,"receiver_id":10004,
                         "remark":"伤口处理和器具消毒","out_date":"2026-10-19","stock_version":5}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbound_id").value(6511))
                .andExpect(jsonPath("$.data.lot_no").value("2025033"))
                .andExpect(jsonPath("$.data.target_type").value("PEN"));

        mvc.perform(get("/api/v1/drug-outbounds/allocation-preview")
                        .param("drug_lot_id", "6301").param("input_qty", "2").param("input_unit", "PACK")
                        .param("target_type", "BATCH").param("batch_id", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_amount").value(18))
                .andExpect(jsonPath("$.data.unit_price").value(9))
                .andExpect(jsonPath("$.data.rows[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data.rows[0].head_count").value(200))
                .andExpect(jsonPath("$.data.rows[0].ratio").value(1))
                .andExpect(jsonPath("$.data.rows[0].amount").value(18));
    }

    // ================================================================== 兽药疫苗

    @Test
    void m24ToM30DrugRoutesMatchAppContract() throws Exception {
        mvc.perform(get("/api/v1/drug-stock/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lot_counts.all").value(7))
                .andExpect(jsonPath("$.data.lot_counts.vet_drug").value(3))
                .andExpect(jsonPath("$.data.lot_counts.vaccine").value(3))
                .andExpect(jsonPath("$.data.lot_counts.disinfectant").value(1))
                .andExpect(jsonPath("$.data.near_days.vet").value(30))
                .andExpect(jsonPath("$.data.low_stock_banner.drugs[0].drug_id").value(5201))
                .andExpect(jsonPath("$.data.low_stock_banner.drugs[0].usable.value").value(6000))
                .andExpect(jsonPath("$.data.low_stock_banner.preselect_drug_id").value(5201))
                .andExpect(jsonPath("$.data.attention_count").value(3))
                .andExpect(jsonPath("$.data.normal_count").value(4))
                .andExpect(jsonPath("$.data.has_any_lot").value(true))
                .andExpect(jsonPath("$.data.farm_readonly").value(false));

        mvc.perform(get("/api/v1/drug-lots").param("group", "ATTENTION").param("category", "VACCINE")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].lot_id").value(5201))
                .andExpect(jsonPath("$.data.list[0].drug_name").value("口蹄疫灭活疫苗"))
                .andExpect(jsonPath("$.data.list[0].category_text").value("疫苗"))
                .andExpect(jsonPath("$.data.list[0].stock.value").value(300))
                .andExpect(jsonPath("$.data.list[0].stock.pack_count").value(30))
                .andExpect(jsonPath("$.data.list[0].lot_status").value("RECALLED"))
                .andExpect(jsonPath("$.data.list[0].lot_status_text").value("已停用"))
                .andExpect(jsonPath("$.data.list[0].days_to_expire").value(183))
                .andExpect(jsonPath("$.data.list[0].priority_out").value(false))
                .andExpect(jsonPath("$.data.list[0].status_note").value("平台停用，只能报废"))
                .andExpect(jsonPath("$.data.tab_counts.ATTENTION").value(3))
                .andExpect(jsonPath("$.data.tab_counts.NORMAL").value(4));

        mvc.perform(get("/api/v1/drug-lots/5201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock_amount").value(840))
                .andExpect(jsonPath("$.data.avg_price").value(2.8))
                .andExpect(jsonPath("$.data.first_inbound.inbound_id").value(6210))
                .andExpect(jsonPath("$.data.inbound_count").value(1))
                .andExpect(jsonPath("$.data.has_outbound").value(true))
                .andExpect(jsonPath("$.data.drug_banned").value(false))
                .andExpect(jsonPath("$.data.recalled_at").value("2026-10-18 16:20:00"))
                .andExpect(jsonPath("$.data.recall_reason").value("生产企业召回，抽检效价不合格"))
                .andExpect(jsonPath("$.data.stock_version").value(5));

        mvc.perform(get("/api/v1/drug-lots/options").param("drug_id", "5301").param("purpose", "USE")
                        .param("need_qty", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectable[0].lot_id").value(6301))
                .andExpect(jsonPath("$.data.selectable[0].lot_status").value("NEAR_EXPIRY"))
                .andExpect(jsonPath("$.data.selectable[0].stock.value").value(40))
                .andExpect(jsonPath("$.data.selectable[0].priority_out").value(true))
                .andExpect(jsonPath("$.data.selectable[0].insufficient").value(false))
                .andExpect(jsonPath("$.data.selectable[0].can_go_scrap").value(false))
                .andExpect(jsonPath("$.data.selectable[0].stock_version").value(5))
                .andExpect(jsonPath("$.data.unselectable[0].lot_id").value(5201))
                .andExpect(jsonPath("$.data.default_lot_id").value(6301));

        mvc.perform(post("/api/v1/drug-inbounds").header(IDEM, "req-6211").contentType(JSON).content("""
                        {"drug_id":5001,"lot_no":"2025113","inbound_date":"2026-10-16",
                         "expire_date":"2027-03-15","input_qty":60,"input_unit":"PACK","unit_price":24.00}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inbound_id").value(6211))
                .andExpect(jsonPath("$.data.lot_no").value("2025113"))
                .andExpect(jsonPath("$.data.category").value("VACCINE"));
    }

    // ================================================================== 药品档案

    @Test
    void m34ToM38DrugArchiveRoutesMatchAppContract() throws Exception {
        mvc.perform(get("/api/v1/drugs").param("keyword", "阿莫").param("category", "VET_DRUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.list[0].drug_id").value(5201))
                .andExpect(jsonPath("$.data.list[0].catalog_id").value(7201))
                .andExpect(jsonPath("$.data.list[0].category").value("VET_DRUG"))
                .andExpect(jsonPath("$.data.list[0].usable.value").value(6000))
                .andExpect(jsonPath("$.data.list[0].min_stock").value(7500))
                .andExpect(jsonPath("$.data.list[0].low_stock").value(true))
                .andExpect(jsonPath("$.data.list[0].has_stock").value(true))
                .andExpect(jsonPath("$.data.list[0].banned").value(false));

        mvc.perform(get("/api/v1/drugs/options").param("purpose", "OUTBOUND").param("keyword", "克仑特罗"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].drug_id").value(5201))
                .andExpect(jsonPath("$.data.list[0].usable.value").value(6000))
                .andExpect(jsonPath("$.data.banned_hint").value("克仑特罗为禁用药，不能用于食品动物"));

        mvc.perform(get("/api/v1/drugs/5201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drug_id").value(5201))
                .andExpect(jsonPath("$.data.catalog_source_text").value("兽药目录"))
                .andExpect(jsonPath("$.data.pack_unit_options[0]").value("袋"))
                .andExpect(jsonPath("$.data.pack_size_locked").value(false))
                .andExpect(jsonPath("$.data.near_days").value(30))
                .andExpect(jsonPath("$.data.banned").value(false));

        mvc.perform(post("/api/v1/drugs").contentType(JSON).content("""
                        {"category":"VET_DRUG","catalog_id":7201,"spec":"10% · 500 克/袋","unit":"G",
                         "pack_unit":"袋","pack_size":500,"min_stock":7500,"min_stock_unit":"PACK"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drug_id").value(5204));

        mvc.perform(put("/api/v1/drugs/5202").contentType(JSON).content("""
                        {"category":"VET_DRUG","catalog_id":7202,"spec":"100 克/袋","unit":"G",
                         "pack_unit":"袋","pack_size":100,"version":1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drug_id").value(5202));
    }

    // ================================================================== 设置与提交结果

    @Test
    void m40ToM42SettingAndRequestRoutesMatchAppContract() throws Exception {
        mvc.perform(get("/api/v1/stock-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.near_days_vet").value(30))
                .andExpect(jsonPath("$.data.near_days_vaccine").value(30))
                .andExpect(jsonPath("$.data.near_days_disinfectant").value(30))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(put("/api/v1/stock-settings").contentType(JSON).content("""
                        {"near_days_vet":30,"near_days_vaccine":45,"near_days_disinfectant":15,"version":1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(get("/api/v1/stock-requests/req-6201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.biz_type").value("INBOUND"))
                .andExpect(jsonPath("$.data.biz_id").value(6201));
    }

    // ================================================================== 错误码

    @Test
    void businessErrorsMapToHttpStatusAndCode() throws Exception {
        when(lossService.createFeedLoss(any(), any())).thenThrow(new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                "超过当前结存 500 千克，出库后库存不能为负数"));
        mvc.perform(post("/api/v1/feed-losses").header(IDEM, "req-x").contentType(JSON).content("""
                        {"feed_id":5101,"input_qty":600,"input_unit":"MEASURE","reason":"MILDEW","stock_version":12}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.message").value("超过当前结存 500 千克，出库后库存不能为负数"));

        when(inboundService.createDrugInbound(any(), any())).thenThrow(new BizException(ErrorCode.LOT_UNAVAILABLE,
                "批号 2025047 已被平台停用，不能入库"));
        mvc.perform(post("/api/v1/drug-inbounds").header(IDEM, "req-y").contentType(JSON).content("""
                        {"drug_id":5003,"lot_no":"2025047","inbound_date":"2026-10-18",
                         "expire_date":"2027-04-20","input_qty":1,"input_unit":"PACK","unit_price":28}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42204));

        when(outboundService.createDrugOutbound(any(), any())).thenThrow(new BizException(ErrorCode.BANNED_DRUG,
                "该药品属于禁用兽药，不能使用"));
        mvc.perform(post("/api/v1/drug-outbounds").header(IDEM, "req-z").contentType(JSON).content("""
                        {"out_type":"USE","drug_id":5301,"drug_lot_id":6301,"input_qty":2,
                         "input_unit":"PACK","out_date":"2026-10-19","stock_version":5}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));

        when(stocktakeService.confirm(any(), any())).thenThrow(new BizException(ErrorCode.VERSION_CONFLICT,
                "账面结存已变化，请刷新后重新核对", Map.of("items", List.of())));
        mvc.perform(post("/api/v1/stocktakes").header(IDEM, "req-w").contentType(JSON).content("""
                        {"items":[{"feed_id":5101,"book_qty":500,"stock_version":12,"actual_qty":999}]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));

        when(inboundService.update(anyLong(), any())).thenThrow(new BizException(
                ErrorCode.CHANGE_REQUEST_REQUIRED, "已发生出库，需要提交修改申请"));
        mvc.perform(put("/api/v1/stock-inbounds/6201").contentType(JSON).content("""
                        {"input_qty":10,"unit_price":3.2,"inbound_date":"2026-10-12","version":1}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40307));

        // 40001 字段校验（@Valid）走统一错误结构
        mvc.perform(post("/api/v1/feed-losses").header(IDEM, "req-v").contentType(JSON)
                        .content("{\"input_qty\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ================================================================== 桩数据

    private void stubSuccess() {
        QtyVO feedQty = QtyVO.builder().value(new BigDecimal("500")).unit("KG").unitText("千克").build();
        QtyVO drugQty = QtyVO.builder().value(new BigDecimal("300")).unit("DOSE").unitText("头份")
                .packUnit("瓶").packSize(new BigDecimal("10")).packCount(30)
                .restValue(BigDecimal.ZERO).build();

        FeedStockOverviewVO overview = new FeedStockOverviewVO();
        FeedStockOverviewVO.Banner banner = new FeedStockOverviewVO.Banner();
        banner.setKind("DAYS");
        banner.setFeedId(5101L);
        banner.setFeedName("育肥全价料");
        banner.setRemainingDays(1);
        banner.setWarnDays(7);
        banner.setStock(QtyVO.builder().value(new BigDecimal("500")).unit("KG").unitText("千克").build());
        banner.setMinStock(new BigDecimal("800"));
        banner.setPreselectFeedId(5101L);
        overview.setBanner(banner);
        overview.setFeedCount(2);
        overview.setWarnCount(1);
        overview.setStockAmount(new BigDecimal("9370"));
        overview.setUsage7dTotal(new BigDecimal("4410"));
        FeedStockOverviewVO.UsageItem usage = new FeedStockOverviewVO.UsageItem();
        usage.setFeedId(5101L);
        usage.setFeedName("育肥全价料");
        usage.setQty(new BigDecimal("3360"));
        overview.setUsage7dItems(List.of(usage));
        overview.setRecentFlows(List.of(flowItem()));
        overview.setCanOtherOut(true);
        overview.setHasFeed(true);
        lenient().when(feedService.overview()).thenReturn(overview);

        FeedStockItemVO item = new FeedStockItemVO();
        item.setFeedId(5101L);
        item.setName("育肥全价料");
        item.setSpec("40 千克/袋");
        item.setFeedTypeCode("COMPLETE");
        item.setFeedTypeText("全价料");
        item.setCategoryText("育肥猪");
        item.setStock(feedQty);
        item.setRemainingDays(1);
        item.setRemainingBasis("USAGE_7D");
        item.setDailyUseQty(new BigDecimal("480"));
        item.setUsageQty(new BigDecimal("3360"));
        item.setUsageDays(7);
        item.setAvgPrice(new BigDecimal("3.2"));
        item.setStockAmount(new BigDecimal("1600"));
        item.setMinStock(new BigDecimal("800"));
        item.setWarnDays(7);
        item.setLowStock(true);
        item.setLowStockReason("BELOW_DAYS");
        item.setLowStockReasonText("可用天数不足");
        lenient().when(feedService.page(anyInt(), anyInt()))
                .thenReturn(PageVO.of(List.of(item), 2, 1, 20));

        FeedOptionVO feedOption = new FeedOptionVO();
        feedOption.setFeedId(5101L);
        feedOption.setName("育肥全价料");
        feedOption.setStock(feedQty);
        feedOption.setRemainingDays(1);
        feedOption.setLowStock(true);
        feedOption.setSelectable(true);
        feedOption.setAvgPrice(new BigDecimal("3.2"));
        feedOption.setStockVersion(12);
        lenient().when(feedService.options(any(), any())).thenReturn(List.of(feedOption));

        FeedDetailVO feedDetail = new FeedDetailVO();
        feedDetail.setFeedId(5101L);
        feedDetail.setName("育肥全价料");
        feedDetail.setFeedTypeCode("COMPLETE");
        feedDetail.setFeedTypeText("全价料");
        feedDetail.setSpec("40 千克/袋");
        feedDetail.setUnit("KG");
        feedDetail.setUnitText("千克");
        feedDetail.setPackUnit("袋");
        feedDetail.setPackSize(new BigDecimal("40"));
        feedDetail.setCategoryId(101L);
        feedDetail.setCategoryText("育肥猪");
        feedDetail.setMinStock(new BigDecimal("800"));
        feedDetail.setWarnDays(7);
        feedDetail.setUnitLocked(true);
        feedDetail.setEditVersion(3);
        feedDetail.setUpdatedAt("2026-10-10 09:15:00");
        feedDetail.setUpdatedByName("王建国");
        lenient().when(feedService.detail(anyLong())).thenReturn(feedDetail);
        lenient().when(feedService.create(any())).thenReturn(feedDetail);
        lenient().when(feedService.update(anyLong(), any())).thenReturn(feedDetail);

        StockInboundVO inbound = new StockInboundVO();
        inbound.setInboundId(6201L);
        inbound.setInboundNo("RK26106201");
        inbound.setMaterialType("FEED");
        inbound.setMaterialName("育肥全价料");
        inbound.setFeedId(5101L);
        inbound.setInputQty(new BigDecimal("2000"));
        inbound.setInputUnit("MEASURE");
        inbound.setQty(QtyVO.builder().value(new BigDecimal("2000")).unit("KG").unitText("千克").build());
        inbound.setUnitPrice(new BigDecimal("3.2"));
        inbound.setAmount(new BigDecimal("6400"));
        inbound.setSupplierName("松山区丰禾饲料经销部");
        inbound.setProductionDate("2026-10-08");
        inbound.setShelfLifeDate("2027-01-08");
        inbound.setInboundDate("2026-10-12");
        inbound.setSyncFinance(true);
        inbound.setStatus("NORMAL");
        inbound.setStatusText("正常");
        inbound.setHasOutbound(false);
        inbound.setActions(actions(true));
        inbound.setCreatedBy(user());
        inbound.setCreatedAt("2026-10-12 09:00:00");
        inbound.setVersion(1);
        lenient().when(inboundService.createFeedInbound(any(), any())).thenReturn(inbound);
        lenient().when(inboundService.get(anyLong())).thenReturn(inbound);
        StockInboundVO updatedInbound = new StockInboundVO();
        updatedInbound.setInboundId(6211L);
        updatedInbound.setMaterialType("DRUG");
        updatedInbound.setLotNo("2025113");
        updatedInbound.setVersion(2);
        lenient().when(inboundService.update(eq(6211L), any())).thenReturn(updatedInbound);

        StockInboundVO drugInbound = new StockInboundVO();
        drugInbound.setInboundId(6211L);
        drugInbound.setMaterialType("DRUG");
        drugInbound.setDrugId(5001L);
        drugInbound.setDrugLotId(6002L);
        drugInbound.setLotNo("2025113");
        drugInbound.setCategory("VACCINE");
        drugInbound.setCategoryText("疫苗");
        drugInbound.setQty(drugQty);
        drugInbound.setVersion(2);
        lenient().when(inboundService.createDrugInbound(any(), any())).thenReturn(drugInbound);

        lenient().when(outboundService.recentPartners(any(), any()))
                .thenReturn(List.of("松山区丰禾饲料经销部"));

        PageVO<StockFlowItemVO> flows = PageVO.of(List.of(flowItem()), 1, 1, 20);
        lenient().when(flowService.feedFlows(any(), any(), anyInt(), anyInt())).thenReturn(flows);
        PageVO<StockFlowItemVO> drugFlows = PageVO.of(List.of(drugFlow()), 1, 1, 20);
        lenient().when(flowService.drugFlows(any(), any(), any(), anyInt(), anyInt())).thenReturn(drugFlows);

        StocktakeSheetVO sheet = new StocktakeSheetVO();
        sheet.setStocktakeDate("2026-10-19");
        sheet.setOperator(user());
        StocktakeSheetVO.Item sheetItem = new StocktakeSheetVO.Item();
        sheetItem.setFeedId(5101L);
        sheetItem.setFeedName("育肥全价料");
        sheetItem.setUnitText("千克");
        sheetItem.setBookQty(new BigDecimal("500"));
        sheetItem.setStockVersion(12);
        sheetItem.setLowStock(true);
        sheetItem.setAvgPrice(new BigDecimal("3.2"));
        sheet.setItems(List.of(sheetItem));
        lenient().when(stocktakeService.sheet()).thenReturn(sheet);

        StocktakeVO stocktake = new StocktakeVO();
        stocktake.setStocktakeId(6301L);
        stocktake.setStocktakeNo("PD2610010001");
        stocktake.setStocktakeDate("2026-10-01");
        stocktake.setOperator(user());
        stocktake.setItemCount(2);
        stocktake.setGainCount(1);
        stocktake.setLossCount(1);
        stocktake.setGainAmount(new BigDecimal("25.20"));
        stocktake.setLossAmount(new BigDecimal("38.40"));
        stocktake.setVersion(1);
        stocktake.setActions(actions(false));
        StocktakeVO.Item loss = new StocktakeVO.Item();
        loss.setItemId(1L);
        loss.setFeedId(5101L);
        loss.setFeedName("育肥全价料");
        loss.setUnitText("千克");
        loss.setBookQty(new BigDecimal("500"));
        loss.setActualQty(new BigDecimal("488"));
        loss.setDiffQty(new BigDecimal("-12"));
        loss.setResult("LOSS");
        loss.setResultText("盘亏");
        loss.setAvgPrice(new BigDecimal("3.2"));
        loss.setDiffAmount(new BigDecimal("-38.40"));
        stocktake.setItems(List.of(loss));
        lenient().when(stocktakeService.confirm(any(), any())).thenReturn(stocktake);
        lenient().when(stocktakeService.get(anyLong())).thenReturn(stocktake);
        lenient().when(stocktakeService.page(anyInt(), anyInt()))
                .thenReturn(PageVO.of(List.of(stocktake), 2, 1, 20));

        StockLossVO lossVo = new StockLossVO();
        lossVo.setLossId(6401L);
        lossVo.setLossType("FEED_LOSS");
        lossVo.setLossTypeText("报损");
        lossVo.setMaterialName("育肥全价料");
        lossVo.setQty(QtyVO.builder().value(new BigDecimal("10")).unit("KG").unitText("千克").build());
        lossVo.setRemaining(feedQty);
        lossVo.setReason("MILDEW");
        lossVo.setReasonText("霉变");
        lossVo.setRemark("3 号育肥舍料塔底部结块");
        lossVo.setLossDate("2026-10-15");
        lossVo.setStatus("NORMAL");
        lossVo.setUnitPrice(new BigDecimal("3.2"));
        lossVo.setAmount(new BigDecimal("32"));
        lossVo.setActions(actions(false));
        lossVo.setCreatedBy(user());
        lossVo.setCreatedAt("2026-10-15 16:30:00");
        lossVo.setVersion(1);
        lenient().when(lossService.createFeedLoss(any(), any())).thenReturn(lossVo);
        lenient().when(lossService.get(anyLong())).thenReturn(lossVo);
        StockLossVO scrap = new StockLossVO();
        scrap.setLossId(6402L);
        scrap.setLossType("DRUG_SCRAP");
        scrap.setLossTypeText("报废");
        scrap.setLotNo("2025033");
        lenient().when(lossService.createDrugScrap(any(), any())).thenReturn(scrap);

        StockOutboundVO outbound = new StockOutboundVO();
        outbound.setOutboundId(6501L);
        outbound.setOutboundNo("CK26106501");
        outbound.setOutType("OTHER_OUT");
        outbound.setOutTypeText("其他出库");
        outbound.setMaterialType("FEED");
        outbound.setMaterialName("保育料");
        outbound.setFeedId(5102L);
        outbound.setInputQty(new BigDecimal("50"));
        outbound.setInputUnit("MEASURE");
        outbound.setQty(QtyVO.builder().value(new BigDecimal("50")).unit("KG").unitText("千克").build());
        outbound.setMaxQty(new BigDecimal("1900"));
        outbound.setDestination("RETURN_SUPPLIER");
        outbound.setDestinationText("退回供应商");
        outbound.setRemark("包装破损，退回经销部");
        outbound.setOutDate("2026-10-16");
        outbound.setStatus("NORMAL");
        outbound.setStatusText("正常");
        outbound.setUnitPrice(new BigDecimal("4.2"));
        outbound.setAmount(new BigDecimal("210"));
        outbound.setActions(actions(false));
        outbound.setCreatedBy(user());
        outbound.setCreatedAt("2026-10-16 10:05:00");
        outbound.setVersion(1);
        lenient().when(outboundService.createFeedOtherOut(any(), any())).thenReturn(outbound);
        lenient().when(outboundService.get(anyLong())).thenReturn(outbound);
        lenient().when(outboundService.update(anyLong(), any())).thenReturn(outbound);

        StockOutboundVO drugOutbound = new StockOutboundVO();
        drugOutbound.setOutboundId(6511L);
        drugOutbound.setOutType("USE");
        drugOutbound.setOutTypeText("领用");
        drugOutbound.setMaterialType("DRUG");
        drugOutbound.setLotNo("2025033");
        drugOutbound.setTargetType("PEN");
        drugOutbound.setPen(PenBriefVO.builder().penId(2004L).name("4 号育肥舍").build());
        drugOutbound.setReceiver(user());
        CostAllocationVO.Row allocation = new CostAllocationVO.Row();
        allocation.setBatchId(3002L);
        allocation.setBatchNo("ZM2610-019");
        allocation.setHeadCount(150);
        allocation.setRatio(BigDecimal.ONE);
        allocation.setAmount(new BigDecimal("90"));
        drugOutbound.setAllocations(List.of(allocation));
        lenient().when(outboundService.createDrugOutbound(any(), any())).thenReturn(drugOutbound);
        StockOutboundVO useOutbound = new StockOutboundVO();
        useOutbound.setOutboundId(6511L);
        useOutbound.setPen(PenBriefVO.builder().penId(2004L).name("4 号育肥舍").build());
        useOutbound.setReceiver(UserBriefVO.builder().userId(10004L).name("张伟").role("STAFF")
                .roleText("员工").build());
        useOutbound.setBatch(BatchBriefVO.builder().batchId(3002L).batchNo("ZM2610-019").build());
        useOutbound.setAllocations(List.of(allocation));
        lenient().when(outboundService.get(eq(6511L))).thenReturn(useOutbound);
        StockOutboundVO returnOutbound = new StockOutboundVO();
        returnOutbound.setOutboundId(6512L);
        returnOutbound.setOutType("RETURN");
        returnOutbound.setReturnTo("松山区康牧兽药店");
        returnOutbound.setReturnReason("PACKAGE_DAMAGED");
        returnOutbound.setVersion(1);
        lenient().when(outboundService.get(eq(6512L))).thenReturn(returnOutbound);
        lenient().when(outboundService.update(eq(6512L), any())).thenReturn(returnOutbound);

        CostAllocationVO allocationVo = new CostAllocationVO();
        allocationVo.setTotalAmount(new BigDecimal("18"));
        allocationVo.setUnitPrice(new BigDecimal("9"));
        CostAllocationVO.Row previewRow = new CostAllocationVO.Row();
        previewRow.setBatchId(3001L);
        previewRow.setBatchNo("ZM2610-031");
        previewRow.setHeadCount(200);
        previewRow.setRatio(BigDecimal.ONE);
        previewRow.setAmount(new BigDecimal("18"));
        allocationVo.setRows(List.of(previewRow));
        lenient().when(outboundService.allocationPreview(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(allocationVo);

        DrugStockOverviewVO drugOverview = new DrugStockOverviewVO();
        DrugStockOverviewVO.LotCounts counts = new DrugStockOverviewVO.LotCounts();
        counts.setAll(7);
        counts.setVetDrug(3);
        counts.setVaccine(3);
        counts.setDisinfectant(1);
        drugOverview.setLotCounts(counts);
        DrugStockOverviewVO.NearDays nearDays = new DrugStockOverviewVO.NearDays();
        nearDays.setVet(30);
        nearDays.setVaccine(30);
        nearDays.setDisinfectant(30);
        drugOverview.setNearDays(nearDays);
        DrugStockOverviewVO.LowStockBanner banner2 = new DrugStockOverviewVO.LowStockBanner();
        DrugStockOverviewVO.LowDrug lowDrug = new DrugStockOverviewVO.LowDrug();
        lowDrug.setDrugId(5201L);
        lowDrug.setName("阿莫西林可溶性粉");
        lowDrug.setUsable(QtyVO.builder().value(new BigDecimal("6000")).unit("G").unitText("克")
                .packUnit("袋").packSize(new BigDecimal("500")).packCount(12).restValue(BigDecimal.ZERO).build());
        lowDrug.setMinStock(new BigDecimal("15"));
        banner2.setDrugs(List.of(lowDrug));
        banner2.setPreselectDrugId(5201L);
        drugOverview.setLowStockBanner(banner2);
        drugOverview.setAttentionCount(3);
        drugOverview.setNormalCount(4);
        drugOverview.setHasAnyLot(true);
        lenient().when(drugService.overview()).thenReturn(drugOverview);

        DrugLotItemVO lotItem = new DrugLotItemVO();
        lotItem.setLotId(5201L);
        lotItem.setDrugId(5003L);
        lotItem.setDrugName("口蹄疫灭活疫苗");
        lotItem.setCategory("VACCINE");
        lotItem.setCategoryText("疫苗");
        lotItem.setLotNo("2025047");
        lotItem.setExpireDate("2027-04-20");
        lotItem.setStock(drugQty);
        lotItem.setLotStatus("RECALLED");
        lotItem.setLotStatusText("已停用");
        lotItem.setDaysToExpire(183);
        lotItem.setPriorityOut(false);
        lotItem.setStatusNote("平台停用，只能报废");
        PageVO<DrugLotItemVO> lotPage = PageVO.of(List.of(lotItem), 3, 1, 20);
        lotPage.setTabCounts(Map.of("ATTENTION", 3, "NORMAL", 4));
        lenient().when(lotService.page(any(), any(), any(), anyInt(), anyInt())).thenReturn(lotPage);

        DrugLotDetailVO lotDetail = new DrugLotDetailVO();
        lotDetail.setLotId(5201L);
        lotDetail.setStockAmount(new BigDecimal("840"));
        lotDetail.setAvgPrice(new BigDecimal("2.8"));
        lotDetail.setPackPrice(new BigDecimal("28"));
        DrugLotDetailVO.FirstInbound firstInbound = new DrugLotDetailVO.FirstInbound();
        firstInbound.setInboundId(6210L);
        firstInbound.setInboundDate("2026-10-08");
        firstInbound.setQty(QtyVO.builder().value(new BigDecimal("400")).unit("DOSE").unitText("头份").build());
        firstInbound.setUnitPrice(new BigDecimal("28"));
        lotDetail.setFirstInbound(firstInbound);
        lotDetail.setInboundCount(1);
        lotDetail.setHasOutbound(true);
        lotDetail.setDrugBanned(false);
        lotDetail.setActions(actions(false));
        lotDetail.setRecalledAt("2026-10-18 16:20:00");
        lotDetail.setRecallReason("生产企业召回，抽检效价不合格");
        lotDetail.setStockVersion(5);
        lenient().when(lotService.detail(anyLong())).thenReturn(lotDetail);

        DrugLotOptionVO selectable = new DrugLotOptionVO();
        selectable.setLotId(6301L);
        selectable.setLotNo("2025033");
        selectable.setExpireDate("2026-11-08");
        selectable.setDaysToExpire(20);
        selectable.setLotStatus("NEAR_EXPIRY");
        selectable.setLotStatusText("临期");
        selectable.setStock(QtyVO.builder().value(new BigDecimal("40")).unit("L").unitText("升")
                .packUnit("桶").packSize(new BigDecimal("5")).packCount(8).restValue(BigDecimal.ZERO).build());
        selectable.setPriorityOut(true);
        selectable.setInsufficient(false);
        selectable.setCanGoScrap(false);
        selectable.setAvgPrice(new BigDecimal("9"));
        selectable.setStockVersion(5);
        DrugLotOptionVO unselectable = new DrugLotOptionVO();
        unselectable.setLotId(5201L);
        unselectable.setLotNo("2025047");
        unselectable.setLotStatus("RECALLED");
        unselectable.setInsufficient(false);
        unselectable.setCanGoScrap(true);
        DrugLotOptionsVO options = new DrugLotOptionsVO();
        options.setSelectable(List.of(selectable));
        options.setUnselectable(List.of(unselectable));
        options.setDefaultLotId(6301L);
        lenient().when(lotService.options(anyLong(), any(), any())).thenReturn(options);

        DrugArchiveListResult archive = new DrugArchiveListResult();
        archive.setTotal(5);
        archive.setList(List.of(archiveItem()));
        lenient().when(drugService.list(any(), any())).thenReturn(archive);

        DrugOptionListResult drugOptions = new DrugOptionListResult();
        DrugOptionVO drugOption = new DrugOptionVO();
        drugOption.setDrugId(5201L);
        drugOption.setName("阿莫西林可溶性粉");
        drugOption.setCategory("VET_DRUG");
        drugOption.setSpec("10% · 500 克/袋");
        drugOption.setUnit("G");
        drugOption.setPackUnit("袋");
        drugOption.setPackSize(new BigDecimal("500"));
        drugOption.setUsable(QtyVO.builder().value(new BigDecimal("6000")).unit("G").unitText("克").build());
        drugOptions.setList(List.of(drugOption));
        drugOptions.setBannedHint("克仑特罗为禁用药，不能用于食品动物");
        lenient().when(drugService.options(any(), any())).thenReturn(drugOptions);

        DrugDetailVO drugDetail = new DrugDetailVO();
        drugDetail.setDrugId(5201L);
        drugDetail.setCategory("VET_DRUG");
        drugDetail.setCatalogId(7201L);
        drugDetail.setName("阿莫西林可溶性粉");
        drugDetail.setCatalogSourceText("兽药目录");
        drugDetail.setSpec("10% · 500 克/袋");
        drugDetail.setUnit("G");
        drugDetail.setPackUnit("袋");
        drugDetail.setPackUnitOptions(List.of("袋", "桶"));
        drugDetail.setPackSize(new BigDecimal("500"));
        drugDetail.setPackSizeLocked(false);
        drugDetail.setMinStock(new BigDecimal("7500"));
        drugDetail.setUsable(QtyVO.builder().value(new BigDecimal("6000")).unit("G").unitText("克").build());
        drugDetail.setUnitLocked(true);
        drugDetail.setBanned(false);
        drugDetail.setNearDays(30);
        drugDetail.setVersion(2);
        drugDetail.setUpdatedAt("2026-10-05 09:00:00");
        drugDetail.setUpdatedByName("王建国");
        lenient().when(drugService.detail(anyLong())).thenReturn(drugDetail);
        DrugDetailVO created = new DrugDetailVO();
        created.setDrugId(5204L);
        lenient().when(drugService.create(any())).thenReturn(created);
        DrugDetailVO updated = new DrugDetailVO();
        updated.setDrugId(5202L);
        lenient().when(drugService.update(anyLong(), any())).thenReturn(updated);

        StockSettingVO setting = new StockSettingVO();
        setting.setNearDaysVet(30);
        setting.setNearDaysVaccine(30);
        setting.setNearDaysDisinfectant(30);
        setting.setVersion(1);
        lenient().when(settingService.get()).thenReturn(setting);
        lenient().when(settingService.save(any())).thenReturn(setting);
        StockSettingVO saved = new StockSettingVO();
        saved.setNearDaysVet(30);
        saved.setNearDaysVaccine(45);
        saved.setNearDaysDisinfectant(15);
        saved.setVersion(2);
        lenient().when(settingService.save(any())).thenReturn(saved);

        RequestStatusVO status = new RequestStatusVO();
        status.setStatus("CREATED");
        status.setBizType("INBOUND");
        status.setBizId(6201L);
        lenient().when(requestService.status(any())).thenReturn(status);
    }

    private static StockFlowItemVO flowItem() {
        StockFlowItemVO flow = new StockFlowItemVO();
        flow.setFlowId(9301L);
        flow.setFlowType("FEED_USE");
        flow.setFlowTypeText("饲喂领用");
        flow.setDirection("OUT");
        flow.setMaterialType("FEED");
        flow.setMaterialName("育肥全价料");
        flow.setQty(QtyVO.builder().value(new BigDecimal("158")).unit("KG").unitText("千克").build());
        flow.setStockQtyAfter(new BigDecimal("500"));
        flow.setRelatedDesc("ZM2610-031 · 3 号育肥舍 早餐");
        flow.setBatchId(3001L);
        flow.setBatchNo("ZM2610-031");
        flow.setPenName("3 号育肥舍");
        flow.setOperatorName("李明");
        flow.setOccurAt("2026-10-19 07:12:00");
        flow.setBizDate("2026-10-19");
        flow.setStatus("NORMAL");
        flow.setStatusText("正常");
        flow.setBizType("FEED_RECORD");
        flow.setBizId(7106L);
        flow.setUnitPrice(new BigDecimal("3.2"));
        flow.setAmount(new BigDecimal("505.6"));
        return flow;
    }

    private static StockFlowItemVO drugFlow() {
        StockFlowItemVO flow = new StockFlowItemVO();
        flow.setFlowId(9500L);
        flow.setFlowType("INBOUND");
        flow.setFlowTypeText("入库");
        flow.setDirection("IN");
        flow.setMaterialType("DRUG");
        flow.setMaterialName("口蹄疫灭活疫苗");
        flow.setLotNo("2025047");
        flow.setQty(QtyVO.builder().value(new BigDecimal("400")).unit("DOSE").unitText("头份").build());
        flow.setBizType("INBOUND");
        flow.setBizId(6210L);
        flow.setOccurAt("2026-10-08 10:00:00");
        flow.setStatus("NORMAL");
        return flow;
    }

    private static UserBriefVO user() {
        return UserBriefVO.builder().userId(StockFixtures.OWNER_ID).name("王建国").role("OWNER")
                .roleText("所有者").build();
    }

    private static com.zhimu.server.biz.stock.vo.StockActionsVO actions(boolean editable) {
        com.zhimu.server.biz.stock.vo.StockActionsVO actions =
                new com.zhimu.server.biz.stock.vo.StockActionsVO();
        actions.setCanEdit(editable);
        actions.setEditDeadline("2026-10-19 24:00");
        actions.setEditState(editable ? "DIRECT" : "REQUEST");
        actions.setCanVoid(editable);
        actions.setCanRequestChange(!editable);
        actions.setCanScrap(true);
        actions.setCanOut(true);
        return actions;
    }

    private static DrugArchiveItemVO archiveItem() {
        DrugArchiveItemVO item = new DrugArchiveItemVO();
        item.setDrugId(5201L);
        item.setCatalogId(7201L);
        item.setName("阿莫西林可溶性粉");
        item.setCategory("VET_DRUG");
        item.setUsable(QtyVO.builder().value(new BigDecimal("6000")).unit("G").unitText("克").build());
        item.setMinStock(new BigDecimal("7500"));
        item.setLowStock(true);
        item.setHasStock(true);
        item.setBanned(false);
        return item;
    }

    /** 未使用的请求类型引用，保证契约字段被编译期检查 */
    @SuppressWarnings("unused")
    private static void requestTypes() {
        Class<?>[] types = {FeedInboundRequest.class, DrugInboundRequest.class, InboundUpdateRequest.class,
                VoidRequest.class, FeedSaveRequest.class, DrugSaveRequest.class, FeedLossRequest.class,
                DrugScrapRequest.class, FeedOtherOutRequest.class, DrugOutboundRequest.class,
                OutboundUpdateRequest.class, StocktakeConfirmRequest.class, StockSettingRequest.class};
    }
}
