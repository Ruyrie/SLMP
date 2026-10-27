package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.data.AiApi;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.ui.chat.AiMessageAdapter;
import com.zhimu.slmp.ai.ui.chat.ChatItem;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.network.RxResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** One real model request: visible public progress, automatic collapse, and independent sources. */
@RunWith(AndroidJUnit4.class)
public class AiProgressUiRegressionTest {
    private static final String QUESTION = "怎样整理养殖场的工具柜？请用3点简要回答。";
    private static final String OLD_SOURCE_PREFIX = "未找到平台知识依据，仅供参考。";
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private MainActivity page;
    private RecyclerView list;
    private ViewTreeObserver.OnPreDrawListener progressObserver;
    private final AtomicReference<String> observedProgress = new AtomicReference<>();

    @Before public void localRealProviderOnly() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        assertFalse("验收必须经过本地服务端AI接口，不能切为App直连模式", AiChatClient.getDefault().isEnabled());
        core.sessionManager().saveToken(core.retrofit().create(AccountApi.class).loginByPassword(new PasswordLoginRequest(
                "13800006620", "Zmt12345", new DeviceInfo("pixel9-ai-progress", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet());
        core.farmContext().update(core.retrofit().create(FarmApi.class).switchFarm(1001L)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet());
    }

    @After public void closePage() {
        if (instrumentation == null) return;
        onMain(() -> {
            if (list != null && progressObserver != null && list.getViewTreeObserver().isAlive()) {
                list.getViewTreeObserver().removeOnPreDrawListener(progressObserver);
            }
            if (page != null) page.finish();
        });
    }

    @Test public void realAnswerShowsProgressThenCollapsesSummaryAndSeparatesSourceNote() throws Exception {
        Bundle args = new Bundle();
        args.putString(RouterPath.EXTRA_ASSISTANT_MODE, AiExtras.MODE_CHAT);
        args.putBoolean(RouterPath.EXTRA_NEW_SESSION, true);
        page = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(RouterPath.EXTRA_MAIN_TAB, TabRegistry.ASSISTANT)
                .putExtra(RouterPath.EXTRA_MAIN_TAB_ARGS, args));
        awaitUi(20000, "真实AI页面未加载", () -> {
            list = page.findViewById(com.zhimu.slmp.ai.R.id.ai_chat_list);
            return list != null && list.getAdapter() instanceof AiMessageAdapter && list.isShown();
        });
        AtomicBoolean voiceMode = new AtomicBoolean();
        onMain(() -> {
            View keyboard = page.findViewById(com.zhimu.slmp.ai.R.id.ai_chat_keyboard);
            voiceMode.set(keyboard != null && keyboard.isShown());
            // Observe actual rendered frames so a short real progress state cannot be missed by Espresso's idle wait.
            progressObserver = () -> {
                ChatItem item = answerItem();
                TextView progress = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_process_text);
                if (item != null && (item.state == ChatItem.AnswerState.THINKING || item.state == ChatItem.AnswerState.STREAMING)
                        && progress != null && progress.getVisibility() == View.VISIBLE
                        && !progress.getText().toString().trim().isEmpty() && progress.getGlobalVisibleRect(new Rect())) {
                    observedProgress.compareAndSet(null, progress.getText().toString());
                }
                return true;
            };
            list.getViewTreeObserver().addOnPreDrawListener(progressObserver);
        });
        if (voiceMode.get()) onView(withId(com.zhimu.slmp.ai.R.id.ai_chat_keyboard)).perform(click());
        onView(withId(com.zhimu.slmp.ai.R.id.ai_chat_input)).perform(replaceText(QUESTION), closeSoftKeyboard());
        onView(withId(com.zhimu.slmp.ai.R.id.ai_chat_action)).perform(click());
        awaitUi(30000, "生成期间未渲染真实公开处理进度", () -> {
            failOnProviderError();
            return observedProgress.get() != null;
        });
        screenshot("ai-progress-observed");
        AtomicReference<AiMessageVO> completed = new AtomicReference<>();
        awaitUi(180000, "真实AI服务未在180秒内完成，不重试或伪造结果", () -> {
            failOnProviderError();
            ChatItem item = answerItem();
            if (item == null || item.state != ChatItem.AnswerState.DONE || item.message == null) return false;
            completed.set(item.message);
            return true;
        });
        AiMessageVO result = completed.get();
        assertEquals("必须是真实生成完成的回答", AiCodes.STATUS_ANSWERED, result.getStatus());
        assertTrue("真实回答应有持久化消息ID", result.getId() > 0 && result.getSessionId() > 0);
        assertNotNull("完成事件必须提供公开摘要", result.getAnalysisSummary());
        assertFalse(result.getAnalysisSummary().trim().isEmpty());
        AiApi api = core.retrofit().create(AiApi.class);
        AiMessageVO saved = api.message(result.getId()).compose(RxResult.<AiMessageVO>unwrap()).blockingGet();
        AiMessageSliceVO session = api.messages(result.getSessionId(), null, null, 10)
                .compose(RxResult.<AiMessageSliceVO>unwrap()).blockingGet();
        assertEquals("此验收只提交一轮真实请求", 1, session.getList().size());
        assertEquals(QUESTION, saved.getQuestionText());
        assertEquals(AiCodes.STATUS_ANSWERED, saved.getStatus());
        assertNotNull("服务端必须提供面向用户的公开摘要", saved.getAnalysisSummary());
        assertFalse(saved.getAnalysisSummary().trim().isEmpty());
        scrollToAnswerTop();
        awaitUi(10000, "完成后公开摘要未默认折叠", () -> {
            View toggle = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_process_toggle);
            View summary = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_process_text);
            TextView answer = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_text);
            return toggle != null && toggle.isShown() && summary != null && summary.getVisibility() == View.GONE
                    && answer != null && !answer.getText().toString().trim().isEmpty();
        });
        AtomicReference<String> body = new AtomicReference<>();
        onMain(() -> body.set(((TextView) page.findViewById(
                com.zhimu.slmp.ai.R.id.ai_answer_text)).getText().toString()));
        assertFalse("来源不足固定前缀不能挤占正文开头", body.get().startsWith(OLD_SOURCE_PREFIX));
        screenshot("ai-answer-summary-collapsed");
        onView(withId(com.zhimu.slmp.ai.R.id.ai_answer_process_toggle)).perform(click());
        awaitUi(10000, "用户点开后必须看到服务端公开摘要", () -> {
            TextView summary = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_process_text);
            return summary != null && summary.getVisibility() == View.VISIBLE
                    && summary.getText().toString().contains(result.getAnalysisSummary());
        });
        onView(withId(com.zhimu.slmp.ai.R.id.ai_answer_process_text)).check(matches(isDisplayed()));
        screenshot("ai-answer-summary-expanded");
        // Preserve genuine citations if retrieval finds evidence; never manufacture a NO_BASIS fixture.
        if (AiCodes.ANSWER_NO_BASIS.equals(saved.getAnswerType())) {
            assertTrue("无依据回答不能附上伪造引用", saved.getCitations().isEmpty());
            assertNotNull("NO_BASIS须由服务端独立提供source_note", saved.getSourceNote());
            assertFalse(saved.getSourceNote().trim().isEmpty());
        }
        onMain(() -> {
            TextView note = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_source_note);
            assertNotNull(note);
            if (saved.getSourceNote() != null && !saved.getSourceNote().trim().isEmpty()) {
                assertEquals(View.VISIBLE, note.getVisibility());
                assertEquals(context.getString(com.zhimu.slmp.ai.R.string.ai_chat_source_note, saved.getSourceNote()),
                        note.getText().toString());
                assertFalse("来源说明必须独立于答案正文", body.get().startsWith(saved.getSourceNote()));
            } else {
                assertEquals(View.GONE, note.getVisibility());
            }
            android.view.ViewGroup citations = page.findViewById(com.zhimu.slmp.ai.R.id.ai_answer_sources);
            assertEquals("真实引用必须保留", saved.getCitations().size(), citations.getChildCount());
        });
        JsonObject evidence = new JsonObject();
        evidence.addProperty("message_id", saved.getId());
        evidence.addProperty("session_id", saved.getSessionId());
        evidence.addProperty("question", QUESTION);
        evidence.addProperty("observed_progress", observedProgress.get());
        evidence.addProperty("answer_type", saved.getAnswerType());
        evidence.addProperty("analysis_summary", saved.getAnalysisSummary());
        evidence.addProperty("source_note", saved.getSourceNote());
        evidence.addProperty("citation_count", saved.getCitations().size());
        evidence.addProperty("answer_text", body.get());
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), "ai-progress-evidence.json"))) {
            output.write(evidence.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private ChatItem answerItem() {
        if (list == null || !(list.getAdapter() instanceof AiMessageAdapter)) return null;
        for (ChatItem item : ((AiMessageAdapter) list.getAdapter()).getCurrentList()) {
            if (item.type == ChatItem.Type.ANSWER) return item;
        }
        return null;
    }

    private void failOnProviderError() {
        ChatItem item = answerItem();
        if (item != null && (item.state == ChatItem.AnswerState.ERROR || item.state == ChatItem.AnswerState.SEND_FAILED
                || item.state == ChatItem.AnswerState.STOPPED)) {
            fail("真实AI服务未成功完成：" + item.statusText);
        }
    }

    private void scrollToAnswerTop() {
        onMain(() -> {
            AiMessageAdapter adapter = (AiMessageAdapter) list.getAdapter();
            for (int position = 0; position < adapter.getCurrentList().size(); position++) {
                if (adapter.getCurrentList().get(position).type == ChatItem.Type.ANSWER) {
                    ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
                    return;
                }
            }
        });
        instrumentation.waitForIdleSync();
    }

    private void awaitUi(long timeoutMs, String message, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail(message);
    }

    private void onMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
        });
        Throwable error = failure.get();
        if (error instanceof AssertionError) throw (AssertionError) error;
        if (error instanceof RuntimeException) throw (RuntimeException) error;
        if (error instanceof Error) throw (Error) error;
        if (error != null) throw new AssertionError("读取真实UI失败", error);
    }

    private void screenshot(String name) throws Exception {
        Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }
}
