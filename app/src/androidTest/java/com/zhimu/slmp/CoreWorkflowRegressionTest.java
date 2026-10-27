package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.feed.data.FeedApi;
import com.zhimu.slmp.feed.data.WeighApi;
import com.zhimu.slmp.feed.model.vo.*;
import com.zhimu.slmp.feed.ui.growth.GrowthCurveActivity;
import com.zhimu.slmp.feed.ui.register.FeedRegisterActivity;
import com.zhimu.slmp.feed.ui.weigh.WeighRegisterActivity;
import com.zhimu.slmp.feed.widget.GrowthChartModel;
import com.zhimu.slmp.feed.widget.GrowthCurveView;
import com.zhimu.slmp.finance.data.FinanceApi;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.record.FinanceRecordEditActivity;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.HerdApi;
import com.zhimu.slmp.herd.model.request.PenCreateRequest;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.ui.archive.BatchArchiveActivity;
import com.zhimu.slmp.herd.ui.common.OptionAdapter;
import com.zhimu.slmp.herd.ui.entry.BatchEntryActivity;
import com.zhimu.slmp.herd.ui.entry.BatchEntrySuccessActivity;
import com.zhimu.slmp.herd.ui.transfer.BatchTransferActivity;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.today.data.TodayApi;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.model.vo.TodoPageVO;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Url;

/** Real UI writes against the isolated local backend. No resets or updates to existing weigh records. */
@RunWith(AndroidJUnit4.class)
public class CoreWorkflowRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private final List<Activity> opened = new ArrayList<>();

    public interface ReadApi {
        @GET Single<ApiResponse<JsonObject>> get(@Url String path);
    }

    @Before
    public void loginToIsolatedService() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        core.sessionManager().saveToken(unwrap(core.retrofit().create(AccountApi.class).loginByPassword(
                new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-core-workflow", "Pixel 9 Pro", "Android")))));
        core.farmContext().update(unwrap(core.retrofit().create(FarmApi.class).switchFarm(1001L)));
        instrumentation.waitForIdleSync();
    }

    @After
    public void finishTestActivities() {
        if (instrumentation == null) return;
        instrumentation.runOnMainSync(() -> {
            for (Activity activity : opened) if (!activity.isFinishing()) activity.finish();
        });
    }

    @Test
    public void growthComparisonShowsTheSelectedBatchAndRestoresRangeWhenRemoved() throws Exception {
        core.farmContext().update(unwrap(core.retrofit().create(FarmApi.class).switchFarm(1002L)));
        WeighApi api = core.retrofit().create(WeighApi.class);
        long mainId = 0;
        for (WeighBatchOptionVO option : unwrap(api.getBatchOptions(true))) {
            if ("ZM2603-015".equals(option.getBatchNo())) mainId = option.getBatchId();
        }
        assertTrue("需要截图中的主批次 ZM2603-015，测试不修改称重数据", mainId > 0);
        GrowthCompareOptionVO selected = null;
        for (GrowthCompareOptionVO option : unwrap(api.getCompareOptions(mainId))) {
            if ("ZM2610-026".equals(option.getBatchNo())) selected = option;
        }
        assertNotNull("需要截图中的对比批次 ZM2610-026", selected);
        final long compareId = selected.getBatchId();
        final String compareNo = selected.getBatchNo();
        GrowthCurveVO expected = unwrap(api.getGrowthCurve(mainId, String.valueOf(compareId)));
        GrowthCurveVO.GrowthCompareSeriesVO expectedSeries = null;
        for (GrowthCurveVO.GrowthCompareSeriesVO series : expected.getCompareSeries()) {
            if (series.getBatchId() == compareId) expectedSeries = series;
        }
        assertNotNull("真实接口必须返回所选对比批次", expectedSeries);
        assertFalse("该回归样本需要真实对比点", expectedSeries.getPoints().isEmpty());

        GrowthCurveActivity activity = (GrowthCurveActivity) open(GrowthCurveActivity.class,
                RouterPath.EXTRA_BATCH_ID, mainId);
        waitForUi(() -> {
            GrowthCurveView chart = activity.findViewById(com.zhimu.slmp.feed.R.id.feed_growth_chart);
            return chart != null && chart.getModel() != null && chart.isShown();
        }, "生长曲线未加载");
        AtomicReference<GrowthChartModel> initial = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> initial.set(((GrowthCurveView) activity.findViewById(
                com.zhimu.slmp.feed.R.id.feed_growth_chart)).getModel()));
        onView(withId(com.zhimu.slmp.feed.R.id.feed_growth_add_compare)).perform(click());
        waitForUi(() -> {
            for (androidx.fragment.app.Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
                if (fragment instanceof androidx.fragment.app.DialogFragment) {
                    android.app.Dialog dialog = ((androidx.fragment.app.DialogFragment) fragment).getDialog();
                    if (dialog != null && dialog.getWindow() != null
                            && containsText(dialog.getWindow().getDecorView(), compareNo)) return true;
                }
            }
            return false;
        }, "真实对比选项没有加载");
        onView(allOf(withId(com.zhimu.slmp.feed.R.id.feed_compare_title), withText(compareNo)))
                .perform(scrollTo(), click());
        onView(withId(com.zhimu.slmp.base.R.id.base_sheet_primary)).perform(click());
        AtomicReference<GrowthChartModel> displayed = new AtomicReference<>();
        waitForUi(() -> {
            GrowthCurveView chart = activity.findViewById(com.zhimu.slmp.feed.R.id.feed_growth_chart);
            GrowthChartModel model = chart.getModel();
            if (model == null || model.compare.size() != 1 || model.compare.get(0).batchId != compareId) return false;
            displayed.set(model);
            return containsText(activity.findViewById(com.zhimu.slmp.feed.R.id.feed_growth_compare_chips), compareNo);
        }, "选择后未立即显示真实对比数据及批次芯片");
        GrowthChartModel model = displayed.get();
        GrowthChartModel.Series actualSeries = model.compare.get(0);
        int validPoints = 0;
        for (GrowthCurveVO.GrowthComparePointVO point : expectedSeries.getPoints()) {
            if (point.getAvgWeight() == null) continue;
            GrowthChartModel.Point actualPoint = actualSeries.points.get(validPoints++);
            assertEquals(point.getDayAge(), actualPoint.dayAge);
            assertEquals(point.getAvgWeight().floatValue(), actualPoint.weight, 0.0001f);
            assertTrue("对比日龄不能被主批次横轴裁掉", actualPoint.dayAge >= model.xStart && actualPoint.dayAge <= model.xEnd);
            assertTrue("对比体重必须在可见纵轴内", actualPoint.weight >= model.yMin && actualPoint.weight <= model.yMax);
        }
        assertEquals(validPoints, actualSeries.points.size());
        waitForScreenIdle();
        screenshot("growth-comparison-added");

        onView(allOf(withText(compareNo), isDescendantOfA(withId(com.zhimu.slmp.feed.R.id.feed_growth_compare_chips))))
                .perform(new GeneralClickAction(Tap.SINGLE, view -> {
                    int[] location = new int[2];
                    view.getLocationOnScreen(location);
                    float inset = 16 * view.getResources().getDisplayMetrics().density;
                    return new float[]{location[0] + view.getWidth() - inset, location[1] + view.getHeight() / 2f};
                }, Press.FINGER));
        waitForUi(() -> {
            GrowthChartModel removed = ((GrowthCurveView) activity.findViewById(
                    com.zhimu.slmp.feed.R.id.feed_growth_chart)).getModel();
            ViewGroup chips = activity.findViewById(com.zhimu.slmp.feed.R.id.feed_growth_compare_chips);
            return removed != null && removed.compare.isEmpty() && chips.getChildCount() == 0
                    && removed.xStart == initial.get().xStart && removed.xEnd == initial.get().xEnd;
        }, "移除批次后曲线、芯片或横轴没有恢复");
        waitForScreenIdle();
        screenshot("growth-comparison-removed");
        JsonObject result = new JsonObject();
        result.addProperty("batch_id", mainId);
        result.addProperty("compare_batch_id", compareId);
        result.addProperty("point_count", actualSeries.points.size());
        result.addProperty("x_start", model.xStart);
        result.addProperty("x_end", model.xEnd);
        result.addProperty("removed_restores_range", true);
        evidence("growth_comparison", result);
    }

    @Test
    public void weighingSavesNewRecordAndReadsBackThroughGrowthCurve() throws Exception {
        WeighApi api = core.retrofit().create(WeighApi.class);
        WeighFormVO chosen = null;
        long batchId = 0;
        for (WeighBatchOptionVO option : unwrap(api.getBatchOptions(false))) {
            WeighFormVO candidate = unwrap(api.getWeighForm(option.getBatchId(), null));
            if (candidate.isCanRegister() && candidate.getExistingRecordId() == null
                    && candidate.getStockCount() != null && candidate.getStockCount() >= 10) {
                chosen = candidate;
                batchId = option.getBatchId();
                break;
            }
        }
        assertNotNull("Fixture不足：需至少一个今天尚未称重、存栏不少于10的可登记批次；不会覆盖历史记录", chosen);
        final long selectedBatch = batchId;
        PageVO<WeighRecordItemVO> before = unwrap(api.getWeighRecords(batchId, 1, 100));
        int stock = chosen.getStockCount();
        int samples = Math.min(stock, Math.max(10, chosen.getSuggestedSample() == null ? 10 : chosen.getSuggestedSample()));
        BigDecimal reference = chosen.getStdWeightAtDate();
        if (reference == null || reference.signum() <= 0) reference = chosen.getBatch().getEntryAvgWeight();
        assertNotNull("Fixture需有标准或入栏参考体重", reference);
        BigDecimal totalWeight = reference.multiply(BigDecimal.valueOf(samples)).setScale(1, RoundingMode.HALF_UP);
        assertTrue(totalWeight.signum() > 0);
        Activity form = open(WeighRegisterActivity.class, RouterPath.EXTRA_BATCH_ID, batchId);
        waitForUi(() -> visible(form, com.zhimu.slmp.feed.R.id.feed_weigh_sample_input), "称重表单未加载");
        onView(withId(com.zhimu.slmp.feed.R.id.feed_weigh_sample_input)).perform(scrollTo(), replaceText(String.valueOf(samples)), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.feed.R.id.feed_weigh_total_input)).perform(scrollTo(), replaceText(totalWeight.toPlainString()), closeSoftKeyboard());
        screenshot("weigh-before-submit");
        onView(allOf(withText(com.zhimu.slmp.feed.R.string.feed_weigh_action_submit),
                isDescendantOfA(withId(com.zhimu.slmp.feed.R.id.feed_action_bar)))).perform(click());
        Activity chart = waitForActivity(GrowthCurveActivity.class);
        opened.add(chart);
        waitForUi(() -> visible(chart, com.zhimu.slmp.feed.R.id.feed_growth_chart), "提交后未显示生长曲线");
        onView(withId(com.zhimu.slmp.feed.R.id.feed_growth_chart)).check(matches(isDisplayed()));
        PageVO<WeighRecordItemVO> records = unwrap(api.getWeighRecords(selectedBatch, 1, 100));
        assertEquals("UI提交必须真正新增一条记录", before.getTotal() + 1, records.getTotal());
        WeighRecordItemVO saved = null;
        for (WeighRecordItemVO row : records.getList()) {
            if (chosen.getWeighDate().equals(row.getWeighDate())) saved = row;
        }
        assertNotNull("提交日期的新称重记录必须能读取", saved);
        WeighRecordVO detail = unwrap(api.getWeighRecord(saved.getId()));
        assertEquals(samples, detail.getSampleCount().intValue());
        assertEquals(0, totalWeight.compareTo(detail.getTotalWeight()));
        GrowthCurveVO curve = unwrap(api.getGrowthCurve(selectedBatch, null));
        boolean containsSavedWeight = false;
        for (GrowthCurveVO.GrowthActualPointVO point : curve.getActualPoints()) {
            if (!point.isEntry() && chosen.getWeighDate().equals(point.getDate())
                    && point.getAvgWeight() != null && point.getAvgWeight().compareTo(detail.getAvgWeight()) == 0) {
                containsSavedWeight = true;
            }
        }
        assertTrue("曲线实际点必须包含刚保存的称重数值", containsSavedWeight);
        screenshot("weigh-saved-growth");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("batch_id", selectedBatch);
        evidence.addProperty("record_id", detail.getId());
        evidence.addProperty("weigh_date", detail.getWeighDate());
        evidence.addProperty("sample_count", samples);
        evidence.addProperty("total_weight", totalWeight);
        evidence.addProperty("avg_weight", detail.getAvgWeight());
        evidence("weigh", evidence);
    }

    @Test
    public void feedingSavesRecordDecreasesStockAndRefreshesHomeTodo() throws Exception {
        FeedApi api = core.retrofit().create(FeedApi.class);
        FeedTaskFormVO chosen = null;
        for (FeedTaskItemVO task : unwrap(api.getTodayTasks(null, null, 1, 100)).getList()) {
            FeedTaskFormVO candidate = unwrap(api.getTaskForm(task.getTaskId()));
            FeedOptionBriefVO feed = candidate.getDefaultFeed();
            if (candidate.isCanRegister() && candidate.getRecordId() == null
                    && DateFormatter.todayString().equals(candidate.getTaskDate())
                    && feed != null && feed.isSelectable() && feed.getStockQty() != null
                    && candidate.getPlanQty() != null && candidate.getPlanQty().signum() > 0
                    && feed.getStockQty().compareTo(candidate.getPlanQty().multiply(BigDecimal.TEN)) >= 0) {
                chosen = candidate;
                break;
            }
        }
        assertNotNull("Fixture不足：需要一个当天尚未登记且默认饲料库存充足的投喂任务", chosen);
        final FeedTaskFormVO selected = chosen;
        int before = homeTodoCount();
        assertTrue("待办数应包括未完成的投喂", before > 0);
        TodayApi todayApi = core.retrofit().create(TodayApi.class);
        List<TodoItemVO> homeBefore = readHomeTodos(todayApi);
        TodoItemVO feedTodo = null;
        for (TodoItemVO item : homeBefore) {
            if (item.getRegisterLink() != null && item.getRegisterLink().getParams() != null
                    && String.valueOf(selected.getTaskId()).equals(item.getRegisterLink().getParams().get("task_id"))) {
                feedTodo = item;
                break;
            }
        }
        assertNotNull("所选投喂任务必须在真实首页待办中", feedTodo);
        final long completedTodoId = feedTodo.getTodoId();
        assertEquals("今日卡片与今日未完成列表总数必须一致", before,
                unwrap(todayApi.getTodayTodos("ALL", 1, 100)).getTotal());
        Activity main = openMainToday();
        waitForUi(() -> homeCardCount(main) == before, "首页未显示当前待办数量");
        Activity form = open(FeedRegisterActivity.class, RouterPath.EXTRA_TASK_ID, selected.getTaskId());
        waitForUi(() -> visible(form, com.zhimu.slmp.feed.R.id.feed_reg_actual_input), "投喂表单未加载");
        String marker = "QA投喂" + System.currentTimeMillis();
        onView(withId(com.zhimu.slmp.feed.R.id.feed_reg_actual_input)).perform(scrollTo(), replaceText(selected.getPlanQty().toPlainString()), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.feed.R.id.feed_reg_remark_input)).perform(scrollTo(), replaceText(marker), closeSoftKeyboard());
        screenshot("feed-before-submit");
        onView(allOf(withText(com.zhimu.slmp.feed.R.string.feed_register_action_confirm),
                isDescendantOfA(withId(com.zhimu.slmp.feed.R.id.feed_action_bar)))).perform(click());
        waitForActivity(MainActivity.class);
        FeedTaskFormVO after = unwrap(api.getTaskForm(selected.getTaskId()));
        assertEquals("DONE", after.getStatus());
        assertNotNull("投喂任务必须关联真实记录", after.getRecordId());
        FeedRecordDetailVO record = unwrap(api.getRecord(after.getRecordId()));
        assertEquals(marker, record.getRemark());
        assertEquals(0, selected.getPlanQty().compareTo(record.getActualQty()));
        // todo_completed belongs to the POST response only; verify the persisted task and counts instead.
        BigDecimal expectedStock = selected.getDefaultFeed().getStockQty().subtract(selected.getPlanQty());
        assertNotNull(after.getDefaultFeed());
        assertEquals("投喂必须实际扣减饲料库存", 0, expectedStock.compareTo(after.getDefaultFeed().getStockQty()));
        assertEquals("首页服务端剩余待办应减一", before - 1, homeTodoCount());
        List<TodoItemVO> homeAfter = readHomeTodos(todayApi);
        assertEquals("首页今天和历史逾期的未完成总数应减一", homeBefore.size() - 1, homeAfter.size());
        for (TodoItemVO item : homeAfter) assertNotEquals("已完成投喂不能留在首页", completedTodoId, item.getTodoId());
        assertEquals("今日卡片与今日未完成列表总数必须一起更新", before - 1,
                unwrap(todayApi.getTodayTodos("ALL", 1, 100)).getTotal());
        assertEquals("已完成的历史详情必须保留", "DONE", unwrap(todayApi.getTodoDetail(completedTodoId)).getDisplayStatus());
        boolean calendarRetained = false;
        for (int page = 1; ; page++) {
            PageVO<TodoItemVO> day = unwrap(todayApi.getDayTodos(DateFormatter.todayString(), null, null, "ALL", page, 100));
            for (TodoItemVO item : day.getList()) {
                if (item.getTodoId() == completedTodoId && "DONE".equals(item.getDisplayStatus())) calendarRetained = true;
            }
            if (page * 100 >= day.getTotal() || calendarRetained) break;
        }
        assertTrue("日历仍需保留这笔已完成投喂", calendarRetained);
        waitForUi(() -> homeCardCount(main) == before - 1, "返回首页后待办数量未刷新");
        waitForUi(() -> homeListHasOnlyOpenTodos(main, completedTodoId), "App首页仍显示已完成或取消的待办");
        waitForScreenIdle();
        screenshot("feed-saved-home");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("task_id", selected.getTaskId());
        evidence.addProperty("record_id", record.getRecordId());
        evidence.addProperty("actual_qty", record.getActualQty());
        evidence.addProperty("remark", marker);
        evidence.addProperty("stock_before", selected.getDefaultFeed().getStockQty());
        evidence.addProperty("stock_after", after.getDefaultFeed().getStockQty());
        evidence.addProperty("todo_before", before);
        evidence.addProperty("todo_after", before - 1);
        evidence.addProperty("completed_todo_id", completedTodoId);
        evidence.addProperty("calendar_retained", calendarRetained);
        evidence("feed", evidence);
    }

    @Test
    public void manualIncomeSavesAndReadsBackTheExactAmountAndDescription() throws Exception {
        FinanceApi api = core.retrofit().create(FinanceApi.class);
        FinanceCategoryVO chosen = null;
        for (FinanceCategoryVO category : unwrap(api.getCategories("INCOME"))) {
            if (!category.isAutoSource() && !category.isMarkOnly()) {
                chosen = category;
                if ("其他收入".equals(category.getName())) break;
            }
        }
        assertNotNull("Fixture需有可手工记账的收入分类", chosen);
        final String categoryName = chosen.getName();
        String marker = "QA互联收入" + System.currentTimeMillis();
        BigDecimal amount = new BigDecimal("12.37");
        Activity main = openMainToday();
        Activity form = open(FinanceRecordEditActivity.class, null, 0);
        waitForUi(() -> visible(form, com.zhimu.slmp.finance.R.id.finance_type_income), "记账表单未加载");
        onView(withId(com.zhimu.slmp.finance.R.id.finance_type_income)).perform(click());
        waitForUi(() -> containsText(form.findViewById(com.zhimu.slmp.finance.R.id.finance_categories), categoryName)
                && visible(form, com.zhimu.slmp.finance.R.id.finance_note), "收入分类和说明未加载");
        onView(allOf(withText(categoryName), isDescendantOfA(withId(com.zhimu.slmp.finance.R.id.finance_categories))))
                .perform(scrollTo(), click());
        onView(withId(com.zhimu.slmp.finance.R.id.finance_amount)).perform(scrollTo(), replaceText(amount.toPlainString()), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.finance.R.id.finance_note)).perform(scrollTo(), replaceText(marker), closeSoftKeyboard());
        screenshot("income-before-submit");
        onView(allOf(withText(com.zhimu.slmp.finance.R.string.finance_e03_save),
                isDescendantOfA(withId(com.zhimu.slmp.finance.R.id.finance_bottom_bar)))).perform(click());
        waitForActivity(MainActivity.class);
        List<FinanceRecordVO> records = unwrap(api.listRecords(DateFormatter.todayString().substring(0, 7),
                "INCOME", null, null, null, marker, null, 1, 20)).getList();
        assertEquals("UI保存必须产生唯一的收入流水", 1, records.size());
        FinanceRecordVO saved = records.get(0);
        JsonObject detail = read("/finance-records/" + saved.getId());
        assertEquals("INCOME", detail.get("record_type").getAsString());
        assertEquals("MANUAL", detail.get("source_type").getAsString());
        assertEquals(marker, detail.get("note").getAsString());
        assertEquals(0, amount.compareTo(detail.get("amount").getAsBigDecimal()));
        assertFalse(main.isFinishing());
        Activity detailForm = open(FinanceRecordEditActivity.class, RouterPath.EXTRA_RECORD_ID, saved.getId());
        waitForUi(() -> marker.equals(text(detailForm, com.zhimu.slmp.finance.R.id.finance_note))
                && amount.toPlainString().equals(text(detailForm, com.zhimu.slmp.finance.R.id.finance_amount)),
                "已保存收入的详情未加载出原金额和说明");
        screenshot("income-saved-detail");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("id", saved.getId());
        evidence.addProperty("amount", amount);
        evidence.addProperty("note", marker);
        evidence.addProperty("record_date", saved.getRecordDate());
        evidence.addProperty("record_type", "INCOME");
        evidence("income", evidence);
    }

    @Test
    public void newBatchThenTransferPreservesTotalAndRefreshesBothPenCounts() throws Exception {
        HerdApi api = core.retrofit().create(HerdApi.class);
        PenFormOptionsVO options = unwrap(api.getFormOptions());
        CategoryVO category = null;
        for (CategoryVO candidate : core.baseDataRepository().getCategories().blockingGet()) {
            if (candidate.isBreeding() || candidate.getBreeds().isEmpty()) continue;
            for (PenFormOptionsVO.CategoryOption allowed : options.getCategories()) {
                if (allowed.getCategoryId() == candidate.getId()) { category = candidate; break; }
            }
            if (category != null) break;
        }
        assertNotNull("本地QA场需要启用的商品品类及品种", category);
        assertFalse(options.getPenTypes().isEmpty());
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        // Only these two fresh pens are changed. Existing batch/pen stock is never used as a fixture.
        PenDetailVO from = createQaPen(api, category.getId(), options.getPenTypes().get(0).getValue(), suffix, "A");
        PenDetailVO to = createQaPen(api, category.getId(), options.getPenTypes().get(0).getValue(), suffix, "B");
        assertEquals(0, from.getStockCount());
        assertEquals(0, to.getStockCount());
        BatchEntryActivity form = (BatchEntryActivity) open(BatchEntryActivity.class, null, 0);
        chooseHerdOption(form, com.zhimu.slmp.herd.R.id.herd_breed_input, category.getBreeds().get(0).getId());
        chooseHerdOption(form, com.zhimu.slmp.herd.R.id.herd_pen_input, from.getPenId());
        onView(withId(com.zhimu.slmp.herd.R.id.herd_source_self)).perform(scrollTo(), click());
        onView(withHint(com.zhimu.slmp.herd.R.string.herd_entry_age)).perform(scrollTo(), replaceText("30"), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.herd.R.id.herd_count_input)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard());
        screenshot("batch-entry-form");
        onView(allOf(withText(com.zhimu.slmp.herd.R.string.herd_action_submit),
                isDescendantOfA(withId(com.zhimu.slmp.herd.R.id.herd_action_bar)))).perform(click());
        BatchEntrySuccessActivity success = (BatchEntrySuccessActivity) waitForActivity(BatchEntrySuccessActivity.class);
        opened.add(success);
        BatchEntryResultVO created = GsonFactory.get().fromJson(success.getIntent().getStringExtra(HerdExtras.EXTRA_RESULT_JSON),
                BatchEntryResultVO.class);
        assertNotNull("真实入栏成功页必须携带服务端批次结果", created);
        assertEquals(from.getPenId(), created.getPenId());
        assertEquals(5, created.getEntryCount());
        assertEquals(5, unwrap(api.getBatch(created.getBatchId())).getCurrentCount());
        waitForUi(() -> {
            android.widget.ImageView qr = success.findViewById(com.zhimu.slmp.herd.R.id.herd_qr_image);
            return qr != null && qr.getDrawable() != null;
        }, "新批次成功页必须展示真实二维码");
        screenshot("batch-entry-saved");
        onView(withText(com.zhimu.slmp.herd.R.string.herd_success_view)).perform(click());
        BatchArchiveActivity archive = (BatchArchiveActivity) waitForActivity(BatchArchiveActivity.class);
        opened.add(archive);
        waitForUi(() -> visible(archive, com.zhimu.slmp.herd.R.id.herd_menu_more), "新批次档案没有管理菜单");
        onView(withId(com.zhimu.slmp.herd.R.id.herd_menu_more)).perform(click());
        onView(withText("转栏")).perform(click());
        BatchTransferActivity transfer = (BatchTransferActivity) waitForActivity(BatchTransferActivity.class);
        opened.add(transfer);
        chooseHerdOption(transfer, com.zhimu.slmp.herd.R.id.herd_to_input, to.getPenId());
        onView(withId(com.zhimu.slmp.herd.R.id.herd_quantity_input)).perform(scrollTo(), replaceText("2"), closeSoftKeyboard());
        onView(allOf(withText(com.zhimu.slmp.herd.R.string.herd_action_save),
                isDescendantOfA(withId(com.zhimu.slmp.herd.R.id.herd_action_bar)))).perform(click());
        waitForUi(() -> transfer.isFinishing() && archive.hasWindowFocus()
                        && text(archive, com.zhimu.slmp.herd.R.id.herd_pens_text).contains(from.getPenName() + " 3")
                        && text(archive, com.zhimu.slmp.herd.R.id.herd_pens_text).contains(to.getPenName() + " 2"),
                "转栏保存后档案必须立即刷新两栏的3/2数量");
        int total = 0, fromCount = 0, toCount = 0;
        for (PenStockVO pen : unwrap(api.getBatchPenStocks(created.getBatchId()))) {
            total += pen.getStockCount();
            if (pen.getPenId() == from.getPenId()) fromCount = pen.getStockCount();
            if (pen.getPenId() == to.getPenId()) toCount = pen.getStockCount();
        }
        assertEquals(5, total);
        assertEquals(3, fromCount);
        assertEquals(2, toCount);
        assertEquals(5, unwrap(api.getBatch(created.getBatchId())).getCurrentCount());
        List<TimelineItemVO> timeline = unwrap(api.getTimeline(created.getBatchId(), "TRANSFER", 1, 50)).getList();
        assertEquals("真实转栏只能生成一条生产记录", 1, timeline.size());
        assertEquals("TRANSFER", timeline.get(0).getRecordType());
        screenshot("batch-transfer-saved");
        JsonObject result = new JsonObject();
        result.addProperty("batch_id", created.getBatchId());
        result.addProperty("batch_no", created.getBatchNo());
        result.addProperty("from_pen_id", from.getPenId());
        result.addProperty("from_pen_name", from.getPenName());
        result.addProperty("to_pen_id", to.getPenId());
        result.addProperty("to_pen_name", to.getPenName());
        result.addProperty("from_count", fromCount);
        result.addProperty("to_count", toCount);
        result.addProperty("total_count", total);
        result.addProperty("transfer_event_id", timeline.get(0).getBizId());
        evidence("batch_transfer", result);
    }

    private PenDetailVO createQaPen(HerdApi api, long categoryId, String type, String suffix, String side) {
        PenCreateRequest request = new PenCreateRequest();
        request.penCode = "Q" + suffix + side;
        request.penName = "QA转栏" + suffix + side;
        request.categoryId = categoryId;
        request.penType = type;
        request.capacity = 10;
        return unwrap(api.createPen(request, UUID.randomUUID().toString()));
    }

    private void chooseHerdOption(Activity activity, int inputId, long optionId) {
        waitForUi(() -> {
            android.widget.AutoCompleteTextView input = activity.findViewById(inputId);
            return input != null && input.getAdapter() instanceof OptionAdapter
                    && ((OptionAdapter) input.getAdapter()).findById(optionId) != null;
        }, "真实业务下拉缺少目标选项 " + optionId);
        onView(withId(inputId)).perform(scrollTo(), click());
        waitForUi(() -> ((android.widget.AutoCompleteTextView) activity.findViewById(inputId)).isPopupShowing(),
                "点击业务下拉后未显示选项弹窗");
        onData(new androidx.test.espresso.matcher.BoundedMatcher<Object, OptionAdapter.Item>(OptionAdapter.Item.class) {
            @Override protected boolean matchesSafely(OptionAdapter.Item item) { return item.id == optionId && !item.disabled; }
            @Override public void describeTo(org.hamcrest.Description description) { description.appendText("业务选项ID=" + optionId); }
        }).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
    }

    private <T> T unwrap(Single<ApiResponse<T>> source) {
        return source.compose(RxResult.<T>unwrap()).blockingGet();
    }

    private JsonObject read(String path) {
        return unwrap(core.retrofit().create(ReadApi.class).get("api/v1" + path));
    }

    private int homeTodoCount() {
        return read("/home").getAsJsonObject("owner_metrics").get("today_todo_count").getAsInt();
    }

    private List<TodoItemVO> readHomeTodos(TodayApi api) {
        List<TodoItemVO> result = new ArrayList<>();
        for (int page = 1; ; page++) {
            TodoPageVO data = unwrap(api.getTodayTodos("HOME", page, 100));
            for (TodoItemVO item : data.getList()) {
                assertTrue("首页只应返回未完成或逾期待办", "PENDING".equals(item.getDisplayStatus())
                        || "OVERDUE".equals(item.getDisplayStatus()));
                result.add(item);
            }
            if (result.size() >= data.getTotal() || data.getList().isEmpty()) break;
        }
        return result;
    }

    private boolean homeListHasOnlyOpenTodos(Activity activity, long completedTodoId) {
        androidx.recyclerview.widget.RecyclerView list = activity.findViewById(com.zhimu.slmp.today.R.id.list);
        if (list == null || !(list.getAdapter() instanceof androidx.recyclerview.widget.ConcatAdapter)) return false;
        for (androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter :
                ((androidx.recyclerview.widget.ConcatAdapter) list.getAdapter()).getAdapters()) {
            if (adapter instanceof TodoPagingAdapter) {
                for (TodoItemVO item : ((TodoPagingAdapter) adapter).snapshot().getItems()) {
                    if (item.getTodoId() == completedTodoId || !("PENDING".equals(item.getDisplayStatus())
                            || "OVERDUE".equals(item.getDisplayStatus()))) return false;
                }
                return true;
            }
        }
        return false;
    }

    private Activity openMainToday() {
        Activity activity = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(RouterPath.EXTRA_MAIN_TAB, TabRegistry.TODAY));
        opened.add(activity);
        return activity;
    }

    private Activity open(Class<? extends Activity> type, String extra, long id) {
        Intent intent = new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extra != null) intent.putExtra(extra, id);
        Activity activity = instrumentation.startActivitySync(intent);
        opened.add(activity);
        return activity;
    }

    private boolean visible(Activity activity, int id) {
        View view = activity.findViewById(id);
        return view != null && view.isShown() && view.isEnabled();
    }

    private String text(Activity activity, int id) {
        TextView view = activity.findViewById(id);
        return view == null ? "" : view.getText().toString();
    }

    private int homeCardCount(Activity activity) {
        View card = activity.findViewById(com.zhimu.slmp.today.R.id.card_today);
        TextView value = card == null ? null : card.findViewById(com.zhimu.slmp.base.R.id.base_stat_value);
        if (value == null) return -1;
        try { return Integer.parseInt(value.getText().toString().replace(",", "").trim()); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private boolean containsText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private void waitForUi(BooleanSupplier condition, String failure) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> done.set(condition.getAsBoolean()));
            if (done.get()) return;
            SystemClock.sleep(100);
        }
        fail(failure);
    }

    private Activity waitForActivity(Class<? extends Activity> type) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        AtomicReference<Activity> found = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity)) found.set(activity);
                }
            });
            if (found.get() != null) return found.get();
            SystemClock.sleep(100);
        }
        fail("未进入目标页面 " + type.getSimpleName() + "；请检查 UI 错误、表单校验或网络响应");
        return null;
    }

    private File artifacts() {
        File directory = new File(context.getExternalFilesDir(null), "core-workflow");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        return directory;
    }

    private void evidence(String key, JsonObject result) throws Exception {
        File file = new File(artifacts(), "evidence.json");
        JsonObject all = file.isFile() ? JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject()
                : new JsonObject();
        all.add(key, result);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(all.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void screenshot(String name) throws Exception {
        waitForScreenIdle();
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(artifacts(), name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }

    private void waitForScreenIdle() {
        instrumentation.waitForIdleSync();
        try {
            instrumentation.getUiAutomation().waitForIdle(500, 5000);
        } catch (java.util.concurrent.TimeoutException ignored) {
            // Data and visible UI assertions still determine success if unrelated events keep arriving.
        }
    }
}
