package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.inspect.ui.diagnosis.DiagnosisDetailActivity;
import com.zhimu.slmp.inspect.ui.report.AbnormalReportActivity;
import com.zhimu.slmp.inspect.ui.report.AbnormalReportViewModel;
import com.zhimu.slmp.media.ui.camera.CameraActivity;
import com.zhimu.slmp.network.RxResult;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real CameraX capture -> explicit confirmation -> upload -> actual report submission. */
@RunWith(AndroidJUnit4.class)
public class CameraCaptureRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;

    @Before public void isolatedLogin() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        AuthTokenVO token = core.retrofit().create(AccountApi.class).loginByPassword(
                new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-camera-regression", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        core.farmContext().update(core.retrofit().create(FarmApi.class).switchFarm(1001L)
                .compose(RxResult.unwrap()).blockingGet());
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
        instrumentation.waitForIdleSync();
    }

    @Test public void capturedPhotoCanBeConfirmedAndSubmittedWithTheReport() throws Exception {
        captureReport(false);
    }

    @Test public void recordedVideoCanBeConfirmedAndSubmittedWithTheReport() throws Exception {
        captureReport(true);
    }

    private void captureReport(boolean video) throws Exception {
        Activity report = instrumentation.startActivitySync(new Intent(context, AbnormalReportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RouterPath.EXTRA_PEN_ID, 2003L));
        await("report form", () -> {
            TextView batch = report.findViewById(com.zhimu.slmp.inspect.R.id.inspect_batch_input);
            return batch != null && batch.length() > 0;
        }, 20000);
        AtomicReference<AbnormalReportViewModel> model = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> model.set(new ViewModelProvider(
                (AbnormalReportActivity) report).get(AbnormalReportViewModel.class)));
        AbnormalReportViewModel vm = model.get();
        int before = vm.getMedia().current().size();
        assertTrue("The fixture must have room for one capture", before < 6);
        onView(allOf(withId(com.zhimu.slmp.media.R.id.media_add),
                isDescendantOfA(withId(com.zhimu.slmp.inspect.R.id.inspect_media))))
                .perform(scrollTo(), click());
        onView(withText(video ? "录像" : "拍照")).perform(click());
        CameraActivity camera = (CameraActivity) activity(CameraActivity.class, 15000);
        assertNotNull("Camera must open", camera);
        await("real CameraX preview", () -> {
            PreviewView preview = camera.findViewById(com.zhimu.slmp.media.R.id.media_preview);
            return preview != null && preview.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING;
        }, 20000);
        onView(withId(com.zhimu.slmp.media.R.id.media_shutter)).perform(click());
        if (video) {
            await("actual recording started", () -> camera.findViewById(
                    com.zhimu.slmp.media.R.id.media_timer).getVisibility() == android.view.View.VISIBLE, 10000);
            SystemClock.sleep(3500);
            onView(withId(com.zhimu.slmp.media.R.id.media_shutter)).perform(click());
        }
        await("explicit capture confirmation", () -> camera.findViewById(
                com.zhimu.slmp.media.R.id.media_done).isEnabled(), 20000);
        screenshot(video ? "camera-after-video" : "camera-after-photo");
        onView(withId(com.zhimu.slmp.media.R.id.media_done)).perform(click());
        assertSame("Confirm must return to the original form", report, activity(AbnormalReportActivity.class, 10000));
        await("captured photo in form", () -> vm.getMedia().current().size() == before + 1, 10000);
        await("real photo upload completes", () -> !vm.getMedia().hasUploading()
                && !vm.getMedia().hasFailed() && vm.getMedia().getFileIds().size() >= before + 1, 45000);
        List<Long> fileIds = new ArrayList<>(vm.getMedia().getFileIds());
        screenshot(video ? "report-with-confirmed-video" : "report-with-confirmed-photo");
        String description = "本地三端验收：实际" + (video ? "录像" : "拍照") + "确认上传 " + System.currentTimeMillis();
        onView(withId(com.zhimu.slmp.inspect.R.id.inspect_sick_input)).perform(replaceText("1"), closeSoftKeyboard());
        onView(allOf(isAssignableFrom(android.widget.EditText.class),
                isDescendantOfA(withId(com.zhimu.slmp.inspect.R.id.inspect_description_layout))))
                .perform(scrollTo(), replaceText(description), closeSoftKeyboard());
        onView(withText("提交并诊断")).perform(click());
        Activity diagnosis = activity(DiagnosisDetailActivity.class, 25000);
        assertNotNull("Report with camera photo must submit without crashing", diagnosis);
        screenshot(video ? "diagnosis-after-video-report" : "diagnosis-after-camera-report");
        JsonObject result = new JsonObject();
        result.addProperty("description", description);
        result.addProperty("media_type", video ? "VIDEO" : "IMAGE");
        JsonArray ids = new JsonArray(); fileIds.forEach(ids::add); result.add("file_ids", ids);
        JsonObject extras = new JsonObject();
        Bundle bundle = diagnosis.getIntent().getExtras();
        if (bundle != null) for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof Number) extras.addProperty(key, (Number) value);
            else if (value instanceof String) extras.addProperty(key, (String) value);
        }
        result.add("diagnosis_extras", extras);
        File output = new File(folder(), video ? "video-result.json" : "camera-result.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(result.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void await(String label, BooleanSupplier condition, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            SystemClock.sleep(150);
        }
        fail("Timed out: " + label);
    }
    private Activity activity(Class<? extends Activity> type, long timeout) {
        AtomicReference<Activity> found = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (type.isInstance(a)) found.set(a);
            });
            if (found.get() != null) return found.get();
            SystemClock.sleep(150);
        }
        return null;
    }
    private File folder() {
        File folder = new File(context.getExternalFilesDir(null), "camera-regression");
        if (!folder.exists()) assertTrue(folder.mkdirs());
        return folder;
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream stream = new FileOutputStream(new File(folder(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
