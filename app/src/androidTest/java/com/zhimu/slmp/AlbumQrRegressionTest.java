package com.zhimu.slmp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonObject;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.herd.ui.archive.BatchArchiveActivity;
import com.zhimu.slmp.herd.ui.scan.BatchScanActivity;
import com.zhimu.slmp.herd.ui.scan.BatchScanViewModel;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.HerdApi;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;
import com.zhimu.slmp.herd.ui.trace.BatchTraceActivity;
import com.zhimu.slmp.herd.util.QrBitmapEncoder;
import com.zhimu.slmp.media.scan.QrImageDecoder;
import com.zhimu.slmp.media.ui.camera.CameraActivity;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Url;

/** Real system Photo Picker -> URI decoding -> business lookup -> batch archive. No result injection. */
@RunWith(AndroidJUnit4.class)
public class AlbumQrRegressionTest {
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private long batchId;
    private long targetFarm;
    private long startFarm;
    private String expectedBatchNo;
    private final List<Uri> temporaryImages = new ArrayList<>();

    public interface ReadApi {
        @GET Single<ApiResponse<JsonObject>> get(@Url String path);
    }

    @Before public void isolatedAlbumFixture() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        assertEquals("先在instrument启动前临时撤回相机权限，避免虚拟场景旧码抢先识别", PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA));
        Bundle args = InstrumentationRegistry.getArguments();
        batchId = Long.parseLong(args.getString("qaBatchId", "3001"));
        targetFarm = Long.parseLong(args.getString("qaFarmId", "1001"));
        startFarm = Long.parseLong(args.getString("qaStartFarmId", "1002"));
        AuthTokenVO token = core.retrofit().create(AccountApi.class).loginByPassword(new PasswordLoginRequest(
                "13800006620", "Zmt12345", new DeviceInfo("pixel9-album-qr", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        switchFarm(targetFarm);
    }

    @After public void cleanOnlyTestImagesAndActivities() {
        if (context == null) return;
        for (Uri uri : temporaryImages) context.getContentResolver().delete(uri, null, null);
        AccessibilityNodeInfo window = instrumentation.getUiAutomation().getRootInActiveWindow();
        String windowPackage = window == null || window.getPackageName() == null ? "" : window.getPackageName().toString();
        if (isSystemPicker(windowPackage)) {
            instrumentation.getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            SystemClock.sleep(300);
        }
        instrumentation.runOnMainSync(() -> {
            for (Stage stage : new Stage[]{Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.PAUSED, Stage.STOPPED}) {
                for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))) {
                    if (activity instanceof CameraActivity || activity instanceof BatchScanActivity
                            || activity instanceof BatchArchiveActivity || activity instanceof BatchTraceActivity) activity.finish();
                }
            }
        });
    }

    @Test public void galleryBatchImageOpensTheAuthorizedArchiveAndSwitchesFarm() throws Exception {
        String content = batch().get("qr_content").getAsString();
        String name = image(QrBitmapEncoder.encode(content, 768), false, true);
        openScanner();
        pickLatestImage(name);
        assertBatchArchive();
        screenshot("album-batch-archive");
    }

    @Test public void galleryPublicQrOpensTraceWithoutJoiningOrSwitchingFarm() throws Exception {
        String qr = InstrumentationRegistry.getArguments().getString("qaPublicTraceQr");
        assertNotNull("传入来自未加入养殖场的有效公开二维码 qaPublicTraceQr", qr);
        // This existing employee belongs only to farm 1001. Other cases keep their owner login.
        core.sessionManager().saveToken(core.retrofit().create(AccountApi.class).loginByPassword(new PasswordLoginRequest(
                "13700005678", "Zmt12345", new DeviceInfo("pixel9-album-public", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet());
        startFarm = 1001L;
        switchFarm(startFarm);
        HerdApi api = core.retrofit().create(HerdApi.class);
        BatchLookupVO lookup = api.lookup(qr, null).compose(RxResult.<BatchLookupVO>unwrap()).blockingGet();
        assertEquals("测试二维码必须属于当前账号无管理权限的批次", BatchLookupVO.PUBLIC_TRACE, lookup.getResult());
        assertNotNull(lookup.getTraceToken());
        BatchTraceVO trace = api.getPublicTrace(lookup.getTraceToken())
                .compose(RxResult.<BatchTraceVO>unwrap()).blockingGet();
        String name = image(QrBitmapEncoder.encode(qr, 768), false, false);
        openScanner();
        pickLatestImage(name);
        Activity tracePage = waitFor(BatchTraceActivity.class, 20000);
        assertNotNull("真实相册选择后应打开公开溯源页", tracePage);
        assertEquals(lookup.getTraceToken(), tracePage.getIntent().getStringExtra(HerdExtras.EXTRA_TRACE_TOKEN));
        waitForRenderedText(tracePage, com.zhimu.slmp.herd.R.id.herd_trace_batch_no, trace.getBatchNo());
        onView(withId(com.zhimu.slmp.herd.R.id.herd_trace_batch_no)).check(matches(withText(trace.getBatchNo())));
        onView(withId(com.zhimu.slmp.herd.R.id.herd_trace_farm)).check(matches(withText(trace.getFarmName())));
        assertEquals("公开溯源不能切换当前养殖场", startFarm, core.farmContext().current().getFarmId());
        assertNull("未授权批次不能进入内部档案", findActivity(BatchArchiveActivity.class));
        screenshot("album-public-trace");
    }

    @Test public void gallerySelectionSurvivesScannerRecreationAndExifRotation() throws Exception {
        String name = image(QrBitmapEncoder.encode(batch().get("qr_content").getAsString(), 768), true, true);
        CameraActivity camera = openScanner();
        onView(withId(com.zhimu.slmp.media.R.id.media_scan_album)).perform(click());
        waitForSystemPicker();
        instrumentation.runOnMainSync(camera::recreate);
        chooseLatestPhoto(name);
        assertBatchArchive();
        screenshot("album-exif-recreated");
    }

    @Test public void legacyPrintedBatchImageStillOpensTheAuthorizedArchive() throws Exception {
        String content = batch().get("qr_content").getAsString();
        String token = Uri.parse(content).getLastPathSegment();
        assertNotNull(token);
        String legacyContent = "https://web.zhimutong.example.com/s/b/" + token;
        String name = image(QrBitmapEncoder.encode(legacyContent, 768), false, false);
        openScanner();
        pickLatestImage(name);
        assertBatchArchive();
        screenshot("album-legacy-batch-archive");
    }

    @Test public void canceledAndQrFreeImagesRecoverThenARealBatchImageWorks() throws Exception {
        CameraActivity camera = openScanner();
        onView(withId(com.zhimu.slmp.media.R.id.media_scan_album)).perform(click());
        waitForSystemPicker();
        assertTrue(instrumentation.getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
        waitForText(camera, context.getString(com.zhimu.slmp.media.R.string.media_scan_album_canceled));
        Bitmap blank = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888);
        blank.eraseColor(Color.WHITE);
        pickLatestImage(image(blank, false, false));
        waitForText(camera, context.getString(com.zhimu.slmp.media.R.string.media_scan_album_no_qr));
        assertFalse(camera.isFinishing());
        screenshot("album-no-qr-retry");
        switchFarm(targetFarm);
        String content = batch().get("qr_content").getAsString();
        switchFarm(startFarm);
        pickLatestImage(image(QrBitmapEncoder.encode(content, 768), false, true));
        assertBatchArchive();
    }

    @Test public void unrelatedQrStillUsesExistingBatchValidation() throws Exception {
        String name = image(QrBitmapEncoder.encode("https://example.com/not-a-batch", 768), false, false);
        openScanner();
        pickLatestImage(name);
        Activity scanner = waitFor(BatchScanActivity.class, 15000);
        assertNotNull(scanner);
        waitForRenderedText(scanner, com.google.android.material.R.id.snackbar_text, BatchScanViewModel.MSG_NOT_BATCH_QR);
        onView(withText(BatchScanViewModel.MSG_NOT_BATCH_QR)).check(matches(isDisplayed()));
        assertEquals(startFarm, core.farmContext().current().getFarmId());
        assertNull("普通二维码不能被当作批次打开", findActivity(BatchArchiveActivity.class));
        screenshot("album-non-batch-rejected");
    }

    @Test public void brokenAndMissingImagesAreReadFailuresInsteadOfFakeQrResults() throws Exception {
        ContentValues values = values("slmp-broken-" + System.currentTimeMillis() + ".png", "image/png");
        Uri broken = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(broken);
        temporaryImages.add(broken);
        try (OutputStream output = context.getContentResolver().openOutputStream(broken)) {
            assertNotNull(output);
            output.write(new byte[]{1, 2, 3, 4});
        }
        publish(broken);
        try {
            QrImageDecoder.decode(context.getContentResolver(), broken);
            fail("损坏的图片必须报读取失败");
        } catch (IOException expected) { }
        try {
            QrImageDecoder.decode(context.getContentResolver(), Uri.parse("content://media/external/images/media/9223372036854775806"));
            fail("失效URI必须报读取失败");
        } catch (IOException expected) { }
    }

    private JsonObject batch() {
        JsonObject batch = core.retrofit().create(ReadApi.class).get("api/v1/batches/" + batchId)
                .compose(RxResult.<JsonObject>unwrap()).blockingGet();
        expectedBatchNo = batch.get("batch_no").getAsString();
        return batch;
    }

    private void switchFarm(long id) {
        FarmContextVO farm = core.retrofit().create(FarmApi.class).switchFarm(id)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
        core.farmContext().update(farm);
        instrumentation.waitForIdleSync();
    }

    private CameraActivity openScanner() {
        switchFarm(startFarm);
        instrumentation.startActivitySync(new Intent(context, BatchScanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        // This is the real permission prompt. Gallery decoding remains usable after declining camera access.
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo deny = find(root, node -> node.getViewIdResourceName() != null
                    && node.getViewIdResourceName().endsWith(":id/permission_deny_button"));
            if (deny != null) { clickNode(deny); break; }
            Activity camera = findActivity(CameraActivity.class);
            if (camera != null) break;
            SystemClock.sleep(100);
        }
        CameraActivity camera = (CameraActivity) waitFor(CameraActivity.class, 15000);
        assertNotNull("应进入真实扫码相机页", camera);
        onView(withId(com.zhimu.slmp.media.R.id.media_scan_album)).check(matches(isDisplayed()));
        return camera;
    }

    private String image(Bitmap bitmap, boolean exifRotated, boolean retain) throws Exception {
        String name = "slmp-album-" + System.currentTimeMillis() + (exifRotated ? ".jpg" : ".png");
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values(name, exifRotated ? "image/jpeg" : "image/png"));
        assertNotNull(uri);
        if (!retain) temporaryImages.add(uri);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            assertNotNull(output);
            assertTrue(bitmap.compress(exifRotated ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
        if (exifRotated) {
            try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "rw")) {
                assertNotNull(descriptor);
                ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                exif.saveAttributes();
            }
        }
        publish(uri);
        return name;
    }

    private ContentValues values(String name, String mime) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SLMP-QA");
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        return values;
    }

    private void publish(Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        assertEquals(1, context.getContentResolver().update(uri, values, null, null));
    }

    private void pickLatestImage(String name) throws Exception {
        // Espresso only idles the app. Wait for the system picker's dismissal animation too,
        // otherwise the first tap after Cancel can be consumed by the outgoing system window.
        instrumentation.getUiAutomation().waitForIdle(500, 5000);
        onView(withId(com.zhimu.slmp.media.R.id.media_scan_album)).perform(click());
        waitForSystemPicker();
        chooseLatestPhoto(name);
    }

    private void waitForSystemPicker() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            String name = root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (isSystemPicker(name)) return;
            SystemClock.sleep(100);
        }
        screenshot("album-picker-open-failed");
        logWindow(instrumentation.getUiAutomation().getRootInActiveWindow());
        fail("没有打开真实系统照片选择器");
    }

    private void chooseLatestPhoto(String displayName) throws Exception {
        boolean photoClicked = false;
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            String windowPackage = root == null ? "" : String.valueOf(root.getPackageName());
            // A selected thumbnail is not a delivered result. The Compose picker also requires Done.
            if (photoClicked && context.getPackageName().equals(windowPackage)) return;
            if (!isSystemPicker(windowPackage)) {
                SystemClock.sleep(100);
                continue;
            }
            if (photoClicked) {
                AccessibilityNodeInfo confirm = find(root, node -> {
                    String id = node.getViewIdResourceName();
                    String text = node.getText() == null ? "" : node.getText().toString().trim();
                    String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString().trim();
                    return node.isVisibleToUser() && node.isEnabled()
                            && ((id != null && (id.endsWith(":id/button_add") || id.endsWith(":id/button_done")))
                            || isPickerConfirmation(text) || isPickerConfirmation(description));
                });
                if (confirm != null) clickNode(confirm);
                SystemClock.sleep(100);
                continue;
            }
            AccessibilityNodeInfo item = find(root, node -> displayName.equals(String.valueOf(node.getText())));
            if (item == null) item = find(root, node -> {
                String id = node.getViewIdResourceName();
                String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                return node.isVisibleToUser() && bounds.width() >= 70 && bounds.height() >= 70
                        && ((id != null && id.contains("thumbnail"))
                        || description.startsWith("Photo taken") || description.startsWith("照片拍摄于"));
            });
            if (item != null && clickNode(item)) {
                photoClicked = true;
            }
            SystemClock.sleep(150);
        }
        screenshot("album-picker-selection-failed");
        logWindow(instrumentation.getUiAutomation().getRootInActiveWindow());
        fail(photoClicked ? "系统选图尚未确认并返回App：" + displayName : "系统选图界面未找到最新导入的照片：" + displayName);
    }

    private boolean isSystemPicker(String packageName) {
        return packageName.contains("providers.media") || packageName.contains("documentsui")
                || packageName.contains("photopicker");
    }

    private boolean isPickerConfirmation(String label) {
        return label.matches("^(Done|完成|Add|添加|Select|选择)(\\s*[(（]?1[)）]?)?$");
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, java.util.function.Predicate<AccessibilityNodeInfo> predicate) {
        if (node == null) return null;
        if (predicate.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), predicate);
            if (found != null) return found;
        }
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo current = node; current != null; current = current.getParent()) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    private void assertBatchArchive() throws Exception {
        Activity archive = waitFor(BatchArchiveActivity.class, 20000);
        if (archive == null) {
            screenshot("album-archive-failed");
            logWindow(instrumentation.getUiAutomation().getRootInActiveWindow());
            instrumentation.runOnMainSync(() -> {
                for (Stage stage : Stage.values()) {
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                        Log.e("AlbumQrRegression", stage + " " + activity.getClass().getSimpleName());
                    }
                }
            });
        }
        assertNotNull("相册图片必须经过识别及真实权限查询，打开批次档案", archive);
        assertEquals(batchId, archive.getIntent().getLongExtra(RouterPath.EXTRA_BATCH_ID, -1));
        assertEquals(targetFarm, core.farmContext().current().getFarmId());
        if (startFarm != targetFarm) assertEquals(startFarm, archive.getIntent().getLongExtra(RouterPath.EXTRA_FROM_FARM_ID, -1));
        // Destination extras alone are insufficient: wait for the actual archive response to render.
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicReference<String> title = new AtomicReference<>("");
        AtomicReference<Boolean> focused = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                androidx.appcompat.widget.Toolbar toolbar = archive.findViewById(com.zhimu.slmp.herd.R.id.herd_toolbar);
                title.set(toolbar == null ? "" : String.valueOf(toolbar.getTitle()));
                focused.set(archive.hasWindowFocus());
            });
            if (focused.get() && ("批次 " + expectedBatchNo).equals(title.get())) {
                onView(withText("批次 " + expectedBatchNo)).check(matches(isDisplayed()));
                return;
            }
            SystemClock.sleep(100);
        }
        assertEquals("批次档案必须加载真实批次详情", "批次 " + expectedBatchNo, title.get());
        assertTrue("批次档案必须成为前台窗口", focused.get());
    }

    private void logWindow(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null || node.getContentDescription() != null || node.isClickable()) {
            Log.e("AlbumQrRegression", node.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) logWindow(node.getChild(i));
    }

    private void waitForRenderedText(Activity activity, int viewId, String message) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                TextView view = activity.findViewById(viewId);
                found.set(activity.hasWindowFocus() && view != null && view.isShown()
                        && view.getGlobalVisibleRect(new android.graphics.Rect()) && message.contentEquals(view.getText()));
            });
            if (found.get()) return;
            SystemClock.sleep(50);
        }
        fail("页面必须真正显示：" + message);
    }

    private void waitForText(Activity activity, String message) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                TextView view = activity.findViewById(com.zhimu.slmp.media.R.id.media_scan_album_status);
                found.set(view != null && view.isShown() && message.contentEquals(view.getText())
                        && activity.findViewById(com.zhimu.slmp.media.R.id.media_scan_album).isEnabled());
            });
            if (found.get()) return;
            SystemClock.sleep(100);
        }
        fail("扫码页未恢复并显示提示：" + message);
    }

    private Activity waitFor(Class<? extends Activity> type, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        Activity found;
        while (SystemClock.uptimeMillis() < deadline) {
            if ((found = findActivity(type)) != null) return found;
            SystemClock.sleep(100);
        }
        return null;
    }

    private Activity findActivity(Class<? extends Activity> type) {
        AtomicReference<Activity> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (type.isInstance(activity)) result.set(activity);
            }
        });
        return result.get();
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().waitForIdle(500, 5000);
        File directory = new File(context.getExternalFilesDir(null), "album-qr-regression");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
