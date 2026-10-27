package com.zhimu.slmp;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonObject;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.herd.ui.archive.BatchArchiveActivity;
import com.zhimu.slmp.herd.ui.scan.BatchScanActivity;
import com.zhimu.slmp.herd.util.BatchNoValidator;
import com.zhimu.slmp.herd.util.QrBitmapEncoder;
import com.zhimu.slmp.media.ui.camera.CameraActivity;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Url;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Local QA only. Run individual methods: prepareCameraFixture writes a real server QR into
 * externalFilesDir/batch-qr-regression. Feed it into the emulator virtual scene before
 * cameraFramesOpenTheCorrectBatch; no result injection or analyzer callback bypass is used.
 * Run manualInputRecoversFromInvalidAndUnknownBatch before placing a readable QR in the camera.
 * Arguments: qaBatchId (3001), qaFarmId (1001), qaStartFarmId (same farm; use 1002 for a cross-farm scan).
 */
@RunWith(AndroidJUnit4.class)
public class BatchQrFunctionalTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private long batchId;
    private long targetFarmId;
    private long startFarmId;

    @Before public void useIsolatedBackend() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        Bundle args = InstrumentationRegistry.getArguments();
        batchId = Long.parseLong(args.getString("qaBatchId", "3001"));
        targetFarmId = Long.parseLong(args.getString("qaFarmId", "1001"));
        startFarmId = Long.parseLong(args.getString("qaStartFarmId", String.valueOf(targetFarmId)));
        AuthTokenVO token = core.retrofit().create(AccountApi.class)
                .loginByPassword(new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-batch-qr-regression", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        switchFarm(targetFarmId);
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        instrumentation.waitForIdleSync();
    }

    public interface QaApi {
        @GET Single<ApiResponse<JsonObject>> get(@Url String url);
    }

    private JsonObject api(String path) {
        return core.retrofit().create(QaApi.class).get("api/v1" + path)
                .compose(RxResult.<JsonObject>unwrap()).blockingGet();
    }

    private void switchFarm(long farmId) {
        FarmContextVO farm = core.retrofit().create(FarmApi.class).switchFarm(farmId)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
        core.farmContext().update(farm);
    }

    @Test public void prepareCameraFixture() throws Exception {
        JsonObject batch = api("/batches/" + batchId);
        String content = batch.get("qr_content").getAsString();
        assertFalse(content.isEmpty());
        File folder = outputFolder();
        Bitmap image = QrBitmapEncoder.encode(content, 768);
        try (FileOutputStream out = new FileOutputStream(new File(folder, "scene-" + batchId + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
        JsonObject fixture = new JsonObject();
        fixture.addProperty("batch_id", batchId);
        fixture.addProperty("farm_id", targetFarmId);
        fixture.addProperty("batch_no", batch.get("batch_no").getAsString());
        fixture.addProperty("qr_content", content);
        try (FileOutputStream out = new FileOutputStream(new File(folder, "fixture.json"))) {
            out.write(fixture.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test public void cameraFramesOpenTheCorrectBatch() throws Exception {
        String batchNo = api("/batches/" + batchId).get("batch_no").getAsString();
        switchFarm(startFarmId);
        Instrumentation.ActivityMonitor camera = instrumentation.addMonitor(CameraActivity.class.getName(), null, false);
        try {
            openScanner();
            long timeout = Long.parseLong(InstrumentationRegistry.getArguments().getString("qaCameraTimeoutMs", "60000"));
            Activity archive = waitFor(BatchArchiveActivity.class, Math.min(300000, timeout));
            capture("camera-scan-result");
            assertNotNull("A real camera frame should open the batch archive within 60 seconds", archive);
            assertTrue("The actual CameraActivity must have been launched", camera.getHits() > 0);
            assertEquals(batchId, archive.getIntent().getLongExtra(RouterPath.EXTRA_BATCH_ID, -1));
            assertEquals(targetFarmId, core.farmContext().current().getFarmId());
            if (startFarmId != targetFarmId) {
                assertEquals(startFarmId, archive.getIntent().getLongExtra(RouterPath.EXTRA_FROM_FARM_ID, -1));
            }
            onView(withText("批次 " + batchNo)).check(matches(isDisplayed()));
        } finally { instrumentation.removeMonitor(camera); }
    }

    @Test public void manualInputRecoversFromInvalidAndUnknownBatch() throws Exception {
        String batchNo = api("/batches/" + batchId).get("batch_no").getAsString();
        JsonObject unknown = api("/batches/lookup?batch_no=ZM0000-000");
        assertEquals("The QA fixture must not contain the unknown test batch", "NOT_FOUND", unknown.get("result").getAsString());
        openScanner();
        assertNotNull(waitFor(CameraActivity.class, 15000));
        onView(withId(com.zhimu.slmp.media.R.id.media_manual)).perform(click());
        onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).perform(replaceText("BAD"), closeSoftKeyboard());
        onView(withText("打开档案")).perform(click());
        onView(withText(BatchNoValidator.MSG_INVALID)).check(matches(isDisplayed()));
        onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).perform(replaceText("ZM0000-000"), closeSoftKeyboard());
        onView(withText("打开档案")).perform(click());
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withText(unknown.get("message").getAsString())).check(matches(isDisplayed()));
                break;
            } catch (androidx.test.espresso.NoMatchingViewException | AssertionError pending) {
                SystemClock.sleep(200);
            }
        }
        onView(withText(unknown.get("message").getAsString())).check(matches(isDisplayed()));
        capture("manual-invalid-and-not-found");
        onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).perform(replaceText(batchNo.toLowerCase()), closeSoftKeyboard());
        onView(withText("打开档案")).perform(click());
        Activity archive = waitFor(BatchArchiveActivity.class, 20000);
        assertNotNull("A corrected batch number should open the archive", archive);
        assertEquals(batchId, archive.getIntent().getLongExtra(RouterPath.EXTRA_BATCH_ID, -1));
        capture("manual-open-result");
    }

    private void openScanner() {
        instrumentation.startActivitySync(new Intent(context, BatchScanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private Activity waitFor(Class<? extends Activity> type, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        AtomicReference<Activity> result = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity)) result.set(activity);
                }
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(200);
        }
        return null;
    }

    private File outputFolder() {
        File folder = new File(context.getExternalFilesDir(null), "batch-qr-regression");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        return folder;
    }

    private void capture(String name) throws Exception {
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream out = new FileOutputStream(new File(outputFolder(), name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { screenshot.recycle(); }
    }
}
