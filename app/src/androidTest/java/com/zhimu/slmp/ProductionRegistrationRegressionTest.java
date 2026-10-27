package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.router.RouterTable;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.feed.ui.weigh.WeighRegisterActivity;
import com.zhimu.slmp.inspect.ui.today.InspectTodayActivity;
import com.zhimu.slmp.today.ui.home.QuickRegisterItem;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Requires an owner/manager login for the two UI tests; the manifest audit needs no login. */
@RunWith(AndroidJUnit4.class)
public class ProductionRegistrationRegressionTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void requireWritableProductionFarm() {
        FarmContext farm = CoreInjector.get(context()).farmContext();
        assumeTrue("请先登录可登记的所有者或场长账号", farm.isOwnerOrManager() && farm.permissions().isWritable());
    }

    private Intent todayIntent() {
        return new Intent(context(), MainActivity.class)
                .putExtra(RouterPath.EXTRA_MAIN_TAB, TabRegistry.TODAY);
    }

    @Test
    public void allProductionAndQuickRegisterActionsResolveInTheInstalledApp() {
        assertTrue("T02 必须有实际宿主，不能回退到开发中提示", Router.canOpen(context(), RouterPath.T02));
        for (String code : new String[]{"L03", "B01", "G01", "G05", "I01", "I05", "D01", "D05", "D06", "M01", "M05"}) {
            String action = RouterTable.actionOf(code);
            assertNotNull(code, action);
            assertTrue(code, Router.canOpen(context(), action));
        }
        for (java.util.List<QuickRegisterItem> group : java.util.Arrays.asList(
                QuickRegisterItem.WORK, QuickRegisterItem.OWNER, QuickRegisterItem.STOCK_IN)) {
            for (QuickRegisterItem item : group) {
                if (QuickRegisterItem.ROUTE_STOCK_IN.equals(item.getRoute())) continue;
                String action = RouterTable.actionOf(item.getRoute());
                assertNotNull(item.getRoute(), action);
                assertTrue(item.getRoute(), Router.canOpen(context(), action));
            }
        }
    }

    @Test
    public void cancellingRegistrationReturnsToToday() {
        requireWritableProductionFarm();
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(todayIntent())) {
            onView(withId(com.zhimu.slmp.today.R.id.fab)).perform(click());
            onView(withText(com.zhimu.slmp.today.R.string.today_quick_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            pressBack();
            onView(withId(com.zhimu.slmp.today.R.id.fab)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void choosingWeighingOpensTheExistingFormAndReturnsToToday() {
        requireWritableProductionFarm();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(WeighRegisterActivity.class.getName(), null, false);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(todayIntent())) {
            onView(withId(com.zhimu.slmp.today.R.id.fab)).perform(click());
            onView(withText(com.zhimu.slmp.today.R.string.today_quick_weighing)).inRoot(isDialog()).perform(click());
            Activity form = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("快捷登记应进入实际称重表单", form);
            instrumentation.runOnMainSync(form::finish);
            onView(withId(com.zhimu.slmp.today.R.id.fab)).check(matches(isDisplayed()));
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void choosingInspectionOpensPenListBeforeRegistration() {
        requireWritableProductionFarm();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                InspectTodayActivity.class.getName(), null, false);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(todayIntent())) {
            onView(withId(com.zhimu.slmp.today.R.id.fab)).perform(click());
            onView(withText(com.zhimu.slmp.today.R.string.today_quick_inspection))
                    .inRoot(isDialog()).perform(click());
            Activity list = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("未选择栏舍时先展示巡检清单，不能打开缺少 pen_id 的表单", list);
            instrumentation.runOnMainSync(list::finish);
            onView(withId(com.zhimu.slmp.today.R.id.fab)).check(matches(isDisplayed()));
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }
}
