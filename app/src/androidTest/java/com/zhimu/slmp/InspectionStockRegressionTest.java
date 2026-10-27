package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.QtyVO;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.inspect.data.InspectApi;
import com.zhimu.slmp.inspect.model.vo.*;
import com.zhimu.slmp.inspect.ui.form.InspectItemGroupView;
import com.zhimu.slmp.inspect.ui.register.InspectRegisterActivity;
import com.zhimu.slmp.inspect.ui.today.InspectTodayActivity;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.stock.data.StockApi;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.*;
import com.zhimu.slmp.stock.ui.feedinbound.FeedInboundActivity;
import com.zhimu.slmp.stock.ui.feedstock.FeedStockActivity;
import com.zhimu.slmp.stock.ui.otherout.FeedOtherOutActivity;
import com.zhimu.slmp.stock.util.StockQtyFormatter;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import io.reactivex.rxjava3.core.Single;

/** Local QA only. API calls select/read fixtures; all inspection and stock writes use actual UI. */
@RunWith(AndroidJUnit4.class)
public class InspectionStockRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private final List<Activity> opened = new ArrayList<>();

    @Before public void loginToIsolatedService() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        core.sessionManager().saveToken(unwrap(core.retrofit().create(AccountApi.class).loginByPassword(
                new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-inspection-stock", "Pixel 9 Pro", "Android")))));
        core.farmContext().update(unwrap(core.retrofit().create(FarmApi.class).switchFarm(1001L)));
    }

    @After public void finishActivities() {
        if (instrumentation != null) instrumentation.runOnMainSync(() -> {
            for (Activity activity : opened) if (!activity.isFinishing()) activity.finish();
        });
    }

    @Test public void pendingPenInspectionIsSubmittedFromTodayAndVisibleAfterReturn() throws Exception {
        InspectApi api = core.retrofit().create(InspectApi.class);
        InspectionSummaryVO before = unwrap(api.getSummary(null));
        InspectionListItemVO selected = null;
        InspectionFormVO form = null;
        for (InspectionListItemVO row : unwrap(api.getDayList(before.getDate(), "PENDING", 1, 100)).getList()) {
            if (!row.isCanRegister()) continue;
            InspectionFormVO candidate = unwrap(api.getFormContext(row.getPenId(), "REGISTER", null, null));
            if (candidate.getAlreadyRegistered() == null && !candidate.sortedItems().isEmpty()) {
                selected = row; form = candidate; break;
            }
        }
        assertNotNull("需要今天尚未登记、在栏且有巡检模板的QA栏舍；不会转为修改已有巡检", selected);
        final long penId = selected.getPenId();
        final String penName = selected.getPenName();
        Map<Long, Long> expected = new LinkedHashMap<>();
        for (InspectionFormVO.TemplateItem item : form.sortedItems()) {
            for (InspectionFormVO.TemplateOption option : item.getOptions()) {
                if (!option.isAbnormal()) { expected.put(item.getItemId(), option.getOptionId()); break; }
            }
        }
        assertEquals("每项必须存在正常选项，避免制造异常上报样本", form.sortedItems().size(), expected.size());
        String remark = "QA日常巡检核验-" + System.currentTimeMillis();
        Activity today = open(InspectTodayActivity.class);
        scrollListTo("inspect_list", penName);
        onView(allOf(withId(com.zhimu.slmp.inspect.R.id.inspect_row_card), hasDescendant(withText(penName))))
                .perform(click());
        Activity editor = waitActivity(InspectRegisterActivity.class);
        waitUi(() -> {
            ViewGroup items = editor.findViewById(com.zhimu.slmp.inspect.R.id.inspect_items);
            return items != null && items.getChildCount() == expected.size();
        }, "巡检模板未加载");
        // The location choice is optional; do not inject a fabricated on-site location.
        dismissOptionalLocationPermission();
        onView(allOf(withId(com.zhimu.slmp.inspect.R.id.inspect_submit_button), withText("提交")))
                .check(matches(isDisplayed())); // Refuse automatic MODIFY if another client registered first.
        for (Map.Entry<Long, Long> entry : expected.entrySet()) {
            onView(allOf(withTagValue(is(entry.getValue())), isDescendantOfA(itemGroup(entry.getKey()))))
                    .perform(scrollTo(), click());
        }
        Matcher<View> remarkInput = allOf(isAssignableFrom(EditText.class),
                isDescendantOfA(withId(com.zhimu.slmp.inspect.R.id.inspect_remark_layout)));
        onView(remarkInput).perform(scrollTo(), replaceText(remark), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.inspect.R.id.inspect_submit_button)).perform(click());
        waitUi(editor::isFinishing, "巡检提交未成功返回，检查页面校验或服务端错误");
        waitActivity(InspectTodayActivity.class);
        InspectionListItemVO saved = null;
        for (InspectionListItemVO row : unwrap(api.getDayList(before.getDate(), "ALL", 1, 100)).getList()) {
            if (row.getPenId() == penId) saved = row;
        }
        assertNotNull(saved);
        assertTrue("真实接口没有把栏舍标为已巡", saved.isDone());
        InspectionDetailVO detail = unwrap(api.getDetail(saved.getInspectionId()));
        assertEquals(remark, detail.getRemark());
        assertFalse(detail.isAbnormal());
        assertEquals(expected.size(), detail.getItemResults().size());
        for (InspectionDetailVO.ItemResult item : detail.getItemResults())
            assertEquals(expected.get(item.getItemId()), Long.valueOf(item.getOptionId()));
        InspectionSummaryVO after = unwrap(api.getSummary(before.getDate()));
        assertEquals(before.getDoneCount() + 1, after.getDoneCount());
        assertEquals(before.getPendingCount() - 1, after.getPendingCount());
        scrollListTo("inspect_list", penName);
        waitUi(() -> hasText(findCard(today.findViewById(com.zhimu.slmp.inspect.R.id.inspect_list),
                com.zhimu.slmp.inspect.R.id.inspect_row_card, penName), "已巡"), "提交后今日巡检列表未更新为已巡");
        // 成功提示条会覆盖底部的已巡卡片；等它自然消失再实际点击，不能让触摸落到提示条上。
        waitUi(() -> {
            View snackbar = today.findViewById(com.google.android.material.R.id.snackbar_text);
            return snackbar == null || !snackbar.isShown();
        }, "登记成功提示未消失，不能点击其下方的巡检记录");
        scrollListTo("inspect_list", penName);
        screenshot("inspection-returned-to-today");
        onView(allOf(withId(com.zhimu.slmp.inspect.R.id.inspect_row_card), hasDescendant(withText(penName))))
                .perform(click());
        Activity reopened = waitActivity(InspectRegisterActivity.class);
        waitUi(() -> hasText(reopened.getWindow().getDecorView(), remark), "重新打开巡检没有显示刚保存的说明");
        onView(remarkInput).perform(scrollTo()).check(matches(withText(remark)));
        screenshot("inspection-reopened-record");
        JsonObject result = new JsonObject();
        result.addProperty("pen_id", penId); result.addProperty("inspection_id", saved.getInspectionId());
        result.addProperty("date", before.getDate()); result.addProperty("remark", remark);
        result.addProperty("pending_before", before.getPendingCount()); result.addProperty("pending_after", after.getPendingCount());
        evidence("inspection", result);
    }

    @Test public void feedInboundAndSupplierReturnRefreshInventoryAndPersistTheirFlows() throws Exception {
        StockApi api = core.retrofit().create(StockApi.class);
        FeedOptionVO selected = null;
        for (FeedOptionVO item : unwrap(api.listFeedOptions("INBOUND", null))) {
            if (item.selectable && item.stock != null && item.stock.value != null && item.stock.value.signum() > 0) {
                selected = item; break;
            }
        }
        assertNotNull("需要QA养殖场有可入库且已有库存的饲料档案", selected);
        final long feedId = selected.feedId;
        final String feedName = selected.name;
        BigDecimal initial = feedStock(api, feedId).value;
        Set<Long> beforeFlows = flowIds(api, feedId);
        String supplier = "QA供应商-" + System.currentTimeMillis();
        String returnRemark = "QA入库退回核验-" + System.currentTimeMillis();
        Activity stock = open(FeedStockActivity.class);
        waitUi(() -> hasText(stock.findViewById(com.zhimu.slmp.stock.R.id.stock_feed_list), feedName), "库存页未显示饲料");
        onView(withId(com.zhimu.slmp.stock.R.id.stock_fab)).perform(click());
        Activity inbound = waitActivity(FeedInboundActivity.class);
        chooseFeed(inbound, feedName);
        onView(withId(com.zhimu.slmp.stock.R.id.stock_qty_input)).perform(scrollTo(), replaceText("12"), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.stock.R.id.stock_price_input)).perform(scrollTo(), replaceText("3.50"), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.stock.R.id.stock_supplier_input)).perform(scrollTo(), replaceText(supplier), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.base.R.id.base_bar_primary)).perform(click());
        waitUi(inbound::isFinishing, "确认入库未成功返回，检查表单或请求错误");
        waitActivity(FeedStockActivity.class);
        QtyVO afterInbound = feedStock(api, feedId);
        assertEquals(0, initial.add(new BigDecimal("12")).compareTo(afterInbound.value));
        assertStockDisplayed(stock, feedName, afterInbound);
        StockFlowItemVO inFlow = newFlow(api, feedId, beforeFlows, StockEnums.FLOW_INBOUND);
        StockInboundVO inRecord = unwrap(api.getInbound(inFlow.bizId));
        assertEquals(Long.valueOf(feedId), inRecord.feedId);
        assertEquals(supplier, inRecord.supplierName);
        assertEquals(0, new BigDecimal("12").compareTo(inRecord.qty.value));
        assertEquals(0, new BigDecimal("42.00").compareTo(inRecord.amount));
        assertTrue("默认同步记账应生成实际支出记录", inRecord.syncFinance && inRecord.financeRecordId != null);
        com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO expense = unwrap(core.retrofit()
                .create(com.zhimu.slmp.finance.data.FinanceApi.class).getRecord(inRecord.financeRecordId));
        assertEquals("EXPENSE", expense.getRecordType());
        assertEquals("INBOUND", expense.getSourceType());
        assertEquals("NORMAL", expense.getStatus());
        assertEquals(0, inRecord.amount.compareTo(expense.getAmount()));
        assertNotNull(expense.getSourceTarget());
        assertEquals(Long.valueOf(inRecord.inboundId), expense.getSourceTarget().getBizId());
        screenshot("stock-after-inbound");

        Set<Long> beforeOutFlows = flowIds(api, feedId);
        onView(withId(com.zhimu.slmp.stock.R.id.stock_menu_more)).perform(click());
        onView(withText(context.getString(com.zhimu.slmp.stock.R.string.stock_other_out_menu))).perform(click());
        Activity outbound = waitActivity(FeedOtherOutActivity.class);
        chooseFeed(outbound, feedName);
        onView(withId(com.zhimu.slmp.stock.R.id.stock_qty_input)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard());
        onView(allOf(withText("退回供应商"), isDescendantOfA(withId(com.zhimu.slmp.stock.R.id.stock_dest_chips))))
                .perform(scrollTo(), click());
        onView(withId(com.zhimu.slmp.stock.R.id.stock_remark_input)).perform(scrollTo(), replaceText(returnRemark), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.base.R.id.base_bar_primary)).perform(click());
        waitUi(outbound::isFinishing, "确认出库未成功返回，检查校验或库存版本冲突");
        waitActivity(FeedStockActivity.class);
        QtyVO afterOutbound = feedStock(api, feedId);
        assertEquals(0, initial.add(new BigDecimal("7")).compareTo(afterOutbound.value));
        assertStockDisplayed(stock, feedName, afterOutbound);
        StockFlowItemVO outFlow = newFlow(api, feedId, beforeOutFlows, StockEnums.FLOW_OTHER_OUT);
        StockOutboundVO outRecord = unwrap(api.getOutbound(outFlow.bizId));
        assertEquals(Long.valueOf(feedId), outRecord.feedId);
        assertEquals("RETURN_SUPPLIER", outRecord.destination);
        assertEquals(returnRemark, outRecord.remark);
        assertEquals(0, new BigDecimal("5").compareTo(outRecord.qty.value));
        assertEquals(0, afterOutbound.value.compareTo(outFlow.stockQtyAfter));
        screenshot("stock-after-supplier-return");
        JsonObject result = new JsonObject();
        result.addProperty("feed_id", feedId); result.addProperty("inbound_id", inRecord.inboundId);
        result.addProperty("outbound_id", outRecord.outboundId); result.addProperty("finance_record_id", inRecord.financeRecordId);
        result.addProperty("stock_before", initial); result.addProperty("stock_after_inbound", afterInbound.value);
        result.addProperty("stock_after_outbound", afterOutbound.value);
        evidence("stock_inbound_outbound", result);
    }

    private void chooseFeed(Activity form, String name) throws Exception {
        waitUi(() -> {
            View input = form.findViewById(com.zhimu.slmp.stock.R.id.stock_feed_input);
            return input != null && input.isShown() && input.isEnabled();
        }, "物资表单未加载");
        onView(withId(com.zhimu.slmp.stock.R.id.stock_feed_input)).perform(click());
        scrollListTo("stock_picker_list", name);
        onView(allOf(withId(com.zhimu.slmp.stock.R.id.stock_row_title), withText(name))).perform(click());
        onView(withId(com.zhimu.slmp.stock.R.id.stock_feed_input)).check(matches(withText(name)));
    }

    private void scrollListTo(String id, String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo list = findNode(root, node -> node.getViewIdResourceName() != null
                    && node.getViewIdResourceName().endsWith(":id/" + id) && node.isVisibleToUser());
            if (list != null) {
                AccessibilityNodeInfo target = findNode(list, node -> node.isVisibleToUser()
                        && text.contentEquals(node.getText() == null ? "" : node.getText()));
                if (target != null) {
                    AccessibilityNodeInfo clickTarget = target;
                    for (AccessibilityNodeInfo parent = target.getParent(); parent != null; parent = parent.getParent()) {
                        String resource = parent.getViewIdResourceName();
                        if (resource != null && resource.endsWith(":id/" + id)) break;
                        if (parent.isClickable()) { clickTarget = parent; break; }
                    }
                    clickTarget.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                    instrumentation.waitForIdleSync();
                    return;
                }
                list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }
            SystemClock.sleep(200);
        }
        fail("清单未加载或没有目标 " + id + " / " + text);
    }

    private void dismissOptionalLocationPermission() {
        long deadline = SystemClock.uptimeMillis() + 500;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo deny = findNode(instrumentation.getUiAutomation().getRootInActiveWindow(),
                    node -> node.getViewIdResourceName() != null && node.isVisibleToUser()
                            && node.getViewIdResourceName().endsWith(":id/permission_deny_button"));
            if (deny != null) {
                assertTrue("定位权限弹窗未响应拒绝操作", deny.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                instrumentation.waitForIdleSync();
                return;
            }
            SystemClock.sleep(100);
        }
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node,
                                           java.util.function.Predicate<AccessibilityNodeInfo> predicate) {
        if (node == null) return null;
        if (predicate.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), predicate);
            if (found != null) return found;
        }
        return null;
    }

    private QtyVO feedStock(StockApi api, long id) {
        for (FeedStockItemVO feed : unwrap(api.pageFeeds(1, 100)).getList()) if (feed.feedId == id) return feed.stock;
        throw new AssertionError("库存清单未返回目标饲料 " + id);
    }

    private Set<Long> flowIds(StockApi api, long id) {
        Set<Long> ids = new HashSet<>();
        for (StockFlowItemVO flow : unwrap(api.pageFeedFlows(id, "ALL", 1, 100)).getList()) ids.add(flow.flowId);
        return ids;
    }

    private StockFlowItemVO newFlow(StockApi api, long id, Set<Long> before, String kind) {
        List<StockFlowItemVO> found = new ArrayList<>();
        for (StockFlowItemVO flow : unwrap(api.pageFeedFlows(id, "ALL", 1, 100)).getList())
            if (!before.contains(flow.flowId) && kind.equals(flow.flowType)) found.add(flow);
        assertEquals("一次UI提交必须生成且只生成一条对应库存流水", 1, found.size());
        assertNotNull(found.get(0).bizId);
        return found.get(0);
    }

    private void assertStockDisplayed(Activity activity, String name, QtyVO quantity) {
        String expected = StockQtyFormatter.format(quantity);
        waitUi(() -> {
            View card = findCard(activity.findViewById(com.zhimu.slmp.stock.R.id.stock_feed_list),
                    com.zhimu.slmp.stock.R.id.stock_feed_card, name);
            TextView label = card == null ? null : card.findViewById(com.zhimu.slmp.stock.R.id.stock_feed_stock);
            return label != null && expected.contentEquals(label.getText());
        }, "业务页返回后库存卡未刷新到 " + expected);
        onView(allOf(withId(com.zhimu.slmp.stock.R.id.stock_feed_stock),
                isDescendantOfA(allOf(withId(com.zhimu.slmp.stock.R.id.stock_feed_card), hasDescendant(withText(name))))))
                .perform(scrollTo()).check(matches(withText(expected)));
    }

    private static Matcher<View> itemGroup(long id) {
        return new TypeSafeMatcher<View>() {
            @Override protected boolean matchesSafely(View view) {
                return view instanceof InspectItemGroupView && ((InspectItemGroupView) view).getItemId() == id;
            }
            @Override public void describeTo(Description description) { description.appendText("inspection item " + id); }
        };
    }

    private View findCard(View view, int cardId, String text) {
        if (view == null) return null;
        if (view.getId() == cardId && hasText(view, text)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findCard(((ViewGroup) view).getChildAt(i), cardId, text);
            if (found != null) return found;
        }
        return null;
    }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }

    private Activity open(Class<? extends Activity> type) {
        Activity activity = instrumentation.startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(activity); return activity;
    }

    private Activity waitActivity(Class<? extends Activity> type) {
        AtomicReference<Activity> found = new AtomicReference<>();
        waitUi(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                if (type.isInstance(activity) && !activity.isFinishing()) found.set(activity);
            return found.get() != null;
        }, "未进入业务页面 " + type.getSimpleName());
        if (!opened.contains(found.get())) opened.add(found.get());
        return found.get();
    }

    private void waitUi(BooleanSupplier condition, String message) {
        long end = SystemClock.uptimeMillis() + 20000;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < end) {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail(message);
    }

    private <T> T unwrap(Single<ApiResponse<T>> response) { return response.compose(RxResult.<T>unwrap()).blockingGet(); }
    private File artifacts() {
        File directory = new File(context.getExternalFilesDir(null), "inspection-stock-workflow");
        assertTrue(directory.isDirectory() || directory.mkdirs()); return directory;
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(artifacts(), name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
    private void evidence(String key, JsonObject result) throws Exception {
        File file = new File(artifacts(), "evidence.json");
        JsonObject all = file.isFile() ? JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject()
                : new JsonObject();
        all.add(key, result);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(all.toString().getBytes(StandardCharsets.UTF_8)); }
    }
}
