package com.zhimu.slmp.stock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.DrugScrapRequest;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.FeedOptionVO;
import com.zhimu.slmp.stock.model.vo.StockLossVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.model.vo.StockSettingVO;
import com.zhimu.slmp.stock.ui.druginbound.DrugInboundViewModel;
import com.zhimu.slmp.stock.ui.druglot.DrugLotDetailViewModel;
import com.zhimu.slmp.stock.ui.drugscrap.DrugScrapViewModel;
import com.zhimu.slmp.stock.ui.otherout.FeedOtherOutViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * M07 报废（默认全部结存、原因按状态预选、损失金额、全部 / 部分报废的确认文案、日期不早于首次入库）、
 * M08 入库（有效期早于入库日期、临期提示、头份整数、批号格式）、M10 其他出库（去向必选、说明必填、超结存、折合金额）、
 * M06 横幅（已停用 / 已过期 / 临期）。
 */
public class DrugFormsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private StockRepository repo;
    private FarmContext farm;

    @Before
    public void setUp() {
        repo = mock(StockRepository.class);
        farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
    }

    private static SavedStateHandle args(String key, long id) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, id);
        return new SavedStateHandle(m);
    }

    // ===================== M07 =====================

    private DrugScrapViewModel scrap(long lotId) throws IOException {
        return scrap(TestRules.mock("lot_" + lotId + ".json", DrugLotDetailVO.class));
    }

    private DrugScrapViewModel scrap(DrugLotDetailVO lot) throws IOException {
        long lotId = lot.lotId;
        BaseDataRepository base = mock(BaseDataRepository.class);
        List<DictItemVO> dict = TestRules.mock("dict_scrap_reason.json", new TypeToken<List<DictItemVO>>() {
        }.getType());
        when(base.getDicts(eq("SCRAP_REASON"), isNull())).thenReturn(Single.just(dict));
        when(repo.getDrugLot(lotId)).thenReturn(Single.just(lot));
        DrugScrapViewModel vm = new DrugScrapViewModel(repo, TestRules.noDrafts(), base, farm);
        vm.getLossText().observeForever(v -> {
        });
        vm.attachSavedState(args(RouterPath.EXTRA_LOT_ID, lotId));
        return vm;
    }

    @Test
    public void scrap_recalledDefaultsAndConfirmTexts() throws IOException {
        DrugScrapViewModel vm = scrap(5201);
        assertEquals("300", vm.getInputQty().getValue());
        assertEquals(StockEnums.SCRAP_PLATFORM_RECALL, vm.getReason().getValue());
        assertEquals("生产企业召回，抽检效价不合格", vm.getRemark().getValue());
        assertEquals("最多 300 头份，全部报废后该批号结存清零", TestRules.plain(vm.getMaxText().getValue()));
        assertEquals("840.00", vm.getLossText().getValue());
        assertEquals("批号 2025047 · 结存 300 头份\n（30 瓶）", TestRules.plain(vm.getCardSubtitle().getValue()));
        vm.submit();
        String[] confirm = vm.getShowConfirm().getValue();
        assertEquals("报废这批疫苗？", confirm[0]);
        assertEquals("口蹄疫灭活疫苗 2025047 共 300 头份（30 瓶）将全部报废，损失 840 元，不计入批次成本。确认后如需修改，要提交修改申请。",
                TestRules.plain(confirm[1]));
        vm.getInputQty().setValue("100");
        vm.submit();
        assertEquals("口蹄疫灭活疫苗 2025047 中的 100 头份将报废，损失 280 元，不计入批次成本。确认后如需修改，要提交修改申请。",
                TestRules.plain(vm.getShowConfirm().getValue()[1]));
    }

    @Test
    public void scrap_validation() throws IOException {
        DrugScrapViewModel vm = scrap(5201);
        vm.getInputQty().setValue("301");
        vm.onDatePicked("2026-10-01");
        vm.submit();
        assertEquals("超过当前结存 300 头份", TestRules.plain(vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY)));
        assertEquals("报废日期不能早于入库日期 10月8日", vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_DATE));
        vm.getInputQty().setValue("1.5");
        vm.onDatePicked("2026-10-19");
        vm.onReasonPicked("OTHER");
        vm.getRemark().setValue("");
        vm.submit();
        assertEquals("头份必须是整数", vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY));
        assertEquals("请写明报废原因", vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_REMARK));
    }

    @Test
    public void scrap_expiredLot_noRecallReasonAndPackInput() throws IOException {
        DrugScrapViewModel vm = scrap(6103);
        assertEquals(StockEnums.SCRAP_EXPIRED, vm.getReason().getValue());
        for (DictItemVO r : vm.getReasons().getValue()) {
            assertFalse(StockEnums.SCRAP_PLATFORM_RECALL.equals(r.getCode()));
        }
        assertEquals("3", vm.getInputQty().getValue());
        assertEquals("袋", vm.getUnitText().getValue());
        assertEquals("45.00", vm.getLossText().getValue());
        vm.submit();
        assertEquals("报废这批兽药？", vm.getShowConfirm().getValue()[0]);
        assertTrue(vm.getShowConfirm().getValue()[1].startsWith("阿莫西林可溶性粉 2024118 共 3"));
    }

    @Test
    public void scrap_success() throws IOException {
        DrugScrapViewModel vm = scrap(5201);
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.just(TestRules.mock("scrap_created.json",
                StockLossVO.class)));
        vm.doScrap();
        assertEquals("已登记报废，口蹄疫灭活疫苗 2025047 结存已清零", vm.getScrapped().getValue());
    }

    @Test
    public void scrap_packRequestPreservesQuantityAndUnit() throws IOException {
        DrugScrapViewModel vm = scrap(6103);
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.just(TestRules.mock("scrap_created.json",
                StockLossVO.class)));
        vm.doScrap();
        ArgumentCaptor<DrugScrapRequest> request = ArgumentCaptor.forClass(DrugScrapRequest.class);
        verify(repo).createDrugScrap(request.capture(), anyString());
        assertEquals(StockEnums.INPUT_PACK, request.getValue().inputUnit);
        assertEquals(0, new BigDecimal("3").compareTo(request.getValue().inputQty));
    }

    @Test
    public void scrap_residualUsesExactBaseQuantityAndRemainingAmount() throws IOException {
        DrugLotDetailVO lot = TestRules.mock("lot_6103.json", DrugLotDetailVO.class);
        lot.stock.value = new BigDecimal("2982.05");
        lot.stock.packCount = null;
        lot.stock.restValue = null;
        lot.stockAmount = new BigDecimal("89.46");
        DrugScrapViewModel vm = scrap(lot);
        assertEquals("2982.05", vm.getInputQty().getValue());
        assertEquals("克", vm.getUnitText().getValue());
        assertEquals("89.46", vm.getLossText().getValue());
        vm.submit();
        assertTrue(vm.getFieldErrors().getValue().isEmpty());
        assertTrue(TestRules.plain(vm.getShowConfirm().getValue()[1]).contains("2,982.05 克将全部报废"));
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.just(TestRules.mock("scrap_created.json",
                StockLossVO.class)));
        vm.doScrap();
        ArgumentCaptor<DrugScrapRequest> request = ArgumentCaptor.forClass(DrugScrapRequest.class);
        verify(repo).createDrugScrap(request.capture(), anyString());
        assertEquals(StockEnums.INPUT_MEASURE, request.getValue().inputUnit);
        assertEquals(0, lot.stock.value.compareTo(request.getValue().inputQty));
    }

    @Test
    public void scrap_tinyResidualCanBeClearedWithoutRoundingToZero() throws IOException {
        DrugLotDetailVO lot = TestRules.mock("lot_6103.json", DrugLotDetailVO.class);
        lot.stock.value = new BigDecimal("0.001");
        lot.stock.packCount = null;
        lot.stock.restValue = null;
        DrugScrapViewModel vm = scrap(lot);
        assertEquals("0.001", vm.getInputQty().getValue());
        assertEquals("克", vm.getUnitText().getValue());
        vm.submit();
        assertTrue(vm.getFieldErrors().getValue().isEmpty());
        vm.getInputQty().setValue("0.0001");
        vm.doScrap();
        assertEquals("数量最多保留 3 位小数", vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY));
        verify(repo, never()).createDrugScrap(any(), anyString());
    }

    @Test
    public void scrap_doesNotClaimClearedWhenServerStillHasStock() throws IOException {
        DrugScrapViewModel vm = scrap(6103);
        StockLossVO response = TestRules.mock("scrap_created.json", StockLossVO.class);
        response.remaining.value = new BigDecimal("1497");
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.just(response));
        vm.doScrap();
        assertFalse(vm.getScrapped().getValue().contains("清零"));
    }

    @Test
    public void scrap_conflictRefreshesInputUnitAndExactMaximum() throws IOException {
        DrugScrapViewModel vm = scrap(6103);
        DrugLotDetailVO fresh = TestRules.mock("lot_6103.json", DrugLotDetailVO.class);
        fresh.stock.value = new BigDecimal("1499.999");
        fresh.stock.packCount = null;
        fresh.stock.restValue = null;
        fresh.stockVersion++;
        when(repo.getDrugLot(fresh.lotId)).thenReturn(Single.just(fresh));
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.error(
                new ApiException(ErrorCodes.VERSION_CONFLICT, "库存已变化")));
        vm.doScrap();
        assertEquals("1499.999", vm.getInputQty().getValue());
        assertEquals("克", vm.getUnitText().getValue());
        assertTrue(vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY).contains("重新确认"));
        assertEquals(StockEnums.SCRAP_EXPIRED, vm.getReason().getValue());
    }

    @Test
    public void scrap_conflictMessageSurvivesBindingEchoUntilQtyEdited() throws IOException {
        DrugScrapViewModel vm = scrap(6103);
        DrugLotDetailVO fresh = TestRules.mock("lot_6103.json", DrugLotDetailVO.class);
        fresh.stockVersion++;
        when(repo.getDrugLot(fresh.lotId)).thenReturn(Single.just(fresh));
        when(repo.createDrugScrap(any(), anyString())).thenReturn(Single.error(
                new ApiException(ErrorCodes.VERSION_CONFLICT, "库存已变化")));
        vm.doScrap();
        String err = vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY);
        assertTrue(err.contains("已刷新结存"));
        // 按最新结存重填数量后，输入框双向绑定会把同样的文字回写一次：不算用户修改，提示保留
        vm.getInputQty().setValue(vm.getInputQty().getValue());
        assertEquals(err, vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY));
        vm.getInputQty().setValue("1");
        assertNull(vm.getFieldErrors().getValue().get(DrugScrapViewModel.F_QTY));
    }

    // ===================== M08 =====================

    private DrugInboundViewModel drugInbound() throws IOException {
        when(repo.listDrugOptions(eq("INBOUND"), isNull())).thenReturn(Single.just(TestRules.mock(
                "drug_options_inbound.json", DrugOptionListResult.class)));
        when(repo.getStockSetting()).thenReturn(Single.just(TestRules.mock("stock_setting.json",
                StockSettingVO.class)));
        DrugInboundViewModel vm = new DrugInboundViewModel(repo, TestRules.noDrafts(), farm);
        vm.getAmountText().observeForever(v -> {
        });
        vm.attachSavedState(args(RouterPath.EXTRA_DRUG_ID, 5003));
        return vm;
    }

    @Test
    public void drugInbound_expireAndDoseChecks() throws IOException {
        DrugInboundViewModel vm = drugInbound();
        assertEquals("口蹄疫灭活疫苗", vm.getDrugName().getValue());
        assertEquals("疫苗", vm.getCategoryText().getValue());
        assertEquals("瓶", vm.getPackUnit().getValue());
        vm.onDatePicked(DrugInboundViewModel.F_EXPIRE, "2026-10-15");
        assertEquals("早于入库日期，不能入库", vm.getFieldErrors().getValue().get(DrugInboundViewModel.F_EXPIRE));
        vm.onDatePicked(DrugInboundViewModel.F_EXPIRE, "2026-11-08");
        assertNull(vm.getFieldErrors().getValue().get(DrugInboundViewModel.F_EXPIRE));
        assertEquals("有效期剩 20 天，入库后即为临期", vm.getExpireHint().getValue());
        vm.onDatePicked(DrugInboundViewModel.F_EXPIRE, "2027-09-30");
        assertNull(vm.getExpireHint().getValue());
        vm.getLotNo().setValue("20 25");
        vm.getInputQty().setValue("2.55");
        vm.getUnitPrice().setValue("28");
        vm.submit();
        Map<String, String> e = vm.getFieldErrors().getValue();
        assertEquals("只能输入字母、数字和短横线，最多 30 个字符", e.get(DrugInboundViewModel.F_LOT));
        assertEquals("头份必须是整数", e.get(DrugInboundViewModel.F_QTY));
        vm.getInputQty().setValue("50");
        assertEquals("1,400.00", vm.getAmountText().getValue());
        assertEquals("已入库 50 瓶口蹄疫灭活疫苗，已同步记一笔支出", TestRules.plain(DrugInboundViewModel.successMessage(
                new java.math.BigDecimal("50"), "瓶", "口蹄疫灭活疫苗", true).getText()));
    }

    // ===================== M10 =====================

    @Test
    public void otherOut_conflictMessageStaysUntilQtyEditedOrResubmitted() throws IOException {
        List<FeedOptionVO> options = TestRules.mock("feed_options_other_out.json",
                new TypeToken<List<FeedOptionVO>>() {
                }.getType());
        when(repo.listFeedOptions(eq("OTHER_OUT"), isNull())).thenReturn(Single.just(options));
        FeedOtherOutViewModel vm = new FeedOtherOutViewModel(repo, TestRules.noDrafts(), farm);
        vm.getAmountText().observeForever(v -> {
        });
        vm.attachSavedState(new SavedStateHandle());
        vm.onFeedPicked(5102L);
        vm.getInputQty().setValue("200");
        vm.onDestinationPicked(StockEnums.DEST_SALE);
        when(repo.createFeedOtherOut(any(), anyString())).thenReturn(Single.error(new ApiException(
                ErrorCodes.VERSION_CONFLICT, "库存已变化", "t",
                com.google.gson.JsonParser.parseString("{\"current_qty\":\"1800\"}"), null, null)));
        vm.submit();
        String err = vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_QTY);
        assertTrue(TestRules.plain(err).startsWith("库存已变化，当前结存 1,800"));
        // 自动重拉选项并重新选中饲料后，双向绑定回写同样的数量：提示保留
        vm.getInputQty().setValue("200");
        assertEquals(err, vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_QTY));
        // 用户改数量才清除
        vm.getInputQty().setValue("150");
        assertNull(vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_QTY));
    }

    @Test
    public void otherOut_validationAndAmount() throws IOException {
        List<FeedOptionVO> options = TestRules.mock("feed_options_other_out.json",
                new TypeToken<List<FeedOptionVO>>() {
                }.getType());
        when(repo.listFeedOptions(eq("OTHER_OUT"), isNull())).thenReturn(Single.just(options));
        FeedOtherOutViewModel vm = new FeedOtherOutViewModel(repo, TestRules.noDrafts(), farm);
        vm.getAmountText().observeForever(v -> {
        });
        vm.attachSavedState(new SavedStateHandle());
        vm.onFeedPicked(5103L);
        assertNull(vm.getSelectedFeed().getValue());
        vm.onFeedPicked(5102L);
        assertEquals("当前结存 1,850 千克 · 均价每千克 4.20 元", TestRules.plain(vm.getFeedHint().getValue()));
        assertEquals("最多 1,850 千克，出库后库存不能为负数", TestRules.plain(vm.getMaxText().getValue()));
        vm.getInputQty().setValue("200");
        assertEquals("840.00", vm.getAmountText().getValue());
        vm.submit();
        assertEquals("请选择出库去向", vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_DEST));
        vm.onDestinationPicked(StockEnums.DEST_TRANSFER_FARM);
        assertTrue(vm.getRemarkRequired().getValue());
        assertEquals("对方养殖场不会自动入库，需要对方自行登记入库", vm.getDestHint().getValue());
        vm.getInputQty().setValue("2000");
        vm.submit();
        assertEquals("请写明调往哪个养殖场", vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_REMARK));
        assertEquals("超过当前结存 1,850 千克，出库后库存不能为负数",
                TestRules.plain(vm.getFieldErrors().getValue().get(FeedOtherOutViewModel.F_QTY)));
        vm.onDestinationPicked(StockEnums.DEST_RETURN_SUPPLIER);
        vm.getInputQty().setValue("200");
        when(repo.createFeedOtherOut(any(), anyString())).thenReturn(Single.just(TestRules.mock("outbound_6502.json",
                StockOutboundVO.class)));
        vm.submit();
        assertEquals("已登记其他出库 200 千克保育料", TestRules.plain(vm.getSubmitted().getValue()));
    }

    // ===================== M06 =====================

    @Test
    public void lotBanner_threeStates() throws IOException {
        DrugLotDetailViewModel.Banner recalled = DrugLotDetailViewModel.bannerOf(TestRules.mock("lot_5201.json",
                DrugLotDetailVO.class));
        assertEquals("该批号已被平台停用：", recalled.title);
        assertEquals("原因是生产企业召回，抽检效价不合格。该批号不能出库，登记接种时也选不到，只能报废。", recalled.text);
        DrugLotDetailViewModel.Banner expired = DrugLotDetailViewModel.bannerOf(TestRules.mock("lot_6103.json",
                DrugLotDetailVO.class));
        assertEquals("有效期至 2026-10-12。该批号不能出库，登记用药时也选不到，只能报废。", expired.text);
        DrugLotDetailViewModel.Banner near = DrugLotDetailViewModel.bannerOf(TestRules.mock("lot_6301.json",
                DrugLotDetailVO.class));
        assertEquals("还有 20 天到期。登记接种、用药或消毒时会优先扣减这个批号。", near.text);
        assertNull(DrugLotDetailViewModel.bannerOf(TestRules.mock("lot_6001.json", DrugLotDetailVO.class)));
        assertEquals("疫苗 · 批号 2025047", DrugLotDetailViewModel.subtitle(TestRules.mock("lot_5201.json",
                DrugLotDetailVO.class)));
    }
}
