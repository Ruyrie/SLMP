package com.zhimu.slmp.finance.ui;

import static com.zhimu.slmp.finance.TestRules.plain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.data.SettlementRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.FinanceRecordRequest;
import com.zhimu.slmp.finance.model.vo.DuplicateCheckVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionPageVO;
import com.zhimu.slmp.finance.model.vo.SubmissionVO;
import com.zhimu.slmp.finance.ui.record.FinanceRecordEditViewModel;
import com.zhimu.slmp.media.upload.UploadManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * E03 记一笔：校验文案、canSubmit、疑似重复防抖 500 毫秒（只对 FEED、DRUG、SALE）、保存提示、
 * 幂等键沿用与作废、提交超时经 API-E43 确认的两种结果。
 */
public class FinanceRecordEditViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private FinanceRepository repo;
    private FinanceRecordEditViewModel vm;

    @Before
    public void setUp() throws IOException {
        repo = mock(FinanceRepository.class);
        SettlementRepository settlement = mock(SettlementRepository.class);
        FarmContext farm = mock(FarmContext.class);
        UploadManager.Factory uploads = mock(UploadManager.Factory.class);
        UploadManager photos = mock(UploadManager.class);
        when(uploads.create(any(), anyString(), anyString(), anyInt())).thenReturn(photos);
        when(photos.getFileIds()).thenReturn(Collections.emptyList());
        when(photos.current()).thenReturn(Collections.emptyList());
        when(farm.farmIdOrZero()).thenReturn(1001L);
        List<FinanceCategoryVO> expense = TestRules.mock("categories_expense.json",
                new TypeToken<List<FinanceCategoryVO>>() {
                }.getType());
        List<FinanceCategoryVO> income = TestRules.mock("categories_income.json",
                new TypeToken<List<FinanceCategoryVO>>() {
                }.getType());
        when(repo.getCategories(FinanceConst.EXPENSE)).thenReturn(Single.just(expense));
        when(repo.getCategories(FinanceConst.INCOME)).thenReturn(Single.just(income));
        when(settlement.getOptions(isNull(), anyInt(), anyInt())).thenReturn(Single.just(
                TestRules.mock("settlement_options.json", SettlementOptionPageVO.class)));
        when(repo.checkDuplicate(anyString(), anyLong(), any(), anyString(), any())).thenReturn(Single.just(
                TestRules.mock("dup_hit.json", DuplicateCheckVO.class)));
        vm = new FinanceRecordEditViewModel(repo, settlement, farm, uploads);
        vm.getCanSubmit().observeForever(v -> {
        });
        vm.attachSavedState(new SavedStateHandle(new HashMap<>()));
    }

    @Test
    public void validationMessages() {
        vm.amountText.setValue("");
        vm.save();
        Map<String, String> e = vm.getFieldErrors().getValue();
        assertEquals("请输入金额", e.get(FinanceRecordEditViewModel.F_AMOUNT));
        assertEquals("请选择分类", e.get(FinanceRecordEditViewModel.F_CATEGORY));
        vm.amountText.setValue("0");
        vm.save();
        assertEquals("金额必须大于 0", vm.getFieldErrors().getValue().get(FinanceRecordEditViewModel.F_AMOUNT));
        vm.amountText.setValue("12.345");
        vm.save();
        assertEquals("金额最多保留 2 位小数", vm.getFieldErrors().getValue().get(FinanceRecordEditViewModel.F_AMOUNT));
        vm.setRecordDate("2026-10-20");
        vm.save();
        assertEquals("日期不能晚于今天", vm.getFieldErrors().getValue().get(FinanceRecordEditViewModel.F_DATE));
        vm.switchType(FinanceConst.INCOME);
        vm.note.setValue("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一");
        vm.save();
        assertEquals("说明不能超过 30 字", vm.getFieldErrors().getValue().get(FinanceRecordEditViewModel.F_NOTE));
        verify(repo, never()).createRecord(any(), anyString());
    }

    @Test
    public void canSubmitAndSubcategoryRules() {
        assertFalse(vm.getCanSubmit().getValue());
        vm.amountText.setValue("600");
        vm.selectCategory(104L);
        assertTrue(vm.getCanSubmit().getValue());
        assertEquals(1, vm.getSubcategories().getValue().size());
        assertEquals("饲料采购下已有“玉米”", FinanceRecordEditViewModel.validateSubName("饲料采购", "玉米",
                vm.getSubcategories().getValue().isEmpty() ? null : java.util.Arrays.asList(
                        TestRules.mockSub(1012, "玉米"))));
        assertEquals("子类名称不能超过 10 个字", FinanceRecordEditViewModel.validateSubName("人工", "一二三四五六七八九十一",
                null));
        assertEquals("请输入子类名称", FinanceRecordEditViewModel.validateSubName("人工", "", null));
        // 切换收入：分类、子类清空，收入没有子类
        vm.switchType(FinanceConst.INCOME);
        assertNull(vm.categoryId.getValue());
        assertTrue(vm.getSubcategories().getValue().isEmpty());
        assertEquals("关联后计入批次收入", vm.getBatchHelper().getValue());
    }

    @Test
    public void duplicateCheck_debouncedAndOnlyForFeedDrugSale() {
        vm.amountText.setValue("3200");
        vm.selectCategory(104L); // 人工：不查
        rules.time.advanceTimeBy(600, TimeUnit.MILLISECONDS);
        verify(repo, never()).checkDuplicate(anyString(), anyLong(), any(), anyString(), any());
        vm.selectCategory(101L); // 饲料采购
        vm.amountText.setValue("3200.0");
        vm.amountText.setValue("3200.00");
        rules.time.advanceTimeBy(400, TimeUnit.MILLISECONDS);
        verify(repo, never()).checkDuplicate(anyString(), anyLong(), any(), anyString(), any());
        rules.time.advanceTimeBy(200, TimeUnit.MILLISECONDS);
        verify(repo, times(1)).checkDuplicate(eq(FinanceConst.EXPENSE), eq(101L), any(), eq("2026-10-19"), isNull());
        DuplicateCheckVO d = vm.getDuplicate().getValue();
        assertNotNull(d);
        assertEquals("今天已有一条金额相同的自动记录，饲料采购 3,200 元，来自饲料入库单。确认不是同一笔再保存。",
                plain(FinanceRecordEditViewModel.duplicateText(d)));
    }

    @Test
    public void save_successAndIdemKey() throws IOException {
        FinanceRecordVO created = TestRules.mock("record_created.json", FinanceRecordVO.class);
        when(repo.createRecord(any(), anyString())).thenReturn(Single.just(created));
        vm.amountText.setValue("3,200.00");
        vm.selectCategory(101L);
        vm.selectSubcategory(1011L);
        vm.save();
        ArgumentCaptor<FinanceRecordRequest> req = ArgumentCaptor.forClass(FinanceRecordRequest.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(repo).createRecord(req.capture(), key.capture());
        assertEquals(FinanceConst.EXPENSE, req.getValue().getRecordType());
        assertEquals(0, req.getValue().getAmount().compareTo(new BigDecimal("3200")));
        assertEquals(Long.valueOf(1011), req.getValue().getSubcategoryId());
        assertNull(req.getValue().getNote());
        assertNull(req.getValue().getVersion());
        assertNotNull(key.getValue());
        assertEquals("已记一笔支出 3,200 元", plain(vm.getFinishWithResult().getValue()));
        assertFalse(vm.getSubmitting().getValue());
    }

    @Test
    public void timeout_confirmsWithE43_keepsKeyWhenNotSaved() {
        when(repo.createRecord(any(), anyString())).thenReturn(Single.error(new SocketTimeoutException("timeout")));
        when(repo.getSubmission(anyString())).thenReturn(Single.just(
                com.zhimu.slmp.network.GsonFactory.get().fromJson("{\"saved\":false}", SubmissionVO.class)));
        vm.amountText.setValue("600");
        vm.selectCategory(104L);
        vm.save();
        assertEquals("提交超时，请重试", vm.getMessage().getValue().getText());
        vm.save();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repo, times(2)).createRecord(any(), keys.capture());
        assertEquals("同一份内容重试沿用幂等键", keys.getAllValues().get(0), keys.getAllValues().get(1));
        verify(repo, times(2)).getSubmission(keys.getAllValues().get(0));
        // 改动内容后作废旧键
        vm.amountText.setValue("700");
        vm.save();
        verify(repo, times(3)).createRecord(any(), keys.capture());
        assertFalse(keys.getAllValues().get(0).equals(keys.getAllValues().get(keys.getAllValues().size() - 1)));
    }

    @Test
    public void timeout_savedIsSuccess() {
        when(repo.createRecord(any(), anyString())).thenReturn(Single.error(new SocketTimeoutException("timeout")));
        when(repo.getSubmission(anyString())).thenReturn(Single.just(
                com.zhimu.slmp.network.GsonFactory.get().fromJson("{\"saved\":true}", SubmissionVO.class)));
        vm.switchType(FinanceConst.INCOME);
        vm.amountText.setValue("6000");
        vm.selectCategory(203L);
        vm.note.setValue("能繁母猪补贴");
        vm.save();
        assertEquals("已记一笔收入 6,000 元", plain(vm.getFinishWithResult().getValue()));
    }
}
