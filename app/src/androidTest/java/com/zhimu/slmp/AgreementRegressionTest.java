package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.appbar.MaterialToolbar;
import com.zhimu.slmp.account.ui.about.AgreementActivity;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.common.app.PrivacyDialogFragment;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.router.RouterPath;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real first-launch dialog -> live agreement API -> hosted HTML, without accepting privacy. */
@RunWith(AndroidJUnit4.class)
public class AgreementRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private SharedPreferences settings;
    private boolean hadAgreed, agreed, hadVersion;
    private String version;

    @Before public void prepareFirstLaunch() {
        settings = context.getSharedPreferences(AppSettings.FILE, Context.MODE_PRIVATE);
        hadAgreed = settings.contains("privacy_agreed");
        agreed = settings.getBoolean("privacy_agreed", false);
        hadVersion = settings.contains("privacy_version");
        version = settings.getString("privacy_version", null);
        settings.edit().remove("privacy_agreed").remove("privacy_version").commit();
    }

    @After public void restoreSettings() {
        instrumentation.runOnMainSync(() -> {
            for (Stage stage : new Stage[]{Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.PAUSED, Stage.STOPPED}) {
                for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))) {
                    if (activity instanceof SplashActivity || activity instanceof AgreementActivity) activity.finish();
                }
            }
        });
        SharedPreferences.Editor edit = settings.edit();
        if (hadAgreed) edit.putBoolean("privacy_agreed", agreed); else edit.remove("privacy_agreed");
        if (hadVersion) edit.putString("privacy_version", version); else edit.remove("privacy_version");
        edit.commit();
    }

    @Test public void firstLaunchLinksOpenDistinctDocumentsAndReturnWithoutConsent() {
        instrumentation.startActivitySync(new Intent(context, SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for (String title : new String[]{"用户协议", "隐私政策"}) {
            await(() -> resumed() instanceof SplashActivity, 10_000);
            instrumentation.runOnMainSync(() -> {
                SplashActivity splash = (SplashActivity) resumed();
                PrivacyDialogFragment dialog = (PrivacyDialogFragment) splash.getSupportFragmentManager()
                        .findFragmentByTag("common_core_privacy_dialog");
                assertNotNull(dialog);
                TextView text = dialog.requireDialog().findViewById(android.R.id.message);
                Spanned spans = (Spanned) text.getText();
                int start = spans.toString().indexOf("《" + title + "》");
                ClickableSpan[] links = spans.getSpans(start, start + title.length() + 2, ClickableSpan.class);
                assertEquals(1, links.length);
                links[0].onClick(text);
            });
            await(() -> resumed() instanceof AgreementActivity, 10_000);
            AgreementActivity activity = (AgreementActivity) resumed();
            await(() -> {
                StateLayout state = activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_state);
                return state.getStatus() == UiState.Status.CONTENT;
            }, 45_000);
            instrumentation.runOnMainSync(() -> {
                MaterialToolbar bar = activity.findViewById(com.zhimu.slmp.base.R.id.base_toolbar);
                WebView web = activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_web);
                assertEquals(title, bar.getTitle().toString());
                assertEquals(title + " - 智牧通", web.getTitle());
                assertEquals("http://8.208.12.217/agreements/" + (title.equals("隐私政策") ? "privacy" : "user")
                        + "-2026-10-26.html", web.getUrl());
                assertFalse(web.getSettings().getJavaScriptEnabled());
                assertFalse(settings.getBoolean("privacy_agreed", false));
                activity.getOnBackPressedDispatcher().onBackPressed();
            });
        }
        await(() -> resumed() instanceof SplashActivity, 10_000);
        assertFalse(settings.getBoolean("privacy_agreed", false));
    }

    @Test public void missingDocumentShowsRetryInsteadOfWebErrorOrLoginPage() {
        Intent intent = new Intent(context, AgreementActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RouterPath.EXTRA_TITLE, "用户协议")
                .putExtra(RouterPath.EXTRA_URL, "http://8.208.12.217/agreements/qa-missing-document.html");
        AgreementActivity activity = (AgreementActivity) instrumentation.startActivitySync(intent);
        await(() -> ((StateLayout) activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_state))
                .getStatus() == UiState.Status.ERROR, 45_000);
        onView(withText("重新加载")).perform(click());
        await(() -> ((StateLayout) activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_state))
                .getStatus() == UiState.Status.ERROR, 45_000);
        instrumentation.runOnMainSync(() -> assertFalse(activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_web).isShown()));
        assertFalse(settings.getBoolean("privacy_agreed", false));
    }

    private Activity resumed() {
        AtomicReference<Activity> activity = new AtomicReference<>();
        Runnable read = () -> {
            for (Activity value : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) activity.set(value);
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) read.run();
        else instrumentation.runOnMainSync(read);
        return activity.get();
    }

    private void await(BooleanSupplier condition, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < end) {
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> result.set(condition.getAsBoolean()));
            if (result.get()) return;
            SystemClock.sleep(150);
        }
        AtomicReference<String> detail = new AtomicReference<>("no resumed activity");
        instrumentation.runOnMainSync(() -> {
            Activity activity = resumed();
            if (activity instanceof AgreementActivity) {
                StateLayout state = activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_state);
                WebView web = activity.findViewById(com.zhimu.slmp.account.R.id.account_agreement_web);
                detail.set("state=" + state.getStatus() + ", title=" + web.getTitle() + ", progress=" + web.getProgress());
            } else if (activity != null) detail.set(activity.getClass().getSimpleName());
        });
        fail("Timed out waiting for agreement UI: " + detail.get());
    }
}
