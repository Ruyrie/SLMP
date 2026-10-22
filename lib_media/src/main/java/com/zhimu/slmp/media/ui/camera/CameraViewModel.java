package com.zhimu.slmp.media.ui.camera;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.camera.CameraResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

/**
 * I08 相机页（模块07 文档 2.4.9，本期只有拍照与扫码，录像在模块07 加入）。
 * CameraX 对象由 CameraActivity 持有并随生命周期释放，ViewModel 只保存状态并发命令。
 * 已拍照片路径、镜头、闪光模式保存在 SavedStateHandle 中，页面重建后不丢失。
 */
public class CameraViewModel extends BaseViewModel {

    public static final String MSG_CAPTURE_FAILED = "拍照失败，请重试";
    public static final String MSG_OPEN_FAILED = "相机打开失败，请稍后重试";

    private static final String STATE_CAPTURED = "media_camera_captured";
    private static final String STATE_LENS = "media_camera_lens";
    private static final String STATE_FLASH = "media_camera_flash";

    @NonNull
    private CameraArgs args = CameraArgs.photo(null, 1);
    private final ArrayList<String> captured = new ArrayList<>();

    private final MutableLiveData<String> contextText = new MutableLiveData<>();
    private final MutableLiveData<String> remainingText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shutterEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> capturing = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> flashMode = new MutableLiveData<>(ImageCapture.FLASH_MODE_OFF);
    private final MutableLiveData<Integer> lensFacing = new MutableLiveData<>(CameraSelector.LENS_FACING_BACK);
    private final MutableLiveData<Boolean> hasFlash = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canSwitchLens = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> torchOn = new MutableLiveData<>(false);
    private final MutableLiveData<String> lastThumb = new MutableLiveData<>();
    /** null 未知、true 已授权、false 被拒绝 */
    private final MutableLiveData<Boolean> cameraGranted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> permissionDenied = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(true);

    private final SingleLiveEvent<Void> takePicture = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> vibrate = new SingleLiveEvent<>();
    private final SingleLiveEvent<CameraResult> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<String>> openViewer = new SingleLiveEvent<>();

    @Inject
    public CameraViewModel() {
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        args = CameraArgs.from(handle::get);
        ArrayList<String> saved = handle.get(STATE_CAPTURED);
        if (saved != null) {
            captured.addAll(saved);
            if (!captured.isEmpty()) {
                lastThumb.setValue(captured.get(captured.size() - 1));
            }
        }
        Integer lens = handle.get(STATE_LENS);
        if (lens != null) {
            lensFacing.setValue(lens);
        }
        Integer flash = handle.get(STATE_FLASH);
        if (flash != null) {
            flashMode.setValue(flash);
        }
        updateTexts();
    }

    // ===== 参数 =====

    public boolean isScanMode() {
        return args.isScan();
    }

    /** 扫码页顶部标题 */
    @NonNull
    public String getScanTitle() {
        return args.getTitle() != null ? args.getTitle() : "扫码";
    }

    @NonNull
    public String getScanHint() {
        return args.getScanHint() != null ? args.getScanHint() : "将二维码放入框内，即可自动识别";
    }

    @Nullable
    public String getScanSubHint() {
        return args.getScanSubHint();
    }

    /** 扫码页“手动输入”按钮文字；null 时不显示入口 */
    @Nullable
    public String getManualText() {
        return args.getManualText();
    }

    @Nullable
    public String getManualHint() {
        return args.getManualHint();
    }

    // ===== 状态 =====

    /** 说明胶囊：“死淘登记 · 已添加 2 / 6” */
    public LiveData<String> getContextText() {
        return contextText;
    }

    /** “还可以拍 4 张” */
    public LiveData<String> getRemainingText() {
        return remainingText;
    }

    public LiveData<Boolean> getShutterEnabled() {
        return shutterEnabled;
    }

    public LiveData<Integer> getFlashMode() {
        return flashMode;
    }

    public LiveData<Integer> getLensFacing() {
        return lensFacing;
    }

    public LiveData<Boolean> getHasFlash() {
        return hasFlash;
    }

    public LiveData<Boolean> getCanSwitchLens() {
        return canSwitchLens;
    }

    public LiveData<Boolean> getTorchOn() {
        return torchOn;
    }

    /** 最近一张照片的路径 */
    public LiveData<String> getLastThumb() {
        return lastThumb;
    }

    public LiveData<Boolean> getCameraGranted() {
        return cameraGranted;
    }

    /** 相机权限被拒绝：显示权限说明与“去设置开启” */
    public LiveData<Boolean> getPermissionDenied() {
        return permissionDenied;
    }

    /** 扫码中（扫描线动画与分析器开关） */
    public LiveData<Boolean> getScanning() {
        return scanning;
    }

    public SingleLiveEvent<Void> getTakePicture() {
        return takePicture;
    }

    public SingleLiveEvent<Void> getVibrate() {
        return vibrate;
    }

    public SingleLiveEvent<CameraResult> getFinishEvent() {
        return finishEvent;
    }

    public SingleLiveEvent<List<String>> getOpenViewer() {
        return openViewer;
    }

    public int getRemaining() {
        return Math.max(0, args.getMaxCount() - captured.size());
    }

    // ===== 方法 =====

    public void onPermission(boolean granted) {
        cameraGranted.setValue(granted);
        permissionDenied.setValue(!granted);
        updateTexts();
    }

    /** 相机已绑定：按能力显示闪光灯（手电筒）与切换按钮 */
    public void onCameraBound(boolean flashAvailable, boolean switchable) {
        hasFlash.setValue(flashAvailable);
        canSwitchLens.setValue(switchable);
        if (!flashAvailable) {
            torchOn.setValue(false);
        }
    }

    public void onCameraError() {
        showMessage(MSG_OPEN_FAILED);
    }

    /** 快门：回调返回前不能再次拍照 */
    public void onShutter() {
        if (!Boolean.TRUE.equals(shutterEnabled.getValue())) {
            return;
        }
        capturing.setValue(true);
        updateTexts();
        takePicture.call();
    }

    public void onPhotoSaved(@NonNull String path) {
        capturing.setValue(false);
        captured.add(path);
        getSavedState().set(STATE_CAPTURED, new ArrayList<>(captured));
        lastThumb.setValue(path);
        updateTexts();
        if (getRemaining() <= 0) {
            finishEvent.setValue(CameraResult.photos(captured));
        }
    }

    public void onPhotoFailed() {
        capturing.setValue(false);
        updateTexts();
        showMessage(MSG_CAPTURE_FAILED);
    }

    /** 闪光灯：关闭 → 自动 → 开启 */
    public void cycleFlash() {
        Integer cur = flashMode.getValue();
        int next;
        if (cur == null || cur == ImageCapture.FLASH_MODE_OFF) {
            next = ImageCapture.FLASH_MODE_AUTO;
        } else if (cur == ImageCapture.FLASH_MODE_AUTO) {
            next = ImageCapture.FLASH_MODE_ON;
        } else {
            next = ImageCapture.FLASH_MODE_OFF;
        }
        flashMode.setValue(next);
        getSavedState().set(STATE_FLASH, next);
    }

    public void switchLens() {
        if (!Boolean.TRUE.equals(canSwitchLens.getValue()) || Boolean.TRUE.equals(capturing.getValue())) {
            return;
        }
        Integer cur = lensFacing.getValue();
        int next = cur != null && cur == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        lensFacing.setValue(next);
        getSavedState().set(STATE_LENS, next);
    }

    public void toggleTorch() {
        if (Boolean.TRUE.equals(hasFlash.getValue())) {
            torchOn.setValue(!Boolean.TRUE.equals(torchOn.getValue()));
        }
    }

    /** 识别到二维码：停止分析、震动并返回原文 */
    public void onQrDecoded(@NonNull String text) {
        if (!Boolean.TRUE.equals(scanning.getValue())) {
            return;
        }
        scanning.setValue(false);
        vibrate.call();
        finishEvent.setValue(CameraResult.scan(text));
    }

    public void onManualInput() {
        finishEvent.setValue(CameraResult.manualInput());
    }

    /** 关闭或返回：已拍的照片照样带回（拍下即已加入网格） */
    public void onClose() {
        finishEvent.setValue(CameraResult.photos(captured));
    }

    public void onThumbClick() {
        if (!captured.isEmpty()) {
            openViewer.setValue(new ArrayList<>(captured));
        }
    }

    private void updateTexts() {
        if (args.isScan()) {
            return;
        }
        int added = args.getAddedCount();
        int total = args.getTotalCount();
        String count;
        if (total > 0) {
            count = String.format(Locale.ROOT, "已添加 %d / %d", Math.max(0, added) + captured.size(), total);
        } else if (!captured.isEmpty()) {
            count = String.format(Locale.ROOT, "已拍 %d 张", captured.size());
        } else {
            count = null;
        }
        String title = args.getTitle();
        if (!TextUtils.isEmpty(title) && count != null) {
            contextText.setValue(title + " · " + count);
        } else {
            contextText.setValue(!TextUtils.isEmpty(title) ? title : count);
        }
        int remaining = getRemaining();
        remainingText.setValue(String.format(Locale.ROOT, "还可以拍 %d 张", remaining));
        shutterEnabled.setValue(Boolean.TRUE.equals(cameraGranted.getValue())
                && !Boolean.TRUE.equals(capturing.getValue()) && remaining > 0);
    }
}
