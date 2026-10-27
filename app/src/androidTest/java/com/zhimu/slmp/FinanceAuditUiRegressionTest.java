package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.recordlog.RecordLogTexts;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.finance.data.FinanceApi;
import com.zhimu.slmp.finance.model.request.FinanceRecordRequest;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.record.FinanceRecordEditActivity;
import com.zhimu.slmp.finance.ui.recordlist.FinanceRecordAdapter;
import com.zhimu.slmp.finance.ui.recordlist.FinanceRecordListActivity;
import com.zhimu.slmp.network.RxResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Creates one guarded local fixture, then edits it through the real menu and checks its audit sheet. */
@RunWith(AndroidJUnit4.class)
public class FinanceAuditUiRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private FinanceApi api;
    private FinanceRecordVO before;
    private FinanceRecordListActivity listPage;

    @Before public void createOnlyLocalQaRecord() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        core.sessionManager().saveToken(core.retrofit().create(AccountApi.class).loginByPassword(new PasswordLoginRequest(
                "13800006620", "Zmt12345", new DeviceInfo("pixel9-finance-audit", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet());
        core.farmContext().update(core.retrofit().create(FarmApi.class).switchFarm(1001L)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet());
        api = core.retrofit().create(FinanceApi.class);
        FinanceCategoryVO chosen = null;
        for (FinanceCategoryVO category : api.getCategories("INCOME")
                .compose(RxResult.<List<FinanceCategoryVO>>unwrap()).blockingGet()) {
            if (!category.isAutoSource() && !category.isMarkOnly()) {
                chosen = category;
                if ("其他收入".equals(category.getName())) break;
            }
        }
        assertNotNull("本地QA场需要手工收入分类", chosen);
        String note = "QA_AUDIT_" + System.currentTimeMillis();
        before = api.createRecord(new FinanceRecordRequest("INCOME", new BigDecimal("235.41"), chosen.getId(),
                null, note, DateFormatter.todayString(), null, Collections.emptyList(), null), UUID.randomUUID().toString())
                .compose(RxResult.<FinanceRecordVO>unwrap()).blockingGet();
        assertEquals("MANUAL", before.getSourceType());
        assertTrue("只允许修改本次新建的QA记录", before.getNote() != null && before.getNote().startsWith("QA_AUDIT_"));
    }

    @After public void closeFinancePages() {
        if (instrumentation == null) return;
        instrumentation.runOnMainSync(() -> {
            for (Stage stage : Stage.values()) {
                for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))) {
                    if (activity instanceof FinanceRecordEditActivity || activity instanceof FinanceRecordListActivity) activity.finish();
                }
            }
        });
    }

    @Test public void editAmount_thenRealAuditSheetShowsBeforeAndAfter_andMenuHasNoPurpleFill() throws Exception {
        listPage = (FinanceRecordListActivity) instrumentation.startActivitySync(new Intent(context, FinanceRecordListActivity.class)
                .putExtra(RouterPath.EXTRA_MONTH, before.getRecordDate().substring(0, 7))
                .putExtra(RouterPath.EXTRA_HIGHLIGHT_ID, before.getId()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        View more = findMoreForQaRecord();
        onView(sameInstance(more)).perform(click());
        onView(withText(com.zhimu.slmp.finance.R.string.finance_e04_menu_edit)).check(matches(isDisplayed()));
        saveScreenshotAndCheckMoreColor(more, "finance-audit-menu");
        onView(withText(com.zhimu.slmp.finance.R.string.finance_e04_menu_edit)).perform(click());
        AtomicReference<FinanceRecordEditActivity> form = new AtomicReference<>();
        awaitUi("修改表单必须载入原金额", () -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof FinanceRecordEditActivity) form.set((FinanceRecordEditActivity) activity);
            }
            TextView amount = form.get() == null ? null : form.get().findViewById(com.zhimu.slmp.finance.R.id.finance_amount);
            return amount != null && "235.41".contentEquals(amount.getText());
        });
        onView(withId(com.zhimu.slmp.finance.R.id.finance_amount)).perform(scrollTo(), replaceText("236.42"), closeSoftKeyboard());
        onView(allOf(withText(com.zhimu.slmp.finance.R.string.finance_e03_save),
                isDescendantOfA(withId(com.zhimu.slmp.finance.R.id.finance_bottom_bar)))).perform(click());
        awaitUi("保存后应返回原流水页", () -> form.get().isFinishing() && listPage.hasWindowFocus());
        FinanceRecordDetailVO after = api.getRecord(before.getId()).compose(RxResult.<FinanceRecordDetailVO>unwrap()).blockingGet();
        assertEquals(0, new BigDecimal("236.42").compareTo(after.getAmount()));
        assertEquals(before.getNote(), after.getNote());
        List<RecordLogVO> logs = core.baseDataRepository().getRecordLogs("FINANCE_RECORD", before.getId()).blockingGet();
        RecordLogVO.Change amountChange = null;
        int amountChanges = 0;
        for (RecordLogVO log : logs) for (RecordLogVO.Change change : log.getChanges()) {
            if ("金额".equals(change.getFieldName())) { amountChange = change; amountChanges++; }
        }
        assertEquals("一次真实修改必须记录一笔金额变化", 1, amountChanges);
        assertNotNull(amountChange);
        assertTrue(amountChange.getBefore().contains("235.41"));
        assertTrue(amountChange.getAfter().contains("236.42"));
        String expectedChange = RecordLogTexts.changeText(amountChange);
        onView(sameInstance(findMoreForQaRecord())).perform(click());
        onView(withText(com.zhimu.slmp.finance.R.string.finance_e04_menu_log)).perform(click());
        awaitUi("修改记录弹层必须显示真实金额前后值", () -> {
            androidx.fragment.app.Fragment fragment = listPage.getSupportFragmentManager().findFragmentByTag("common_record_log");
            if (!(fragment instanceof DialogFragment)) return false;
            android.app.Dialog dialog = ((DialogFragment) fragment).getDialog();
            return dialog != null && dialog.getWindow() != null && containsText(dialog.getWindow().getDecorView(), expectedChange);
        });
        onView(withText(containsString(expectedChange))).check(matches(isDisplayed()));
        saveScreenshotAndCheckMoreColor(null, "finance-audit-saved-log");
    }

    private View findMoreForQaRecord() {
        AtomicReference<View> found = new AtomicReference<>();
        awaitUi("流水列表中未找到本次QA记录", () -> {
            RecyclerView list = listPage.findViewById(com.zhimu.slmp.finance.R.id.finance_list);
            if (list == null || !(list.getAdapter() instanceof ConcatAdapter)) return false;
            int offset = 0;
            for (RecyclerView.Adapter<?> adapter : ((ConcatAdapter) list.getAdapter()).getAdapters()) {
                if (adapter instanceof FinanceRecordAdapter) {
                    int pos = ((FinanceRecordAdapter) adapter).positionOf(before.getId());
                    if (pos >= 0) {
                        list.scrollToPosition(offset + pos);
                        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(offset + pos);
                        View more = holder == null ? null : holder.itemView.findViewById(com.zhimu.slmp.finance.R.id.finance_record_more);
                        if (more != null && more.isShown()) { found.set(more); return true; }
                    }
                }
                offset += adapter.getItemCount();
            }
            return false;
        });
        return found.get();
    }

    private void saveScreenshotAndCheckMoreColor(View more, String name) throws Exception {
        Rect bounds = new Rect();
        if (more != null) instrumentation.runOnMainSync(() -> {
            int[] location = new int[2];
            more.getLocationOnScreen(location);
            bounds.set(location[0], location[1], location[0] + more.getWidth(), location[1] + more.getHeight());
        });
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            if (more != null) {
                int purple = 0, sampled = 0;
                float[] hsv = new float[3];
                for (int y = Math.max(0, bounds.top); y < Math.min(screenshot.getHeight(), bounds.bottom); y++) {
                    for (int x = Math.max(0, bounds.left); x < Math.min(screenshot.getWidth(), bounds.right); x++) {
                        Color.colorToHSV(screenshot.getPixel(x, y), hsv);
                        sampled++;
                        if (hsv[0] >= 260 && hsv[0] <= 335 && hsv[1] > .4f && hsv[2] > .55f) purple++;
                    }
                }
                assertTrue("必须检查真实屏幕中的三点按钮区域", sampled > 0);
                assertTrue("三点按钮不能显示高饱和紫色填充", purple < sampled * .05f);
            }
        } finally { screenshot.recycle(); }
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        }
        return false;
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
}
