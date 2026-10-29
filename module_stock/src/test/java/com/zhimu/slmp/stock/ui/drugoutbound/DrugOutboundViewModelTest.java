package com.zhimu.slmp.stock.ui.drugoutbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.CostAllocationVO;
import com.zhimu.slmp.stock.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.StockBatchOptionVO;
import com.zhimu.slmp.stock.model.vo.StockMemberVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.model.vo.StockPenOptionVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * M11 兽药与疫苗出库：领用按先到期先出预选默认批号；切到退货只清空系统默认的批号（手选或从 M06 预选的保留）；
 * 改选较晚批号时提示更早到期批号；数量上限与头份整数；关联栏舍后取分摊预览并拼分摊行；退货必填项；成功文案。
 */
public class DrugOutboundViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private StockRepository repo;
    private DrugOutboundViewModel vm;

    @Before
    public void setUp() throws IOException {
        repo = mock(StockRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        SessionManager session = mock(SessionManager.class);
        when(session.currentUser()).thenReturn(GsonFactory.get().fromJson(
                "{\"user_id\":10001,\"name\":\"王建国\"}", AccountUserVO.class));
        when(repo.listDrugOptions(eq("OUTBOUND"), isNull())).thenReturn(Single.just(
                TestRules.mock("drug_options_outbound.json", DrugOptionListResult.class)));
        for (long drug : new long[]{5301, 5201, 5001}) {
            for (String p : new String[]{"USE", "RETURN"}) {
                when(repo.listDrugLotOptions(eq(drug), eq(p), isNull())).thenReturn(Single.just(TestRules.mock(
                        "lotopt_" + drug + "_" + p.toLowerCase() + ".json", DrugLotOptionsVO.class)));
            }
        }
        List<StockPenOptionVO> pens = GsonFactory.get().fromJson("[{\"pen_id\":2004,\"pen_name\":\"4 号育肥舍\","
                        + "\"stock_count\":240,\"disabled\":false,\"unit_text\":\"头\"},{\"pen_id\":2005,"
                        + "\"pen_name\":\"5 号育肥舍\",\"stock_count\":0,\"disabled\":false,\"unit_text\":\"头\"}]",
                new TypeToken<List<StockPenOptionVO>>() {
                }.getType());
        List<StockBatchOptionVO> batches = GsonFactory.get().fromJson("[{\"batch_id\":3002,\"batch_no\":\"ZM2610-019\","
                        + "\"current_count\":150,\"unit_text\":\"头\",\"pens\":[{\"pen_id\":2004,\"pen_name\":\"4 号育肥舍\","
                        + "\"stock_count\":150}]},{\"batch_id\":3005,\"batch_no\":\"ZM2610-058\",\"current_count\":90,"
                        + "\"unit_text\":\"头\",\"pens\":[{\"pen_id\":2004,\"pen_name\":\"4 号育肥舍\",\"stock_count\":90}]}]",
                new TypeToken<List<StockBatchOptionVO>>() {
                }.getType());
        List<StockMemberVO> members = GsonFactory.get().fromJson("[{\"user_id\":10001,\"name\":\"王建国\"},"
                + "{\"user_id\":10004,\"name\":\"张伟\"}]", new TypeToken<List<StockMemberVO>>() {
        }.getType());
        when(repo.getPenOptions()).thenReturn(Single.just(pens));
        when(repo.getActiveBatches()).thenReturn(Single.just(batches));
        when(repo.getMembers()).thenReturn(Single.just(members));
        when(repo.previewAllocation(anyLong(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(Single.just(TestRules.mock("alloc_pen.json", CostAllocationVO.class)));
        when(repo.recentPartners(anyString(), any())).thenReturn(Single.just(java.util.Collections.emptyList()));
        vm = new DrugOutboundViewModel(repo, TestRules.noDrafts(), farm, session);
        vm.getReturnAmount().observeForever(v -> {
        });
    }

    private void open(Long drugId, Long lotId) {
        Map<String, Object> args = new HashMap<>();
        if (drugId != null) {
            args.put(RouterPath.EXTRA_DRUG_ID, drugId);
        }
        if (lotId != null) {
            args.put(RouterPath.EXTRA_LOT_ID, lotId);
        }
        vm.attachSavedState(new SavedStateHandle(args));
    }

    @Test
    public void use_preselectsDefaultLot_andReturnClearsIt() {
        open(5301L, null);
        assertEquals(6301, vm.getSelectedLot().getValue().lotId);
        assertTrue(vm.isLotDefault());
        assertEquals("有效期至 2026-11-08 · 临期 20 天 · 结存 8 桶", TestRules.plain(vm.getLotHelper().getValue()));
        assertEquals("最多 8 桶，按该批号结存计算", TestRules.plain(vm.getMaxText().getValue()));
        assertTrue(vm.getShowPriorityTag().getValue());
        assertEquals("王建国", vm.getReceiverText().getValue());
        vm.setOutType(StockEnums.OUT_RETURN);
        assertNull(vm.getSelectedLot().getValue());
    }

    @Test
    public void refreshInNewMode_reloadsDrugsAndLotsAndKeepsSelection() {
        open(5301L, null);
        org.mockito.Mockito.clearInvocations(repo);
        // 没有 current_qty 的 40902 只给“刷新”：新建模式也要重拉药品与批号（取到新的 stock_version）
        vm.refresh();
        org.mockito.Mockito.verify(repo).listDrugOptions(eq("OUTBOUND"), isNull());
        org.mockito.Mockito.verify(repo).listDrugLotOptions(eq(5301L), eq("USE"), isNull());
        assertEquals(6301, vm.getSelectedLot().getValue().lotId);
    }

    @Test
    public void presetLotFromM06_keptWhenSwitchingToReturn() {
        open(5301L, 6301L);
        assertFalse(vm.isLotDefault());
        vm.setOutType(StockEnums.OUT_RETURN);
        assertEquals(6301, vm.getSelectedLot().getValue().lotId);
        assertEquals("有效期至 2026-11-08 · 结存 8 桶", TestRules.plain(vm.getLotHelper().getValue()));
        assertEquals("最多 8 桶", TestRules.plain(vm.getMaxText().getValue()));
    }

    @Test
    public void laterLot_hintsEarlierOne() {
        open(5001L, null);
        vm.onLotPicked(6002L);
        assertTrue(TestRules.plain(vm.getLotHelper().getValue()).endsWith("· 批号 2025081 更早到期，建议优先领用"));
        assertFalse(vm.getShowPriorityTag().getValue());
        assertEquals("最多 600 头份（60 瓶），按该批号结存计算", TestRules.plain(vm.getMaxText().getValue()));
    }

    @Test
    public void qty_overStockAndDoseInteger() {
        open(5301L, null);
        vm.getInputQty().setValue("9");
        vm.submit();
        assertEquals("超过该批号结存 8 桶，出库后库存不能为负数",
                TestRules.plain(vm.getFieldErrors().getValue().get(DrugOutboundViewModel.F_QTY)));
        assertEquals("请选择批次", vm.getFieldErrors().getValue().get(DrugOutboundViewModel.F_BATCH));
        open2(5001L);
        vm.getInputQty().setValue("2.5");
        vm.submit();
        assertEquals("头份必须是整数", vm.getFieldErrors().getValue().get(DrugOutboundViewModel.F_QTY));
    }

    private void open2(long drugId) {
        vm.onDrugPicked(drugId);
    }

    @Test
    public void penTarget_previewsAllocation() {
        open(5301L, null);
        vm.getInputQty().setValue("2");
        vm.setTargetType(StockEnums.TARGET_PEN);
        vm.onPenPicked(2004L);
        assertEquals("在栏 2 个批次，成本按存栏量比例分摊", vm.getTargetHelper().getValue());
        verify(repo, atLeastOnce()).previewAllocation(eq(6301L), any(), eq("PACK"), eq("PEN"), isNull(), eq(2004L));
        List<String[]> rows = DrugOutboundViewModel.allocationRows(vm.getAllocation().getValue());
        assertEquals("ZM2610-019 · 存栏 150 头 · 62.5%", rows.get(0)[0]);
        assertEquals("56.25 元", rows.get(0)[1]);
        assertEquals("90.00", vm.getReturnAmount().getValue());
        vm.onPenPicked(2005L);
        assertEquals(Long.valueOf(2004), vm.getPenId().getValue());
    }

    @Test
    public void return_requiredFields() {
        open(5201L, null);
        vm.setOutType(StockEnums.OUT_RETURN);
        assertNull(vm.getSelectedLot().getValue());
        vm.onLotPicked(6101L);
        vm.getInputQty().setValue("4");
        assertEquals("64.00", vm.getReturnAmount().getValue());
        vm.onReturnReasonPicked(StockEnums.RETURN_OTHER);
        vm.submit();
        Map<String, String> e = vm.getFieldErrors().getValue();
        assertEquals("请填写退回对象", e.get(DrugOutboundViewModel.F_RETURN_TO));
        assertEquals("请写明退货原因", e.get(DrugOutboundViewModel.F_REMARK));
        assertNull(e.get(DrugOutboundViewModel.F_BATCH));
    }

    @Test
    public void submit_successTexts() throws IOException {
        StockOutboundVO pen = TestRules.mock("outbound_6511.json", StockOutboundVO.class);
        StockOutboundVO batch = TestRules.mock("outbound_6513.json", StockOutboundVO.class);
        assertEquals("已登记领用 2 桶聚维酮碘溶液，成本已计入 2 个批次",
                DrugOutboundViewModel.successText(true, "2 桶", "聚维酮碘溶液", pen.allocations));
        assertEquals("已登记领用 2 桶聚维酮碘溶液，成本已计入 ZM2610-031",
                DrugOutboundViewModel.successText(true, "2 桶", "聚维酮碘溶液", batch.allocations));
        assertEquals("已登记退货 4 袋阿莫西林可溶性粉",
                DrugOutboundViewModel.successText(false, "4 袋", "阿莫西林可溶性粉", null));

        open(5301L, null);
        vm.getInputQty().setValue("2");
        vm.setTargetType(StockEnums.TARGET_PEN);
        vm.onPenPicked(2004L);
        vm.onReceiverPicked(10004L);
        when(repo.createDrugOutbound(any(), anyString())).thenReturn(Single.just(pen));
        vm.submit();
        assertEquals("已登记领用 2 桶聚维酮碘溶液，成本已计入 2 个批次", TestRules.plain(vm.getSubmitted().getValue()));
        assertEquals(Long.valueOf(10004), vm.buildRequest().receiverId);
    }

    @Test
    public void goScrap_asksDiscardWhenDirty() {
        open(5201L, null);
        vm.onGoScrap(6103L);
        assertEquals(Long.valueOf(6103), vm.getOpenScrap().getValue());
        vm.getInputQty().setValue("1");
        vm.onGoScrap(6103L);
        assertEquals(Long.valueOf(6103), vm.getConfirmDiscard().getValue());
        assertEquals(6101, vm.getSelectedLot().getValue().lotId);
    }

    @Test
    public void view_voidTextListsBatches() throws IOException {
        when(repo.getOutbound(6511L)).thenReturn(Single.just(TestRules.mock("outbound_6511.json",
                StockOutboundVO.class)));
        open(null, null);
        vm.init(null, null, 6511L);
        assertEquals("领用数量", vm.getReadonlyRows().getValue().get(2)[0]);
        vm.onVoidMenu();
        assertEquals("作废后聚维酮碘溶液 2025033 结存增加 2 桶，计入 ZM2610-019、ZM2610-058 的 90 元成本同时撤销。",
                TestRules.plain(vm.getConfirmVoid().getValue()));
    }
}
