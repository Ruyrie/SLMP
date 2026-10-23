package com.zhimu.slmp.finance.util;

import static com.zhimu.slmp.finance.TestRules.plain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 金额预估与显示格式：均重一位小数、销售金额 HALF_UP 到元、环比与涨跌文字和含义、约定收款标签、收款表单校验与提示。
 */
public class FinanceCalcTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    @Test
    public void saleAmount_byWeightAndHead() {
        // 图 10-9：60 头、7,020 千克、每千克 15.60 元 → 均重 117.0 千克、销售金额 109,512 元
        assertEquals(new BigDecimal("117.0"), SaleAmountCalculator.avgWeight(new BigDecimal("7020"), 60));
        assertEquals(0, SaleAmountCalculator.saleAmount(FinanceConst.BY_WEIGHT, 60, new BigDecimal("7020"),
                new BigDecimal("15.60")).compareTo(new BigDecimal("109512")));
        // 按头：98 头 × 1,920.00 元
        assertEquals(0, SaleAmountCalculator.saleAmount(FinanceConst.BY_HEAD, 98, null, new BigDecimal("1920"))
                .compareTo(new BigDecimal("188160")));
        // HALF_UP 到元：21,948 × 14.80 = 324,830.4 → 324,830；2.5 → 3
        assertEquals(0, SaleAmountCalculator.saleAmount(FinanceConst.BY_WEIGHT, 186, new BigDecimal("21948"),
                new BigDecimal("14.80")).compareTo(new BigDecimal("324830")));
        assertEquals(0, SaleAmountCalculator.saleAmount(FinanceConst.BY_WEIGHT, 1, new BigDecimal("0.5"),
                new BigDecimal("5")).compareTo(new BigDecimal("3")));
        // 字段未填完为 null（底部显示“—”）
        assertNull(SaleAmountCalculator.saleAmount(FinanceConst.BY_WEIGHT, 60, null, new BigDecimal("15.6")));
        assertNull(SaleAmountCalculator.saleAmount(FinanceConst.BY_HEAD, null, new BigDecimal("7020"),
                new BigDecimal("15.6")));
        assertNull(SaleAmountCalculator.avgWeight(new BigDecimal("7020"), 0));
        assertEquals(Integer.valueOf(90), SaleAmountCalculator.remaining(150, 60));
        assertEquals(Integer.valueOf(0), SaleAmountCalculator.remaining(150, 200));
        assertEquals(new BigDecimal("3200.00"), SaleAmountCalculator.parseDecimal("3,200.00"));
    }

    @Test
    public void momAndToneByMeaning() {
        assertEquals("较上月同期 +32.1%", plain(FinanceFormat.mom("较上月同期", new BigDecimal("0.321"), true)));
        assertEquals("环比持平", FinanceFormat.mom("环比", BigDecimal.ZERO, true));
        assertEquals("环比 -16.0%", plain(FinanceFormat.mom("环比", new BigDecimal("-0.16"), true)));
        assertEquals("无法比较", FinanceFormat.mom("环比", null, false));
        // 收入上升有利，支出上升不利
        assertEquals(FinanceFormat.TONE_GOOD, FinanceFormat.tone(new BigDecimal("0.111"), true, true));
        assertEquals(FinanceFormat.TONE_BAD, FinanceFormat.tone(new BigDecimal("0.064"), true, false));
        assertEquals(FinanceFormat.TONE_GOOD, FinanceFormat.tone(new BigDecimal("-0.16"), true, false));
        assertEquals(FinanceFormat.TONE_NONE, FinanceFormat.tone(null, false, true));
        // 出栏价上涨有利、种苗价上涨不利
        assertEquals("涨 4.0%", FinanceFormat.priceChange(new BigDecimal("0.04"), FinanceConst.UP));
        assertEquals("跌 2.0%", FinanceFormat.priceChange(new BigDecimal("0.02"), FinanceConst.DOWN));
        assertEquals("持平", FinanceFormat.priceChange(BigDecimal.ZERO, FinanceConst.FLAT));
        assertEquals(FinanceFormat.TONE_GOOD, FinanceFormat.priceTone(new BigDecimal("0.04"), FinanceConst.UP, true));
        assertEquals(FinanceFormat.TONE_BAD, FinanceFormat.priceTone(new BigDecimal("0.04"), FinanceConst.UP, false));
    }

    @Test
    public void amountsAndMonths() {
        assertEquals("-3,200 元", plain(FinanceFormat.signedRecordAmount(FinanceConst.EXPENSE, new BigDecimal("3200.00"))));
        assertEquals("+6,000 元", plain(FinanceFormat.signedRecordAmount(FinanceConst.INCOME, new BigDecimal("6000"))));
        assertEquals("+3,160", FinanceFormat.signed(new BigDecimal("3160")));
        assertEquals("-2,720", plain(FinanceFormat.signed(new BigDecimal("-2720"))));
        assertEquals("87,600 元", plain(FinanceFormat.yuan(new BigDecimal("87600"))));
        assertEquals("2026-10", FinanceFormat.currentMonth());
        assertEquals("10月", FinanceFormat.monthLabel("2026-10"));
        assertEquals("2025年12月", FinanceFormat.monthLabel("2025-12"));
        assertEquals("2026-09", FinanceFormat.shiftMonth("2026-10", -1));
        assertEquals(true, FinanceFormat.isBeforeCurrentMonth("2026-09"));
        assertEquals(false, FinanceFormat.isBeforeCurrentMonth("2026-10"));
        assertEquals(531, FinanceFormat.progress(new BigDecimal("100000"), new BigDecimal("188160")));
    }

    @Test
    public void dueTags() throws IOException {
        SaleOrderItemVO overdue = TestRules.mock("order_9101.json", SaleOrderItemVO.class);
        assertEquals("已逾期 4 天", FinanceFormat.dueTag(overdue));
        SaleOrderItemVO soon = TestRules.mock("order_9102.json", SaleOrderItemVO.class);
        assertEquals("明天到期", FinanceFormat.dueTag(soon));
        SaleOrderItemVO later = TestRules.mock("order_9103.json", SaleOrderItemVO.class);
        assertNull(FinanceFormat.dueTag(later));
        assertEquals("98 头 · 11,760 千克", plain(FinanceFormat.quantityWeight(soon)));
    }

    @Test
    public void receiptForm_defaultsValidationAndTexts() throws IOException {
        SaleOrderDetailVO d = TestRules.mock("order_9102.json", SaleOrderDetailVO.class);
        ReceiptFormDelegate form = new ReceiptFormDelegate();
        form.open(ReceiptFormDelegate.Target.of(d), FinanceConst.PAY_WECHAT);
        assertEquals("88160", form.amountText.getValue());
        assertEquals("2026-10-19", form.receiptDate.getValue());
        assertEquals(FinanceConst.PAY_WECHAT, form.paymentMethod.getValue());
        assertEquals("ZM2609-012 · 销售金额 188,160 元 · 已收 100,000 元", plain(form.summaryText()));
        assertEquals("最多 88,160 元，收款合计不能超过销售金额 188,160 元", plain(form.helperText()));

        form.amountText.setValue("88160.01");
        assertNull(form.validate());
        Map<String, String> e = form.getErrors().getValue();
        assertEquals("不能超过未收金额 88,160 元", plain(e.get(ReceiptFormDelegate.F_AMOUNT)));
        form.amountText.setValue("");
        form.validate();
        assertEquals("请输入收款金额", form.getErrors().getValue().get(ReceiptFormDelegate.F_AMOUNT));
        form.amountText.setValue("0");
        form.validate();
        assertEquals("收款金额必须大于 0", form.getErrors().getValue().get(ReceiptFormDelegate.F_AMOUNT));
        form.amountText.setValue("40000");
        form.receiptDate.setValue("2026-10-12");
        form.validate();
        assertEquals("收款日期不能早于出栏日期 10月13日", form.getErrors().getValue().get(ReceiptFormDelegate.F_DATE));
        form.receiptDate.setValue("2026-10-20");
        form.validate();
        assertEquals("收款日期不能晚于今天", form.getErrors().getValue().get(ReceiptFormDelegate.F_DATE));
        form.receiptDate.setValue("2026-10-19");
        assertEquals(0, form.validate().getAmount().compareTo(new BigDecimal("40000")));

        SaleOrderDetailVO partial = TestRules.mock("receipt_9102_partial.json", SaleOrderDetailVO.class);
        assertEquals("已登记收款 40,000 元，还有 48,160 元未收",
                plain(ReceiptFormDelegate.successText(new BigDecimal("40000"), partial)));
        SaleOrderDetailVO paid = TestRules.mock("receipt_9102_paid.json", SaleOrderDetailVO.class);
        assertEquals("已登记收款 88,160 元，出栏单已收齐",
                plain(ReceiptFormDelegate.successText(new BigDecimal("88160"), paid)));
    }
}
