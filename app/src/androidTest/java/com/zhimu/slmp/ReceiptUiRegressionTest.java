package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.finance.data.FinanceApi;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.ui.common.SaleOrderCardAdapter;
import com.zhimu.slmp.finance.ui.receivable.ReceivableListActivity;
import com.zhimu.slmp.finance.ui.saledetail.SaleOrderDetailActivity;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.network.RxResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Real receipt click -> real local API -> dismissed sheet -> one linked income.
 * Requires an explicitly supplied QA_RECEIPT_ buyer order; the write case additionally requires an unpaid balance.
 * Arguments: qaReceiptOrderId, qaFarmId (default 1001), qaReceiptEntry (detail or receivables).
 */
@RunWith(AndroidJUnit4.class)
public class ReceiptUiRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private FinanceApi api;
    private FragmentActivity activity;
    private SaleOrderDetailVO before;
    private String entryPoint;

    @Before public void requireIsolatedLocalOrder() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        CoreComponent core = LocalQaEnvironment.requireLocalCore(context);
        Bundle args = InstrumentationRegistry.getArguments();
        String id = args.getString("qaReceiptOrderId");
        assertNotNull("请先创建独立 QA_RECEIPT_ 收购商订单，并显式传入 qaReceiptOrderId", id);
        entryPoint = args.getString("qaReceiptEntry", "detail");
        assertTrue("qaReceiptEntry 仅允许 detail / receivables",
                "detail".equals(entryPoint) || "receivables".equals(entryPoint));
        AuthTokenVO token = core.retrofit().create(AccountApi.class).loginByPassword(new PasswordLoginRequest(
                "13800006620", "Zmt12345", new DeviceInfo("pixel9-receipt-qa", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        FarmContextVO farm = core.retrofit().create(FarmApi.class)
                .switchFarm(Long.parseLong(args.getString("qaFarmId", "1001")))
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
        core.farmContext().update(farm);
        api = core.retrofit().create(FinanceApi.class);
        before = api.getOrder(Long.parseLong(id)).compose(RxResult.<SaleOrderDetailVO>unwrap()).blockingGet();
        assertTrue("禁止对正常业务订单执行收款回归", before.getBuyerName() != null
                && before.getBuyerName().startsWith("QA_RECEIPT_"));
    }

    @After public void closeOnlyTestActivity() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
    }

    @Test public void confirmReceipt_closesSheetAndCreatesOneLinkedIncome() throws Exception {
        assertTrue("该订单已收齐，请新建隔离订单，不能重复写入", before.getUnpaidAmount().signum() > 0);
        assertTrue(before.getActions().isCanReceive());
        Class<? extends Activity> page = "detail".equals(entryPoint)
                ? SaleOrderDetailActivity.class : ReceivableListActivity.class;
        activity = (FragmentActivity) instrumentation.startActivitySync(new Intent(context, page)
                .putExtra(RouterPath.EXTRA_SALE_ORDER_ID, before.getId()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if ("detail".equals(entryPoint)) {
            awaitUi("出栏单详情加载完成", () -> {
                android.widget.TextView batch = activity.findViewById(com.zhimu.slmp.finance.R.id.finance_batch_no);
                return batch != null && before.getBatchNo().contentEquals(batch.getText());
            });
            onView(withText(com.zhimu.slmp.finance.R.string.finance_receipt_title)).perform(click());
        } else {
            scrollToReceiptOrder();
            onView(allOf(withText(com.zhimu.slmp.finance.R.string.finance_card_receive),
                    isDescendantOfA(allOf(withId(com.zhimu.slmp.finance.R.id.finance_order_card),
                            hasDescendant(withText(containsString(before.getBuyerName()))))))).perform(click());
        }
        awaitUi("登记收款弹层打开", () -> activity.getSupportFragmentManager()
                .findFragmentByTag("finance_receipt") != null);
        String amount = before.getUnpaidAmount().stripTrailingZeros().toPlainString();
        onView(withId(com.zhimu.slmp.finance.R.id.finance_receipt_amount)).check(matches(withText(amount)));
        onView(withId(com.zhimu.slmp.finance.R.id.finance_receipt_confirm)).check(matches(isDisplayed()));
        screenshot("receipt-" + entryPoint + "-before");
        // Deliberately keep the amount field/keyboard state from the real sheet; use a real button click.
        onView(withId(com.zhimu.slmp.finance.R.id.finance_receipt_confirm)).perform(click());
        awaitUi("确认收款成功后弹层必须真正关闭", () -> activity.getSupportFragmentManager()
                .findFragmentByTag("finance_receipt") == null);
        screenshot("receipt-" + entryPoint + "-after");

        SaleOrderDetailVO after = api.getOrder(before.getId()).compose(RxResult.<SaleOrderDetailVO>unwrap()).blockingGet();
        assertEquals("一次确认只能新增一笔收款", before.getReceipts().size() + 1, after.getReceipts().size());
        assertEquals(0, after.getReceivedAmount().compareTo(before.getReceivedAmount().add(before.getUnpaidAmount())));
        assertEquals(0, after.getUnpaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(FinanceConst.PAY_PAID, after.getPaymentStatus());
        assertFalse("收齐后不应继续提供收款操作", after.getActions().isCanReceive());
        Set<Long> oldIds = new HashSet<>();
        for (SaleOrderDetailVO.Receipt r : before.getReceipts()) oldIds.add(r.getId());
        SaleOrderDetailVO.Receipt saved = null;
        for (SaleOrderDetailVO.Receipt r : after.getReceipts()) if (!oldIds.contains(r.getId())) saved = r;
        assertNotNull(saved);
        assertEquals(0, saved.getAmount().compareTo(before.getUnpaidAmount()));
        assertNotNull("收款必须关联真实收入记录", saved.getFinanceRecordId());
        FinanceRecordDetailVO income = api.getRecord(saved.getFinanceRecordId())
                .compose(RxResult.<FinanceRecordDetailVO>unwrap()).blockingGet();
        assertEquals(FinanceConst.INCOME, income.getRecordType());
        assertEquals(0, saved.getAmount().compareTo(income.getAmount()));
        assertEquals(Long.valueOf(before.getBatchId()), income.getBatchId());
    }

    /** Read-only Web -> App check. Override qaExpectedReceiptAmounts/Id/IncomeId for another QA order. */
    @Test public void webReceiptIsReflectedInAppWithoutAnotherPayment() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String[] amounts = args.getString("qaExpectedReceiptAmounts", "10000,23920").split(",");
        long expectedReceiptId = Long.parseLong(args.getString("qaExpectedReceiptId", "210209"));
        long expectedIncomeId = Long.parseLong(args.getString("qaExpectedIncomeId", "210916"));
        List<BigDecimal> expected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String amount : amounts) {
            BigDecimal value = new BigDecimal(amount.trim());
            expected.add(value.stripTrailingZeros());
            total = total.add(value);
        }
        List<BigDecimal> actual = new ArrayList<>();
        SaleOrderDetailVO.Receipt webReceipt = null;
        for (SaleOrderDetailVO.Receipt receipt : before.getReceipts()) {
            actual.add(receipt.getAmount().stripTrailingZeros());
            if (receipt.getId() == expectedReceiptId) webReceipt = receipt;
        }
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals("Web保存的每笔金额应完整出现在App接口", expected, actual);
        assertEquals(0, total.compareTo(before.getReceivedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(before.getUnpaidAmount()));
        assertEquals(FinanceConst.PAY_PAID, before.getPaymentStatus());
        assertFalse("只读验收订单必须已经收齐", before.getActions().isCanReceive());
        assertNotNull("必须读到Web刚新增的真实收款", webReceipt);
        assertEquals(Long.valueOf(expectedIncomeId), webReceipt.getFinanceRecordId());
        FinanceRecordDetailVO income = api.getRecord(expectedIncomeId)
                .compose(RxResult.<FinanceRecordDetailVO>unwrap()).blockingGet();
        assertEquals(FinanceConst.INCOME, income.getRecordType());
        assertEquals(0, webReceipt.getAmount().compareTo(income.getAmount()));
        assertEquals(Long.valueOf(before.getBatchId()), income.getBatchId());

        activity = (FragmentActivity) instrumentation.startActivitySync(new Intent(context, SaleOrderDetailActivity.class)
                .putExtra(RouterPath.EXTRA_SALE_ORDER_ID, before.getId()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitUi("Web收款后App详情必须真实加载", () -> {
            android.widget.TextView batch = activity.findViewById(com.zhimu.slmp.finance.R.id.finance_batch_no);
            android.view.ViewGroup receipts = activity.findViewById(com.zhimu.slmp.finance.R.id.finance_receipts);
            return batch != null && before.getBatchNo().contentEquals(batch.getText())
                    && receipts != null && receipts.getChildCount() == expected.size();
        });
        onView(withId(com.zhimu.slmp.finance.R.id.finance_pay_tag)).check(matches(withText("已收款"))).check(matches(isDisplayed()));
        onView(withId(com.zhimu.slmp.finance.R.id.finance_received)).check(matches(withText(context.getString(
                com.zhimu.slmp.finance.R.string.finance_e07_received, FinanceFormat.yuan(total))))).check(matches(isDisplayed()));
        onView(withId(com.zhimu.slmp.finance.R.id.finance_unpaid)).check(matches(withText(context.getString(
                com.zhimu.slmp.finance.R.string.finance_e07_unpaid, FinanceFormat.yuan(BigDecimal.ZERO))))).check(matches(isDisplayed()));
        onView(withText(context.getString(com.zhimu.slmp.finance.R.string.finance_e07_receipts, expected.size())))
                .check(matches(isDisplayed()));
        for (BigDecimal amount : expected) {
            onView(allOf(withText(FinanceFormat.yuan(amount)), isDescendantOfA(withId(com.zhimu.slmp.finance.R.id.finance_receipts))))
                    .perform(scrollTo()).check(matches(isDisplayed()));
        }
        onView(withId(com.zhimu.slmp.finance.R.id.finance_receipts)).check(matches(hasDescendant(
                withText(com.zhimu.slmp.finance.R.string.finance_e07_receipt_income))));
        instrumentation.waitForIdleSync();
        try { instrumentation.getUiAutomation().waitForIdle(500, 5000); }
        catch (java.util.concurrent.TimeoutException ignored) { }
        screenshot("web-receipt-reflected-in-app");
        SaleOrderDetailVO after = api.getOrder(before.getId()).compose(RxResult.<SaleOrderDetailVO>unwrap()).blockingGet();
        assertEquals("只读核对不能新增收款", before.getReceipts().size(), after.getReceipts().size());
        assertEquals(0, before.getReceivedAmount().compareTo(after.getReceivedAmount()));
    }

    private void scrollToReceiptOrder() {
        awaitUi("待收款列表显示指定隔离订单", () -> {
            RecyclerView list = activity.findViewById(com.zhimu.slmp.finance.R.id.finance_list);
            if (list == null) return false;
            int position = orderPosition(list.getAdapter(), before.getId());
            if (position >= 0) {
                ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager())
                        .scrollToPositionWithOffset(position, 0);
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                return holder != null && holder.itemView.isShown();
            }
            list.scrollBy(0, list.getHeight());
            return false;
        });
        instrumentation.waitForIdleSync();
    }

    private static int orderPosition(RecyclerView.Adapter<?> adapter, long orderId) {
        if (adapter instanceof SaleOrderCardAdapter) return ((SaleOrderCardAdapter) adapter).positionOf(orderId);
        if (adapter instanceof ConcatAdapter) {
            int offset = 0;
            // Receivables nests the PagingDataAdapter's load-state footer inside a second ConcatAdapter.
            for (RecyclerView.Adapter<?> child : ((ConcatAdapter) adapter).getAdapters()) {
                int position = orderPosition(child, orderId);
                if (position >= 0) return offset + position;
                offset += child.getItemCount();
            }
        }
        return -1;
    }

    private void awaitUi(String message, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail(message);
    }

    private void screenshot(String name) throws Exception {
        Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            image.recycle();
        }
    }
}
