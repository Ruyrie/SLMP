package com.zhimu.slmp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.TextView;
import android.view.View;
import android.widget.ImageView;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.zhimu.slmp.feed.ui.growth.GrowthCurveActivity;
import com.zhimu.slmp.inspect.ui.register.InspectRegisterActivity;
import com.zhimu.slmp.today.ui.notice.NoticeDetailActivity;
import com.zhimu.slmp.today.ui.detail.TodoDetailActivity;
import com.zhimu.slmp.network.model.ApiResponse;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Url;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.inspect.ui.report.AbnormalReportActivity;
import com.zhimu.slmp.inspect.ui.diagnosis.DiagnosisDetailActivity;
import com.zhimu.slmp.network.RxResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 真实接口的模拟器回归。仅在本机隔离测试服务启用，避免对公网数据执行写操作。 */
@RunWith(AndroidJUnit4.class)
public class FunctionalRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;

    @Before public void loginToIsolatedService() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        AuthTokenVO token = core.retrofit().create(AccountApi.class)
                .loginByPassword(new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-functional-regression", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        FarmContextVO farm = core.retrofit().create(FarmApi.class).switchFarm(1001L)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
        core.farmContext().update(farm);
        instrumentation.waitForIdleSync();
    }

    @Test public void reportSubmissionReachesDiagnosisWithoutCrash() throws Exception {
        Intent intent = new Intent(context, AbnormalReportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RouterPath.EXTRA_PEN_ID, 2003L);
        Activity report = instrumentation.startActivitySync(intent);
        waitForText(report, com.zhimu.slmp.inspect.R.id.inspect_batch_input, 20000);
        capture("report-before-submit");
        onView(withId(com.zhimu.slmp.inspect.R.id.inspect_sick_input)).perform(replaceText("1"), closeSoftKeyboard());
        onView(allOf(isAssignableFrom(android.widget.EditText.class),
                isDescendantOfA(withId(com.zhimu.slmp.inspect.R.id.inspect_description_layout))))
                .perform(scrollTo(), replaceText("功能回归测试：观察到采食减少，需要检查。"), closeSoftKeyboard());
        onView(withText("提交并诊断")).perform(click());
        Activity diagnosis = waitForActivity(DiagnosisDetailActivity.class, 20000);
        assertNotNull("提交成功后应进入诊断详情", diagnosis);
        SystemClock.sleep(2000);
        capture("report-after-submit");
        assertFalse(diagnosis.isFinishing());
    }

    public interface QaApi {
        @GET Single<ApiResponse<JsonObject>> get(@Url String url);
    }

    private JsonObject api(String path) {
        return core.retrofit().create(QaApi.class).get("api/v1" + path)
                .compose(RxResult.<JsonObject>unwrap()).blockingGet();
    }

    private Activity open(Class<? extends Activity> type, String extra, long id) {
        return instrumentation.startActivitySync(new Intent(context, type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(extra, id));
    }

    @Test public void growthCurvesLoadForPigAndPoultry() throws Exception {
        for (long[] ids : new long[][]{{1001, 3001}, {1002, 3201}}) {
            FarmContextVO farm = core.retrofit().create(FarmApi.class).switchFarm(ids[0])
                    .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
            core.farmContext().update(farm);
            Activity chart = open(GrowthCurveActivity.class, RouterPath.EXTRA_BATCH_ID, ids[1]);
            waitForText(chart, com.zhimu.slmp.feed.R.id.feed_growth_batch_no, 20000);
            SystemClock.sleep(1200);
            onView(withId(com.zhimu.slmp.feed.R.id.feed_growth_chart)).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            capture("growth-" + ids[1]);
            instrumentation.runOnMainSync(chart::finish);
        }
    }

    @Test public void inspectionTemplatesLoadForPigAndPoultry() throws Exception {
        for (long[] ids : new long[][]{{1001, 2003}, {1002, 2201}}) {
        core.farmContext().update(core.retrofit().create(FarmApi.class).switchFarm(ids[0])
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet());
        Activity form = open(InspectRegisterActivity.class, RouterPath.EXTRA_PEN_ID, ids[1]);
        long deadline = SystemClock.uptimeMillis() + 20000;
        AtomicReference<Integer> count = new AtomicReference<>(0);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                android.view.ViewGroup items = form.findViewById(com.zhimu.slmp.inspect.R.id.inspect_items);
                count.set(items == null ? 0 : items.getChildCount());
            });
            if (count.get() >= 3) break;
            SystemClock.sleep(200);
        }
        assertTrue("巡检应加载已发布的三项模板", count.get() >= 3);
        capture("inspection-loaded-" + ids[1]);
        instrumentation.runOnMainSync(form::finish);
        }
    }

    @Test public void noticeImageLoadsAndOpensPreview() throws Exception {
        long id = Long.parseLong(InstrumentationRegistry.getArguments().getString("qa_message_id", "0"));
        assumeTrue("Requires fixture created by isolated admin API", id > 0);
        Activity notice = open(NoticeDetailActivity.class, RouterPath.EXTRA_MESSAGE_ID, id);
        waitForText(notice, com.zhimu.slmp.today.R.id.content, 20000);
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicReference<Boolean> loaded = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                ImageView image = notice.findViewById(com.zhimu.slmp.today.R.id.image);
                loaded.set(image != null && image.getDrawable() != null
                        && image.getDrawable().getIntrinsicWidth() >= 600);
                TextView content = notice.findViewById(com.zhimu.slmp.today.R.id.content);
                assertFalse("通知正文不应显示HTML标签", content.getText().toString().contains("<p>"));
            });
            if (loaded.get()) break;
            SystemClock.sleep(200);
        }
        assertTrue("管理员通知配图应真实加载", loaded.get());
        capture("notice-with-image");
        onView(withId(com.zhimu.slmp.today.R.id.image)).perform(scrollTo(), click());
        SystemClock.sleep(1000);
        assertNotNull("点配图后应跳转全屏预览", waitForActivity(com.zhimu.slmp.media.ui.viewer.MediaViewerActivity.class, 5000));
        capture("notice-image-preview");
    }

    @Test public void noticeUpdatesWhenAdminRevokes() throws Exception {
        long id = Long.parseLong(InstrumentationRegistry.getArguments().getString("qa_revoke_message_id", "0"));
        assumeTrue("Requires external isolated admin revoke action", id > 0);
        Activity notice = open(NoticeDetailActivity.class, RouterPath.EXTRA_MESSAGE_ID, id);
        waitForText(notice, com.zhimu.slmp.today.R.id.content, 20000);
        capture("notice-before-revoke");
        long deadline = SystemClock.uptimeMillis() + 90000;
        AtomicReference<Boolean> revoked = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                View panel = notice.findViewById(com.zhimu.slmp.today.R.id.revoked);
                revoked.set(panel != null && panel.getVisibility() == View.VISIBLE);
            });
            if (revoked.get()) break;
            SystemClock.sleep(300);
        }
        assertTrue("停留详情时管理员撤回应自动显示", revoked.get());
        onView(withText("该通知已撤回")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        capture("notice-revoked");
    }

    @Test public void completingTodayTodoReducesHomeRemainingCount() throws Exception {
        JsonObject before = api("/home");
        String today = before.get("server_time").getAsString().substring(0, 10);
        int count = before.getAsJsonObject("owner_metrics").get("today_todo_count").getAsInt();
        JsonObject candidate = null;
        for (JsonElement row : api("/todos/today?size=100").getAsJsonArray("list")) {
            JsonObject item = row.getAsJsonObject();
            if (item.has("due_date") && today.equals(item.get("due_date").getAsString())
                    && !"DONE".equals(item.get("display_status").getAsString())) {
                JsonObject detail = api("/todos/" + item.get("todo_id").getAsLong());
                if (detail.has("can_complete") && detail.get("can_complete").getAsBoolean()) {
                    candidate = item; break;
                }
            }
        }
        assertNotNull("应有一条今日可手动完成的隔离待办", candidate);
        long id = candidate.get("todo_id").getAsLong();
        Activity detail = open(TodoDetailActivity.class, RouterPath.EXTRA_TODO_ID, id);
        SystemClock.sleep(1500);
        onView(withText("标记完成")).perform(click());
        SystemClock.sleep(300);
        onView(withText("标记完成")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline && !"DONE".equals(api("/todos/"+id).get("display_status").getAsString())) {
            SystemClock.sleep(300);
        }
        assertEquals("DONE", api("/todos/"+id).get("display_status").getAsString());
        assertEquals("首页剩余待办应减一", count - 1,
                api("/home").getAsJsonObject("owner_metrics").get("today_todo_count").getAsInt());
        capture("todo-completed");
    }

    @Test public void ordinaryAndTodoChannelsCanDisplayForegroundNotifications() throws Exception {
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        String registrationId = (String) Class.forName("cn.jpush.android.api.JPushInterface")
                .getMethod("getRegistrationID", Context.class).invoke(null, context);
        if (registrationId != null && !registrationId.isEmpty()) {
            try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), "qa-registration-id.txt"))) {
                out.write(registrationId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        com.zhimu.slmp.today.push.NotificationChannels.createAll(context);
        assertTrue("测试设备须先授予通知权限", androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled());
        for (String channel : new String[]{com.zhimu.slmp.today.push.NotificationChannels.SYSTEM,
                com.zhimu.slmp.today.push.NotificationChannels.TODO}) {
            assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(channel).getImportance());
            manager.notify("slmp-qa", 9191, new android.app.Notification.Builder(context, channel)
                    .setSmallIcon(com.zhimu.slmp.base.R.drawable.ic_campaign)
                    .setContentTitle("SLMP QA 通知横幅验证")
                    .setContentText("普通通知与待办提醒渠道功能回归")
                    .setAutoCancel(true).build());
            SystemClock.sleep(800);
            assertTrue("通知应进入系统", manager.getActiveNotifications().length > 0);
            capture("notification-" + channel);
            manager.cancel("slmp-qa", 9191);
        }
    }

    @Test public void mainTabsRender() throws Exception {
        Activity main = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        for (String tab : new String[]{"今日", "生产", "助手", "经营", "我的"}) {
            onView(allOf(withText(tab), isDisplayed(), isDescendantOfA(withId(R.id.main_bottom_nav)))).perform(click());
            SystemClock.sleep(1800);
            assertFalse(main.isFinishing());
            capture("main-tab-" + tab);
        }
    }

    private void waitForText(Activity activity, int id, long timeout) {
        long deadline = SystemClock.uptimeMillis()+timeout;
        AtomicReference<String> text = new AtomicReference<>("");
        while (SystemClock.uptimeMillis()<deadline) {
            instrumentation.runOnMainSync(() -> {
                TextView view = activity.findViewById(id);
                text.set(view == null ? "" : view.getText().toString());
            });
            if (!text.get().isEmpty()) return;
            SystemClock.sleep(200);
        }
        fail("表单在限定时间内未加载出批次");
    }

    private Activity waitForActivity(Class<? extends Activity> type, long timeout) {
        long deadline = SystemClock.uptimeMillis()+timeout;
        AtomicReference<Activity> result = new AtomicReference<>();
        while (SystemClock.uptimeMillis()<deadline) {
            instrumentation.runOnMainSync(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(a)) result.set(a);
                }
            });
            if (result.get()!=null) return result.get();
            SystemClock.sleep(200);
        }
        return null;
    }

    private void capture(String name) throws Exception {
        File folder = new File(context.getExternalFilesDir(null), "functional-regression");
        if (!folder.exists()) assertTrue(folder.mkdirs());
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
