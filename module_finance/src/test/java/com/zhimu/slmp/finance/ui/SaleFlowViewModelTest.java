package com.zhimu.slmp.finance.ui;

import static com.zhimu.slmp.finance.TestRules.plain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.ui.change.ChangeRepository;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SaleOrderRequest;
import com.zhimu.slmp.finance.model.vo.ReceivableVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SaleChangePreviewVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderCreatedVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.ui.receivable.ReceivableListViewModel;
import com.zhimu.slmp.finance.ui.salechange.SaleChangeRequestViewModel;
import com.zhimu.slmp.finance.ui.saledetail.SaleOrderDetailViewModel;
import com.zhimu.slmp.finance.ui.saleedit.SaleOrderEditViewModel;
import com.zhimu.slmp.finance.util.NavTarget;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * E06 休药期拦截、均重与销售金额预估、校验、42202 / 42205、成功提示；E07 菜单与横幅；E08 预计结果与校验；E09 提醒文案。
 */
public class SaleFlowViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private SaleOrderRepository repo;
    private FarmContext farm;

    @Before
    public void setUp() throws IOException {
        repo = mock(SaleOrderRepository.class);
        farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        List<SaleBatchOptionVO> batches = TestRules.mock("batch_options.json",
                new TypeToken<List<SaleBatchOptionVO>>() {
                }.getType());
        when(repo.getBatchOptions()).thenReturn(Single.just(batches));
        when(repo.getPrefill(any(), any())).thenReturn(Single.just(TestRules.mock("prefill.json", SalePrefillVO.class)));
    }

    private SaleOrderEditViewModel newEdit(Long batchId) {
        SaleOrderEditViewModel vm = new SaleOrderEditViewModel(repo, farm);
        vm.getCanSubmit().observeForever(v -> {
        });
        Map<String, Object> args = new HashMap<>();
        if (batchId != null) {
            args.put(FinanceExtras.BATCH_ID, batchId);
        }
        vm.attachSavedState(new SavedStateHandle(args));
        return vm;
    }

    @Test
    public void withdrawalBatch_blocksSubmit() {
        SaleOrderEditViewModel vm = newEdit(3001L);
        assertTrue(vm.getInWithdrawal().getValue());
        assertFalse(vm.getCanSubmit().getValue());
        assertEquals("休药中 · 剩 10 天", vm.batchTag());
        assertEquals("批次在休药期内，暂不能登记出栏，可出栏日期 10月29日。休药期结束前“提交”不可用，届时再登记出栏单。",
                vm.withdrawalNotice());
        assertEquals("赤峰鑫源肉联厂", vm.buyerName.getValue());
        vm.submit();
        verify(repo, never()).createOrder(any(), anyString());
        // 改选不在休药期的批次后恢复
        vm.selectBatch(3002L);
        assertFalse(vm.getInWithdrawal().getValue());
        assertTrue(vm.getCanSubmit().getValue());
        assertEquals("可出栏", vm.batchTag());
        assertEquals("4 号育肥舍 · 82 日龄 · 存栏 150 头 · 不在休药期", plain(vm.batchHelper()));
    }

    @Test
    public void estimateAndRuleNotice() {
        SaleOrderEditViewModel vm = newEdit(3002L);
        assertEquals("最多 150 头", plain(vm.quantityHelper()));
        assertEquals("自动计算均重", vm.weightHelper());
        vm.quantityText.setValue("60");
        vm.totalWeightText.setValue("7020");
        vm.unitPriceText.setValue("15.60");
        assertEquals(new BigDecimal("117.0"), vm.getAvgWeight().getValue());
        assertEquals(0, vm.getSaleAmount().getValue().compareTo(new BigDecimal("109512")));
        assertEquals("均重 117.0 千克", plain(vm.weightHelper()));
        assertEquals("出栏数量不能超过批次存栏 150 头。提交后自动扣减存栏，ZM2610-019 剩 90 头；全部出栏后批次自动结束。",
                plain(vm.ruleNotice()));
        assertEquals("参考：内蒙古育肥猪出栏价今天每千克 15.60 元", vm.priceHelper());
        vm.quantityText.setValue("150");
        assertTrue(vm.ruleNotice().contains("全部出栏，批次自动结束并结算核算"));
        // 按头：单价单位与金额
        vm.switchPricing(FinanceConst.BY_HEAD);
        assertEquals("", vm.unitPriceText.getValue());
        assertEquals("元/头", vm.priceUnit());
        vm.unitPriceText.setValue("1500");
        assertEquals(0, vm.getSaleAmount().getValue().compareTo(new BigDecimal("225000")));
        // 数量超过存栏时立即报错
        vm.quantityText.setValue("151");
        assertEquals("不能超过批次当前存栏 150 头",
                plain(vm.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_QUANTITY)));
    }

    @Test
    public void validationAndSubmit() throws IOException {
        SaleOrderEditViewModel vm = newEdit(3002L);
        vm.buyerName.setValue("");
        vm.submit();
        Map<String, String> e = vm.getFieldErrors().getValue();
        assertEquals("请输入数量", e.get(SaleOrderEditViewModel.F_QUANTITY));
        assertEquals("请输入总重", e.get(SaleOrderEditViewModel.F_WEIGHT));
        assertEquals("请输入单价", e.get(SaleOrderEditViewModel.F_PRICE));
        assertEquals("请填写收购商", e.get(SaleOrderEditViewModel.F_BUYER));
        vm.quantityText.setValue("60");
        vm.totalWeightText.setValue("7020");
        vm.unitPriceText.setValue("15.60");
        vm.buyerName.setValue("赤峰鑫源肉联厂");
        vm.setDueDate("2026-10-18");
        vm.submit();
        assertEquals("约定收款日期不能早于出栏日期", vm.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_DUE));
        vm.setDueDate("2026-10-26");
        when(repo.createOrder(any(), anyString())).thenReturn(Single.just(TestRules.mock("order_created.json",
                SaleOrderCreatedVO.class)));
        vm.submit();
        ArgumentCaptor<SaleOrderRequest> req = ArgumentCaptor.forClass(SaleOrderRequest.class);
        verify(repo).createOrder(req.capture(), anyString());
        assertEquals(Long.valueOf(3002), req.getValue().getBatchId());
        assertNull("单栏舍不传 pen_quantities", req.getValue().getPenQuantities());
        assertEquals(FinanceConst.BY_WEIGHT, req.getValue().getPricingMethod());
        NavTarget t = vm.getOpenDetail().getValue();
        assertNotNull(t);
        assertTrue(t.isFinishSelf());
        assertEquals(9106L, t.get(FinanceExtras.SALE_ORDER_ID));
        assertEquals("已登记出栏单，ZM2610-019 存栏剩 90 头", plain(SaleOrderEditViewModel.createdText(
                TestRules.mock("order_created.json", SaleOrderCreatedVO.class))));
    }

    @Test
    public void multiPenAllocationMustSumToQuantity() {
        SaleOrderEditViewModel vm = newEdit(3004L);
        assertEquals(2, vm.getPenQuantities().getValue().size());
        vm.quantityText.setValue("100");
        vm.setPenQuantity(0, "70");
        vm.setPenQuantity(1, "20");
        vm.totalWeightText.setValue("3000");
        vm.unitPriceText.setValue("16");
        vm.submit();
        assertEquals("各栏舍出栏数合计应等于出栏数量 100 头",
                plain(vm.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_PENS)));
        vm.setPenQuantity(1, "90");
        vm.submit();
        assertEquals("1 号保育舍本批次在栏 80 头，不能超过",
                plain(vm.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_PENS)));
    }

    @Test
    public void serverWithdrawalAndCountErrors() {
        SaleOrderEditViewModel vm = newEdit(3002L);
        vm.quantityText.setValue("60");
        vm.totalWeightText.setValue("7020");
        vm.unitPriceText.setValue("15.60");
        when(repo.createOrder(any(), anyString())).thenReturn(Single.error(new ApiException(42202,
                "ZM2610-019 在休药期内，可出栏日期 10月25日", null,
                JsonParser.parseString("{\"sellable_date\":\"2026-10-25\"}"), null, null)));
        vm.submit();
        assertTrue(vm.getInWithdrawal().getValue());
        assertFalse(vm.getCanSubmit().getValue());
        assertEquals("2026-10-25", vm.getSellableDate().getValue());
        assertEquals("ZM2610-019 在休药期内，可出栏日期 10月25日",
                vm.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_BATCH));

        SaleOrderEditViewModel vm2 = newEdit(3002L);
        vm2.quantityText.setValue("145");
        vm2.totalWeightText.setValue("17000");
        vm2.unitPriceText.setValue("15.60");
        when(repo.createOrder(any(), anyString())).thenReturn(Single.error(new ApiException(42205,
                "不能超过批次当前存栏 140 头", null, JsonParser.parseString("{\"current_count\":140}"), null, null)));
        vm2.submit();
        assertEquals(Integer.valueOf(140), vm2.getMaxQuantity().getValue());
        assertEquals("不能超过批次当前存栏 140 头", vm2.getFieldErrors().getValue().get(SaleOrderEditViewModel.F_QUANTITY));
    }

    @Test
    public void detailMenuAndBanner() throws IOException {
        SaleOrderDetailVO d9102 = TestRules.mock("order_9102.json", SaleOrderDetailVO.class);
        List<SaleOrderDetailViewModel.MenuSpec> m = invokeMenu(d9102);
        assertEquals(2, m.size());
        assertEquals(SaleOrderDetailViewModel.MENU_APPLY_CHANGE, m.get(0).id);
        assertTrue(m.get(0).enabled);
        assertEquals("需审核", m.get(0).note);
        SaleOrderDetailVO d9101 = TestRules.mock("order_9101.json", SaleOrderDetailVO.class);
        List<SaleOrderDetailViewModel.MenuSpec> m2 = invokeMenu(d9101);
        assertFalse(m2.get(0).enabled);
        assertEquals("审核中", m2.get(0).note);
        assertEquals("修改申请待审核：单价每千克 15.20 元改为 15.00 元，审核期间原单照常有效。",
                SaleOrderDetailViewModel.pendingText(d9101));
        SaleOrderDetailVO d9103 = TestRules.mock("order_9103.json", SaleOrderDetailVO.class);
        List<SaleOrderDetailViewModel.MenuSpec> m3 = invokeMenu(d9103);
        assertEquals(3, m3.size());
        assertEquals(SaleOrderDetailViewModel.MENU_VOID, m3.get(2).id);
        assertEquals("98 头 · 11,760 千克 · 每千克 16.00 元", plain(SaleOrderDetailViewModel.amountNote(d9102)));
        assertEquals("约定收款 10月20日 周二", SaleOrderDetailViewModel.dueText(d9102));
    }

    private List<SaleOrderDetailViewModel.MenuSpec> invokeMenu(SaleOrderDetailVO d) {
        when(repo.getOrderDetail(d.getId())).thenReturn(Single.just(d));
        com.zhimu.slmp.common.data.BaseDataRepository base = mock(com.zhimu.slmp.common.data.BaseDataRepository.class);
        when(base.getDicts(anyString(), any())).thenReturn(Single.just(java.util.Collections.emptyList()));
        SaleOrderDetailViewModel vm = new SaleOrderDetailViewModel(repo, base, farm);
        Map<String, Object> args = new HashMap<>();
        args.put(FinanceExtras.SALE_ORDER_ID, d.getId());
        vm.attachSavedState(new SavedStateHandle(args));
        return vm.getMenuItems().getValue();
    }

    @Test
    public void changeRequest_previewAndValidation() throws IOException {
        SaleOrderDetailVO d = TestRules.mock("order_9102.json", SaleOrderDetailVO.class);
        when(repo.getOrderDetail(9102L)).thenReturn(Single.just(d));
        when(repo.previewChange(eq(9102L), any())).thenReturn(Single.just(TestRules.mock("preview_price.json",
                SaleChangePreviewVO.class)));
        ChangeRepository change = mock(ChangeRepository.class);
        when(change.getForm(FinanceConst.BIZ_SALE_ORDER, 9102L)).thenReturn(Single.just(TestRules.mock(
                "change_form_sale_order.json", ChangeRequestFormVO.class)));
        when(change.submit(any(), anyString())).thenReturn(Single.just(com.zhimu.slmp.network.GsonFactory.get()
                .fromJson("{\"id\":9020}", com.zhimu.slmp.common.model.vo.ChangeRequestVO.class)));
        SaleChangeRequestViewModel vm = new SaleChangeRequestViewModel(repo, change, farm);
        vm.getCanSubmit().observeForever(v -> {
        });
        Map<String, Object> args = new HashMap<>();
        args.put(FinanceExtras.SALE_ORDER_ID, 9102L);
        vm.attachSavedState(new SavedStateHandle(args));
        vm.newUnitPriceText.setValue("16.00");
        vm.submit();
        assertEquals("新单价与原单价相同，无需修改", vm.getFieldErrors().getValue().get(SaleChangeRequestViewModel.F_PRICE));
        assertEquals("请填写修改原因", vm.getFieldErrors().getValue().get(SaleChangeRequestViewModel.F_REASON));
        vm.newUnitPriceText.setValue("15.50");
        rules.time.advanceTimeBy(300, TimeUnit.MILLISECONDS);
        SaleChangePreviewVO p = vm.getPreview().getValue();
        assertNotNull(p);
        assertEquals(0, p.getProfitAfter().compareTo(new BigDecimal("-2720")));
        assertTrue(p.getTurnToLoss());
        vm.newUnitPriceText.setValue("5");
        vm.reason.setValue("收购商按验级结果结算");
        rules.time.advanceTimeBy(300, TimeUnit.MILLISECONDS);
        vm.submit();
        // 预览返回 182,280 ≥ 已收 100,000，不拦截；本地预估在没有预览时才使用
        assertNull(vm.getFieldErrors().getValue().get(SaleChangeRequestViewModel.F_PRICE));
        ArgumentCaptor<com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest> sub =
                ArgumentCaptor.forClass(com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest.class);
        verify(change).submit(sub.capture(), anyString());
        assertEquals("SALE_ORDER", sub.getValue().getBizType());
        assertEquals("unit_price", sub.getValue().getFieldCode());
        assertEquals("5", sub.getValue().getNewValue());
        assertEquals("3", sub.getValue().getRecordVersion());
        assertEquals("已提交修改申请，等待管理员审核", vm.getFinishWithResult().getValue());
        vm.setMode(FinanceConst.MODE_QUANTITY);
        vm.newQuantityText.setValue("96");
        vm.newTotalWeightText.setValue("11520");
        java.lang.reflect.Method nv;
        try {
            nv = SaleChangeRequestViewModel.class.getDeclaredMethod("newValue");
            nv.setAccessible(true);
            assertEquals("{\"quantity\":96,\"total_weight\":11520}", nv.invoke(vm));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    public void receivableRemindText() throws IOException {
        ReceivableVO p = TestRules.mock("receivables_all.json", ReceivableVO.class);
        assertEquals("3 张出栏单未收齐，其中 1 张已逾期，未收 68,448 元",
                plain(ReceivableListViewModel.summaryNote(p.getSummary())));
        java.lang.reflect.Method m;
        try {
            m = ReceivableListViewModel.class.getDeclaredMethod("remindText", ReceivableVO.class);
            m.setAccessible(true);
            assertEquals("约定收款日前 1 天提醒你和场长，逾期后每 3 天提醒一次。没有填写约定收款日期的，按出栏后第 7 天计算。",
                    m.invoke(null, p));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        verify(repo, never()).addReceipt(anyLong(), any(), anyString());
    }
}
