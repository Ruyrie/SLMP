package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.account.model.vo.CancellationPreviewVO;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.account.ui.cancel.CancelAccountActivity;
import com.zhimu.slmp.account.ui.login.LoginActivity;
import com.zhimu.slmp.account.ui.security.AccountSecurityActivity;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.common.session.SessionStore;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.today.data.TodayApi;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.ui.message.MessageCenterActivity;
import com.zhimu.slmp.today.ui.message.MessagePagingAdapter;
import com.zhimu.slmp.today.util.MessageRow;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import io.reactivex.rxjava3.core.Single;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Destructive only to an explicitly identified disposable member, in com.zhimu.slmp.accountqa.
 * Required args: qaMemberPhone, qaMemberPassword, qaMemberUserId, qaInviteId, qaFarmId, qaFarmName,
 * qaCancelSmsCode. Fixture: recovery confirmed, no owned/joined farms, one pending MANAGER invitation.
 * Code must match the local SMS provider. The test sends it through the real UI before entering it.
 * One ordered case joins, leaves, then cancels the same disposable account; never rerun with a real account.
 */
@RunWith(AndroidJUnit4.class)
public class AccountMembershipUiRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private AccountApi accounts;
    private FarmApi farms;
    private TodayApi today;
    private String phone, password, smsCode, farmName;
    private long userId, farmId, inviteId;
    private final List<Activity> opened = new ArrayList<>();
    private final OkHttpClient sessionProbe = new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build();

    @Before public void requireDisposablePackageAndAccount() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        assertEquals("禁止在原App包运行账号注销回归", "com.zhimu.slmp.accountqa", context.getPackageName());
        core = LocalQaEnvironment.requireLocalCore(context);
        String previousPhone = core.appSettings().getLastPhone();
        assertTrue("专用包必须为未保存登录手机号的新状态，保证A09使用本地验证码服务", previousPhone == null || previousPhone.isEmpty());
        Bundle args = InstrumentationRegistry.getArguments();
        phone = required(args, "qaMemberPhone");
        assertTrue("必须显式提供专用手机号", phone.matches("1[0-9]{10}"));
        assertNotEquals("禁止使用用户原账号", "1380006620", phone);
        assertNotEquals("禁止使用已有业务QA账号", "13800006620", phone);
        password = required(args, "qaMemberPassword");
        smsCode = required(args, "qaCancelSmsCode");
        assertTrue(smsCode.matches("[0-9]{6}"));
        userId = Long.parseLong(required(args, "qaMemberUserId"));
        farmId = Long.parseLong(required(args, "qaFarmId"));
        inviteId = Long.parseLong(required(args, "qaInviteId"));
        farmName = required(args, "qaFarmName");
        accounts = core.retrofit().create(AccountApi.class);
        farms = core.retrofit().create(FarmApi.class);
        today = core.retrofit().create(TodayApi.class);
        AuthTokenVO token = read(accounts.loginByPassword(new PasswordLoginRequest(phone, password,
                new DeviceInfo("account-membership-qa", "Pixel 9 Pro QA", "Android"))));
        assertNotNull(token.getUser());
        assertEquals("手机号必须对应显式指定的可注销用户", userId, token.getUser().getUserId());
        assertTrue("专用账号必须已经完成恢复码确认", token.getNextStep() == null || "NONE".equals(token.getNextStep()));
        core.sessionManager().saveToken(token);
        assertEquals(userId, read(accounts.getSession()).getUser().getUserId());
        assertTrue("只允许没有任何已有农场关系的专用新成员", read(farms.listFarms("ALL", 1, 100)).getList().isEmpty());
        assertNoFarmCancellationImpact(read(accounts.getCancellationPreview()));
        onMain(() -> assertNull("独立测试包不能带有旧养殖场上下文", core.farmContext().current()));
        MessageBriefVO invitation = invitation();
        assertNotNull("必须存在专用待接受邀请", invitation);
        assertEquals(inviteId, invitation.getAction().getActionId());
        assertEquals(MessageRow.STATUS_PENDING, invitation.getAction().getStatus());
        assertTrue(invitation.getAction().isCanAccept());
        // Before joining, the API may omit farm details for a nonmember. Verify the context after acceptance.
        if (invitation.getFarm() != null) {
            assertEquals(farmId, invitation.getFarm().getFarmId());
            assertEquals(farmName, invitation.getFarm().getName());
        }
    }

    @After public void closeOnlyDisposableUi() {
        if (instrumentation != null) onMain(() -> {
            for (Activity activity : opened) if (!activity.isFinishing()) activity.finish();
        });
        sessionProbe.connectionPool().evictAll();
        sessionProbe.dispatcher().executorService().shutdown();
        // No automatic account/farm cleanup on failure: preserve the dedicated fixture for diagnosis.
    }

    @Test public void acceptInvite_thenQuitRevokesFarmAccess_thenCancelLogsOutAndRevokesSession() throws Exception {
        MessageCenterActivity inbox = (MessageCenterActivity) open(MessageCenterActivity.class);
        AtomicReference<View> acceptButton = new AtomicReference<>();
        awaitUi("消息中心未显示指定邀请的同意按钮", () -> {
            RecyclerView list = inbox.findViewById(com.zhimu.slmp.today.R.id.list);
            if (list == null) return false;
            int position = invitationPosition(list.getAdapter());
            if (position < 0) { list.scrollBy(0, list.getHeight()); return false; }
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            View accept = holder == null ? null : holder.itemView.findViewById(com.zhimu.slmp.today.R.id.accept);
            if (accept == null || !accept.isShown() || !accept.isEnabled()) return false;
            acceptButton.set(accept);
            return true;
        });
        screenshot("account-invite-before-accept");
        onView(sameInstance(acceptButton.get())).perform(click());
        awaitUi("同意邀请后应自动进入该养殖场上下文", () -> core.farmContext().current() != null
                && core.farmContext().current().getFarmId() == farmId);
        FarmContextVO joined = read(farms.getContext(farmId));
        assertEquals(farmId, joined.getFarmId());
        assertEquals(farmName, joined.getName());
        assertEquals("专用夹具邀请角色必须为场长", FarmContextVO.ROLE_MANAGER, joined.getRole());
        assertEquals(MessageRow.STATUS_ACCEPTED, invitation().getAction().getStatus());
        MainActivity main = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RouterPath.EXTRA_MAIN_TAB, TabRegistry.MINE));
        opened.add(main);
        awaitUi("加入后我的页面应显示真实退场入口", () -> shown(main, com.zhimu.slmp.account.R.id.account_mine_quit_farm));
        onView(withId(com.zhimu.slmp.account.R.id.account_mine_quit_farm)).perform(scrollTo()).check(matches(isDisplayed()));
        screenshot("account-joined-farm");
        onView(withId(com.zhimu.slmp.account.R.id.account_mine_quit_farm)).perform(click());
        onView(withText(context.getString(com.zhimu.slmp.account.R.string.account_mine_quit_title, farmName)))
                .inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withText(com.zhimu.slmp.account.R.string.account_mine_quit_confirm)).inRoot(isDialog()).perform(click());
        awaitUi("退出唯一养殖场后当前场必须清空", () -> core.farmContext().current() == null);
        assertTrue("退出后我的养殖场列表不能保留旧成员关系", read(farms.listFarms("ALL", 1, 100)).getList().isEmpty());
        try {
            read(farms.getContext(farmId));
            fail("退出后原场受保护接口必须拒绝访问");
        } catch (RuntimeException denied) {
            assertEquals("拒绝必须来自成员权限失效", ErrorCodes.NOT_FARM_MEMBER, ApiException.from(denied).getCode());
        }
        screenshot("account-quit-access-revoked");

        // Navigate through the actual account/security entry; no direct cancellation API call.
        onView(allOf(withText("我的"), isDisplayed())).perform(click());
        awaitUi("退出后我的页面未恢复", () -> shown(main, com.zhimu.slmp.account.R.id.account_mine_security));
        onView(withId(com.zhimu.slmp.account.R.id.account_mine_security)).perform(scrollTo(), click());
        Activity security = waitForActivity(AccountSecurityActivity.class);
        awaitUi("账号安全页加载后应显示注销入口", () -> shown(security, com.zhimu.slmp.account.R.id.account_security_cancel));
        onView(withId(com.zhimu.slmp.account.R.id.account_security_cancel)).perform(scrollTo(), click());
        CancelAccountActivity cancel = (CancelAccountActivity) waitForActivity(CancelAccountActivity.class);
        CancellationPreviewVO preview = read(accounts.getCancellationPreview());
        assertNoFarmCancellationImpact(preview);
        assertTrue("专用账号应允许注销：" + preview.getBlockReasons(), preview.isCanCancel());
        awaitUi("注销预览未显示确认区域", () -> shown(cancel, com.zhimu.slmp.account.R.id.account_cancel_confirm));
        onView(withId(com.zhimu.slmp.account.R.id.account_cancel_confirm)).perform(scrollTo());
        screenshot("account-cancellation-preview");
        onView(withId(com.zhimu.slmp.account.R.id.account_cancel_confirm)).perform(click());
        onView(allOf(withText(FormBinder.SMS_SEND), isDescendantOfA(withId(com.zhimu.slmp.account.R.id.account_cancel_sms))))
                .perform(scrollTo(), click());
        awaitUi("实际获取验证码未成功，不能绕过SMS场景流程", () -> {
            TextInputLayout sms = cancel.findViewById(com.zhimu.slmp.account.R.id.account_cancel_sms);
            return sms != null && sms.getHelperText() != null && sms.getHelperText().length() > 0
                    && !sms.getSuffixTextView().isEnabled();
        });
        onView(allOf(withHint(com.zhimu.slmp.account.R.string.account_field_sms_code),
                isDescendantOfA(withId(com.zhimu.slmp.account.R.id.account_cancel_sms))))
                .perform(scrollTo(), replaceText(smsCode), closeSoftKeyboard());
        onView(allOf(withText(com.zhimu.slmp.account.R.string.account_cancel_submit),
                isDescendantOfA(withId(com.zhimu.slmp.account.R.id.account_cancel_bar)))).perform(click());
        onView(withText(com.zhimu.slmp.account.R.string.account_cancel_dialog_title)).inRoot(isDialog()).check(matches(isDisplayed()));
        screenshot("account-cancellation-confirm");
        String previousToken = core.sessionManager().getAccessToken(); // Memory only; never written to evidence/logs.
        assertNotNull(previousToken);
        assertEquals("注销前同一会话必须仍有效", 0, sessionCode(previousToken));

        // Only the already guarded disposable package/account receives these local cleanup fixtures.
        assertEquals("com.zhimu.slmp.accountqa", context.getPackageName());
        assertEquals(userId, core.sessionManager().currentUser().getUserId());
        List<File> privateFiles = new ArrayList<>();
        String privateFileName = "accountqa-" + userId + ".txt";
        privateFiles.add(writePrivateQaFile(context.getCacheDir(), "voice/" + privateFileName));
        privateFiles.add(writePrivateQaFile(context.getCacheDir(), "capture/" + privateFileName));
        privateFiles.add(writePrivateQaFile(context.getCacheDir(), "upload/" + privateFileName));
        privateFiles.add(writePrivateQaFile(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), privateFileName));
        core.appSettings().setLastPhone("qa-stale-phone"); // Deliberately not 11 digits: A09 keeps using local A02.
        assertEquals("qa-stale-phone", core.appSettings().getLastPhone());
        FormDraftEntity draft = new FormDraftEntity();
        draft.pageCode = "QA_ACCOUNT_CANCEL";
        draft.farmId = farmId;
        draft.bizKey = "accountqa-user-" + userId;
        draft.json = "{\"user_id\":" + userId + ",\"qa\":true}";
        draft.updatedAt = System.currentTimeMillis();
        assertNull("不得覆盖其他本地草稿", core.database().formDraftDao().get(draft.pageCode, farmId, draft.bizKey).blockingGet());
        core.database().formDraftDao().save(draft).blockingAwait();
        assertEquals(draft.json, core.database().formDraftDao().get(draft.pageCode, farmId, draft.bizKey).blockingGet().json);
        File existingScreenshot = new File(directory(), "account-cancellation-confirm.png");
        long screenshotSize = existingScreenshot.length();
        assertTrue("注销前的真实确认截图必须存在", existingScreenshot.isFile() && screenshotSize > 0);

        onView(withText(com.zhimu.slmp.account.R.string.account_cancel_dialog_positive)).inRoot(isDialog()).perform(click());
        Activity login = waitForActivity(LoginActivity.class);
        awaitUi("注销成功必须清除本地登录态和农场上下文", () -> !core.sessionManager().isLoggedIn()
                && core.sessionManager().currentUser() == null && core.farmContext().current() == null);
        onView(withId(com.zhimu.slmp.account.R.id.account_login_submit)).check(matches(isDisplayed()));
        for (File privateFile : privateFiles) assertFalse("注销后个人临时文件仍存在：" + privateFile.getName(), privateFile.exists());
        assertNull("注销后不能保留上次登录手机号", core.appSettings().getLastPhone());
        assertTrue("注销后必须清除本账号的Room草稿", core.database().formDraftDao()
                .get(draft.pageCode, farmId, draft.bizKey).isEmpty().blockingGet());
        assertNull(core.sessionManager().getAccessToken());
        assertNull(core.sessionManager().getRefreshToken());
        assertNull(core.sessionManager().getRecoveryInfo());
        for (String key : new String[]{"access_token", "refresh_token", "user_json", SessionStore.KEY_FARM_CONTEXT}) {
            assertFalse("注销后持久化会话仍含 " + key, context.getSharedPreferences(SessionStore.FILE, Context.MODE_PRIVATE).contains(key));
        }
        assertTrue("清个人缓存不能删除QA截图目录", existingScreenshot.getParentFile().isDirectory());
        assertTrue("必须保留注销前的真实截图", existingScreenshot.isFile());
        assertEquals("原截图不能被清理或覆写", screenshotSize, existingScreenshot.length());
        int revokedCode = sessionCode(previousToken);
        assertTrue("注销前的旧accessToken必须立即失效", revokedCode == ErrorCodes.SESSION_INVALID
                || revokedCode == ErrorCodes.TOKEN_EXPIRED);
        screenshot("account-cancelled-login");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("user_id", userId);
        evidence.addProperty("farm_id", farmId);
        evidence.addProperty("invite_id", inviteId);
        evidence.addProperty("farm_access_after_quit", ErrorCodes.NOT_FARM_MEMBER);
        evidence.addProperty("old_session_after_cancel", revokedCode);
        evidence.addProperty("login_activity", login.getClass().getSimpleName());
        evidence.addProperty("private_files_removed", privateFiles.size());
        evidence.addProperty("last_phone_cleared", true);
        evidence.addProperty("qa_room_draft_cleared", true);
        evidence.addProperty("persisted_session_cleared", true);
        evidence.addProperty("earlier_screenshots_preserved", true);
        try (FileOutputStream out = new FileOutputStream(new File(directory(), "evidence.json"))) {
            out.write(evidence.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private File writePrivateQaFile(File root, String relativePath) throws Exception {
        assertNotNull("专用包必须有应用私有存储目录", root);
        File file = new File(root, relativePath);
        assertTrue("QA文件必须位于本应用提供的目录内", file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator));
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        assertTrue("不得覆盖已有个人文件", file.createNewFile());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(("accountqa cleanup fixture user " + userId).getBytes(StandardCharsets.UTF_8));
        }
        assertTrue("清理夹具必须实际写入", file.length() > 0);
        return file;
    }

    private MessageBriefVO invitation() {
        for (MessageBriefVO message : read(today.getMessages("MEMBER", null, false, 1, 100)).getList()) {
            if (message.isInvite() && message.getAction() != null && message.getAction().getActionId() == inviteId) return message;
        }
        return null;
    }

    private int invitationPosition(RecyclerView.Adapter<?> adapter) {
        if (adapter instanceof MessagePagingAdapter) {
            MessagePagingAdapter messages = (MessagePagingAdapter) adapter;
            for (int i = 0; i < messages.getItemCount(); i++) {
                MessageRow row = messages.peek(i);
                MessageBriefVO message = row == null ? null : row.getMessage();
                if (message != null && message.getAction() != null && message.getAction().getActionId() == inviteId) return i;
            }
        } else if (adapter instanceof ConcatAdapter) {
            int offset = 0;
            for (RecyclerView.Adapter<?> child : ((ConcatAdapter) adapter).getAdapters()) {
                int found = invitationPosition(child);
                if (found >= 0) return offset + found;
                offset += child.getItemCount();
            }
        }
        return -1;
    }

    private void assertNoFarmCancellationImpact(CancellationPreviewVO preview) {
        assertTrue("专用账号不能拥有农场，禁止注销连带删除农场", preview.getOwnedFarms().isEmpty());
        assertTrue("注销前必须已退出唯一测试场，禁止影响其他成员关系", preview.getExitFarms().isEmpty());
    }

    private int sessionCode(String token) throws Exception {
        Request request = new Request.Builder().url(core.retrofit().baseUrl().resolve("api/v1/auth/session"))
                .header("X-Client", "APP_ANDROID")
                .header("Authorization", "Bearer " + token).get().build();
        try (Response response = sessionProbe.newCall(request).execute()) {
            assertNotNull("真实会话接口必须返回响应", response.body());
            JsonObject body = JsonParser.parseString(response.body().string()).getAsJsonObject();
            assertTrue("真实会话接口必须返回业务状态", body.has("code"));
            return body.get("code").getAsInt();
        }
    }

    private <T> T read(Single<ApiResponse<T>> call) { return call.compose(RxResult.<T>unwrap()).blockingGet(); }
    private static String required(Bundle args, String name) {
        String value = args.getString(name);
        assertTrue("必须显式提供专用夹具参数 " + name, value != null && !value.trim().isEmpty());
        return value.trim();
    }
    private Activity open(Class<? extends Activity> type) {
        Activity activity = instrumentation.startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(activity);
        return activity;
    }
    private Activity waitForActivity(Class<? extends Activity> type) {
        AtomicReference<Activity> found = new AtomicReference<>();
        awaitUi("未进入真实页面 " + type.getSimpleName(), () -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (type.isInstance(activity)) { found.set(activity); return true; }
            }
            return false;
        });
        opened.add(found.get());
        return found.get();
    }
    private static boolean shown(Activity activity, int id) {
        View view = activity.findViewById(id);
        return view != null && view.isShown() && view.isEnabled();
    }
    private void awaitUi(String message, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail(message);
    }
    private void onMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() instanceof AssertionError) throw (AssertionError) failure.get();
        if (failure.get() != null) throw new AssertionError("读取真实UI失败", failure.get());
    }
    private File directory() {
        File directory = new File(context.getExternalFilesDir(null), "account-membership");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        return directory;
    }
    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        try { instrumentation.getUiAutomation().waitForIdle(500, 5000); }
        catch (java.util.concurrent.TimeoutException ignored) { }
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(directory(), name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
}
