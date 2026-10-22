package com.zhimu.slmp.media.ui.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.media.R;
import com.zhimu.slmp.media.camera.CameraResult;
import com.zhimu.slmp.media.databinding.MediaActivityCameraPhotoBinding;
import com.zhimu.slmp.media.databinding.MediaActivityCameraScanBinding;
import com.zhimu.slmp.media.di.MediaInjector;
import com.zhimu.slmp.media.scan.QrCodeAnalyzer;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.ui.viewer.ViewerItem;
import com.zhimu.slmp.media.util.MediaFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * I08 相机页（03 号规范 8.2、8.7、8.8 节）：拍照与扫码两种模式，由调用方经
 * {@link com.zhimu.slmp.media.camera.CameraContract} 传入；录像模式预留（模块07）。
 * <ul>
 *     <li>CameraX 用例 bindToLifecycle 绑定本页面，进入后台自动释放相机，手电筒随之熄灭</li>
 *     <li>拍照：Preview + ImageCapture（低延迟），targetRotation 随手机方向更新，照片存 cacheDir/capture；
 *     快门回调返回前不能再次拍照；闪光灯、前后摄像头切换按相机能力显示；拍满本次张数或点关闭时返回照片路径</li>
 *     <li>扫码：Preview + ImageAnalysis（只保留最新一帧，1280×720）+ {@link QrCodeAnalyzer}；识别成功后停止分析、
 *     震动 50 毫秒并返回原文；可打开手电筒；“手动输入”入口返回 TYPE_MANUAL_INPUT</li>
 *     <li>CAMERA 权限进入时申请；被拒绝时显示说明与“去设置开启”，从设置返回后重新检查</li>
 * </ul>
 */
public class CameraActivity extends BaseActivity {

    private CameraViewModel vm;
    @Nullable
    private MediaActivityCameraPhotoBinding photoBinding;
    @Nullable
    private MediaActivityCameraScanBinding scanBinding;
    private PreviewView previewView;

    @Nullable
    private ProcessCameraProvider provider;
    @Nullable
    private Camera camera;
    @Nullable
    private ImageCapture imageCapture;
    @Nullable
    private ExecutorService analysisExecutor;
    @Nullable
    private QrCodeAnalyzer analyzer;
    @Nullable
    private OrientationEventListener orientationListener;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> vm.onPermission(granted));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(MediaInjector.factory(), CameraViewModel.class);
        observeBase(vm);
        if (vm.isScanMode()) {
            setupScan();
        } else {
            setupPhoto();
        }

        vm.getCameraGranted().observe(this, granted -> {
            if (Boolean.TRUE.equals(granted)) {
                startCamera();
            }
        });
        vm.getLensFacing().observe(this, lens -> {
            if (provider != null) {
                bindUseCases();
            }
        });
        vm.getFinishEvent().observe(this, this::finishWith);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                vm.onClose();
            }
        });

        if (hasCameraPermission()) {
            vm.onPermission(true);
        } else if (savedInstanceState == null) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            vm.onPermission(false);
        }
    }

    private void setupPhoto() {
        MediaActivityCameraPhotoBinding b = DataBindingUtil.setContentView(this, R.layout.media_activity_camera_photo);
        photoBinding = b;
        b.setVm(vm);
        b.setLifecycleOwner(this);
        previewView = b.mediaPreview;
        b.mediaOpenSettings.setOnClickListener(v -> openAppSettings());

        vm.getFlashMode().observe(this, mode -> {
            int flash = mode == null ? ImageCapture.FLASH_MODE_OFF : mode;
            updateFlashIcon(b.mediaFlash, flash);
            if (imageCapture != null) {
                imageCapture.setFlashMode(flash);
            }
        });
        vm.getTakePicture().observe(this, v -> capture());
        vm.getOpenViewer().observe(this, this::openCaptured);

        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN || imageCapture == null) {
                    return;
                }
                imageCapture.setTargetRotation(rotationOf(orientation));
            }
        };
    }

    private void setupScan() {
        MediaActivityCameraScanBinding b = DataBindingUtil.setContentView(this, R.layout.media_activity_camera_scan);
        scanBinding = b;
        b.setVm(vm);
        b.setLifecycleOwner(this);
        previewView = b.mediaPreview;
        b.mediaOpenSettings.setOnClickListener(v -> openAppSettings());

        analysisExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "zmt-qr-analyzer"));
        analyzer = new QrCodeAnalyzer(text -> vm.onQrDecoded(text));
        vm.getScanning().observe(this, on -> {
            boolean scanning = Boolean.TRUE.equals(on);
            b.mediaScanFrame.setScanning(scanning);
            if (analyzer != null) {
                analyzer.setEnabled(scanning);
            }
        });
        vm.getTorchOn().observe(this, on -> {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                camera.getCameraControl().enableTorch(Boolean.TRUE.equals(on));
            }
        });
        vm.getVibrate().observe(this, v -> vibrate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统设置返回后重新检查权限
        if (!Boolean.TRUE.equals(vm.getCameraGranted().getValue()) && hasCameraPermission()) {
            vm.onPermission(true);
        }
        if (orientationListener != null && orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    @Override
    protected void onPause() {
        if (orientationListener != null) {
            orientationListener.disable();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
        super.onDestroy();
    }

    // ===== CameraX =====

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        if (provider != null) {
            bindUseCases();
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            try {
                provider = future.get();
                bindUseCases();
            } catch (Exception e) {
                vm.onCameraError();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** 绑定（或重绑）用例；切换镜头时调用 */
    private void bindUseCases() {
        ProcessCameraProvider p = provider;
        if (p == null || !Boolean.TRUE.equals(vm.getCameraGranted().getValue())) {
            return;
        }
        boolean hasBack = hasCamera(p, CameraSelector.DEFAULT_BACK_CAMERA);
        boolean hasFront = hasCamera(p, CameraSelector.DEFAULT_FRONT_CAMERA);
        Integer lens = vm.getLensFacing().getValue();
        boolean front = vm.isScanMode() ? !hasBack
                : (lens != null && lens == CameraSelector.LENS_FACING_FRONT && hasFront) || !hasBack;
        CameraSelector selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

        p.unbindAll();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        try {
            if (vm.isScanMode()) {
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                .build())
                        .build();
                if (analysisExecutor != null && analyzer != null) {
                    analysis.setAnalyzer(analysisExecutor, analyzer);
                }
                camera = p.bindToLifecycle(this, selector, preview, analysis);
            } else {
                Integer flash = vm.getFlashMode().getValue();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flash == null ? ImageCapture.FLASH_MODE_OFF : flash)
                        .setTargetRotation(previewView.getDisplay() != null
                                ? previewView.getDisplay().getRotation() : Surface.ROTATION_0)
                        .build();
                camera = p.bindToLifecycle(this, selector, preview, imageCapture);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            camera = null;
            vm.onCameraError();
            return;
        }
        boolean flashUnit = camera.getCameraInfo().hasFlashUnit();
        vm.onCameraBound(flashUnit, !vm.isScanMode() && hasBack && hasFront);
        if (vm.isScanMode() && flashUnit && Boolean.TRUE.equals(vm.getTorchOn().getValue())) {
            camera.getCameraControl().enableTorch(true);
        }
    }

    private static boolean hasCamera(@NonNull ProcessCameraProvider p, @NonNull CameraSelector selector) {
        try {
            return p.hasCamera(selector);
        } catch (CameraInfoUnavailableException e) {
            return false;
        }
    }

    private void capture() {
        ImageCapture ic = imageCapture;
        if (ic == null) {
            vm.onPhotoFailed();
            return;
        }
        File file = MediaFiles.newCaptureFile(this);
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(file).build();
        flashWhite();
        ic.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                vm.onPhotoSaved(file.getAbsolutePath());
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                vm.onPhotoFailed();
            }
        });
    }

    /** 手机方向（度）→ Surface 旋转常量 */
    private static int rotationOf(int orientation) {
        if (orientation >= 45 && orientation < 135) {
            return Surface.ROTATION_270;
        } else if (orientation >= 135 && orientation < 225) {
            return Surface.ROTATION_180;
        } else if (orientation >= 225 && orientation < 315) {
            return Surface.ROTATION_90;
        }
        return Surface.ROTATION_0;
    }

    // ===== 界面 =====

    private void updateFlashIcon(@NonNull ImageButton button, int mode) {
        if (mode == ImageCapture.FLASH_MODE_AUTO) {
            button.setImageResource(R.drawable.media_ic_flash_auto);
            button.setContentDescription(getString(R.string.media_camera_flash_auto));
        } else if (mode == ImageCapture.FLASH_MODE_ON) {
            button.setImageResource(R.drawable.media_ic_flash_on);
            button.setContentDescription(getString(R.string.media_camera_flash_on));
        } else {
            button.setImageResource(com.zhimu.slmp.base.R.drawable.ic_flash_off);
            button.setContentDescription(getString(R.string.media_camera_flash_off));
        }
    }

    private void flashWhite() {
        if (photoBinding == null) {
            return;
        }
        View white = photoBinding.mediaFlashWhite;
        white.animate().cancel();
        white.setAlpha(0.8f);
        white.animate().alpha(0f).setDuration(220).start();
    }

    private void vibrate() {
        VibratorManager manager = getSystemService(VibratorManager.class);
        Vibrator vibrator = manager == null ? null : manager.getDefaultVibrator();
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void openCaptured(@Nullable List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        List<ViewerItem> items = new ArrayList<>(paths.size());
        for (String p : paths) {
            items.add(ViewerItem.local(p, null));
        }
        MediaViewer.open(this, items, items.size() - 1);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void finishWith(@Nullable CameraResult result) {
        if (result == null || result.isCanceled()) {
            setResult(Activity.RESULT_CANCELED);
        } else {
            setResult(Activity.RESULT_OK, result.toIntent());
        }
        finish();
    }
}
