package com.zhimu.slmp.media.ui.camera;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
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
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
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
 * I08 相机页（03 号规范 8.2、8.7、8.8 节，模块07 文档 2.7.1）：拍照、录像、扫码三种模式，由调用方经
 * {@link com.zhimu.slmp.media.camera.CameraContract} 传入。
 * <ul>
 *     <li>CameraX 用例 bindToLifecycle 绑定本页面，进入后台自动释放相机，手电筒随之熄灭；拍照与录像切换时重新绑定</li>
 *     <li>拍照：Preview + ImageCapture（低延迟），targetRotation 随手机方向更新，照片存 cacheDir/capture</li>
 *     <li>录像：Preview + VideoCapture（Recorder 选 HD 720p，不支持时向下回退，约 4Mbps），FileOutputOptions 时长上限
 *     30 秒；VideoRecordEvent.Status 驱动“0:12 / 0:30”，Finalize 报“达到时长上限”视为正常；必须有麦克风权限</li>
 *     <li>扫码：Preview + ImageAnalysis（只保留最新一帧，1280×720）+ {@link QrCodeAnalyzer}；识别成功后停止分析、
 *     震动 50 毫秒并返回原文；可打开手电筒；“手动输入”入口返回 TYPE_MANUAL_INPUT</li>
 *     <li>CAMERA 权限进入时申请；RECORD_AUDIO 切到录像时申请，被拒绝时显示说明与“去设置开启”，从设置返回后重新检查</li>
 * </ul>
 */
public class CameraActivity extends BaseActivity {

    private static final String REQ_DISCARD = "media_camera_discard_recording";
    /** 录像目标码率 */
    private static final int VIDEO_BITRATE = 4_000_000;

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
    private VideoCapture<Recorder> videoCapture;
    @Nullable
    private Recording activeRecording;
    @Nullable
    private ExecutorService analysisExecutor;
    @Nullable
    private QrCodeAnalyzer analyzer;
    @Nullable
    private OrientationEventListener orientationListener;
    @Nullable
    private ObjectAnimator dotBlink;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> vm.onPermission(granted));
    private final ActivityResultLauncher<String> micLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> vm.onMicPermission(granted));

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
                if (vm.isAllowVideo() && vm.isVideoMode() && !hasMicPermission()) {
                    requestMic();
                }
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

        if (vm.isAllowVideo()) {
            vm.onMicPermission(hasMicPermission());
        }
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
        b.mediaMicSettings.setOnClickListener(v -> openAppSettings());

        vm.getFlashMode().observe(this, mode -> {
            int flash = mode == null ? ImageCapture.FLASH_MODE_OFF : mode;
            updateFlashIcon(b.mediaFlash, flash);
            if (imageCapture != null) {
                imageCapture.setFlashMode(flash);
            }
            if (activeRecording != null) {
                applyTorch(flash == ImageCapture.FLASH_MODE_ON);
            }
        });
        vm.getTakePicture().observe(this, v -> capture());
        vm.getCameraCommand().observe(this, this::onCameraCommand);
        vm.getOpenViewer().observe(this, this::openCaptured);
        vm.getLastThumb().observe(this, path -> {
            if (path == null) {
                Glide.with(b.mediaLastThumb).clear(b.mediaLastThumb);
            } else {
                Glide.with(b.mediaLastThumb).load(path.startsWith("/") ? new File(path) : path)
                        .centerCrop().into(b.mediaLastThumb);
            }
        });

        if (vm.isAllowVideo()) {
            vm.getRequestMic().observe(this, v -> requestMic());
            vm.getShowDiscardRecording().observe(this, v -> ConfirmDialogFragment.builder(REQ_DISCARD)
                    .title(getString(R.string.media_camera_discard_title))
                    .message(getString(R.string.media_camera_discard_message))
                    .negative(getString(R.string.media_camera_keep_recording))
                    .positive(getString(R.string.media_camera_discard))
                    .danger(true)
                    .build()
                    .showSafely(getSupportFragmentManager()));
            getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
                if (ConfirmDialogFragment.isConfirmed(result)) {
                    vm.onDiscardRecordingConfirmed();
                }
            });
            vm.getMode().observe(this, mode -> renderMode(b));
            vm.getRecording().observe(this, rec -> {
                boolean on = Boolean.TRUE.equals(rec);
                b.mediaModeSwitch.setAlpha(on ? 0.5f : 1f);
                b.mediaModePhoto.setClickable(!on);
                b.mediaModeVideo.setClickable(!on);
                blinkDot(b.mediaTimerDot, on);
                renderMode(b);
            });
            vm.getShutterStyle().observe(this, style -> {
                int s = style == null ? CameraViewModel.SHUTTER_PHOTO : style;
                b.mediaShutter.setBackgroundResource(s == CameraViewModel.SHUTTER_RECORDING
                        ? R.drawable.media_bg_shutter_stop
                        : s == CameraViewModel.SHUTTER_VIDEO ? R.drawable.media_bg_shutter_video
                        : R.drawable.media_bg_shutter);
                b.mediaShutter.setContentDescription(getString(s == CameraViewModel.SHUTTER_RECORDING
                        ? R.string.media_camera_stop
                        : s == CameraViewModel.SHUTTER_VIDEO ? R.string.media_camera_record
                        : R.string.media_camera_shutter));
            });
            GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                    if (Math.abs(vx) > Math.abs(vy) * 1.5f && Math.abs(vx) > 600) {
                        vm.onSwipe(vx < 0);
                        return true;
                    }
                    return false;
                }
            });
            previewView.setOnTouchListener((v, e) -> swipe.onTouchEvent(e));
        }

        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return;
                }
                if (imageCapture != null) {
                    imageCapture.setTargetRotation(rotationOf(orientation));
                }
                if (videoCapture != null && activeRecording == null) {
                    videoCapture.setTargetRotation(rotationOf(orientation));
                }
            }
        };
    }

    private void renderMode(@NonNull MediaActivityCameraPhotoBinding b) {
        boolean video = vm.isVideoMode();
        b.mediaModePhoto.setSelected(!video);
        b.mediaModeVideo.setSelected(video);
        b.mediaModePhoto.setBackgroundResource(video ? 0 : R.drawable.media_bg_mode_selected);
        b.mediaModeVideo.setBackgroundResource(video ? R.drawable.media_bg_mode_selected : 0);
        // 已有 1 段视频时“录像”段文字为白色 50%（仍可点，点击后提示）
        b.mediaModeVideo.setAlpha(!video && vm.isVideoTaken() ? 0.5f : 1f);
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
        if (vm.isAllowVideo() && !Boolean.TRUE.equals(vm.getMicGranted().getValue()) && hasMicPermission()) {
            vm.onMicPermission(true);
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
    protected void onStop() {
        // 进入后台时停止录像并保留已录内容（CameraX 解绑前先正常结束，文件可用）
        if (activeRecording != null) {
            vm.onStopWhileRecording();
            stopRecording();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
        if (dotBlink != null) {
            dotBlink.cancel();
        }
        super.onDestroy();
    }

    // ===== 权限 =====

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMic() {
        if (hasMicPermission()) {
            vm.onMicPermission(true);
            return;
        }
        vm.onMicRequested();
        micLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    // ===== CameraX =====

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

    /** 绑定（或重绑）用例；切换镜头、切换拍照 / 录像时调用 */
    private void bindUseCases() {
        ProcessCameraProvider p = provider;
        if (p == null || !Boolean.TRUE.equals(vm.getCameraGranted().getValue()) || activeRecording != null) {
            return;
        }
        boolean hasBack = hasCamera(p, CameraSelector.DEFAULT_BACK_CAMERA);
        boolean hasFront = hasCamera(p, CameraSelector.DEFAULT_FRONT_CAMERA);
        Integer lens = vm.getLensFacing().getValue();
        boolean front = vm.isScanMode() ? !hasBack
                : (lens != null && lens == CameraSelector.LENS_FACING_FRONT && hasFront) || !hasBack;
        CameraSelector selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

        p.unbindAll();
        imageCapture = null;
        videoCapture = null;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        int rotation = previewView.getDisplay() != null ? previewView.getDisplay().getRotation() : Surface.ROTATION_0;
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
            } else if (vm.isAllowVideo() && vm.isVideoMode()) {
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                        .setTargetVideoEncodingBitRate(VIDEO_BITRATE)
                        .build();
                VideoCapture<Recorder> vc = new VideoCapture.Builder<>(recorder)
                        .setTargetRotation(rotation)
                        .build();
                videoCapture = vc;
                camera = p.bindToLifecycle(this, selector, preview, vc);
            } else {
                Integer flash = vm.getFlashMode().getValue();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flash == null ? ImageCapture.FLASH_MODE_OFF : flash)
                        .setTargetRotation(rotation)
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

    private void onCameraCommand(@Nullable Integer cmd) {
        if (cmd == null) {
            return;
        }
        switch (cmd) {
            case CameraViewModel.CMD_TAKE_PHOTO:
                capture();
                break;
            case CameraViewModel.CMD_START_RECORD:
                startRecording();
                break;
            case CameraViewModel.CMD_STOP_RECORD:
                stopRecording();
                break;
            case CameraViewModel.CMD_REBIND:
                bindUseCases();
                break;
            default:
                break;
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

    /** 开始录像（已检查麦克风权限；没有权限不录像） */
    @SuppressLint("MissingPermission")
    private void startRecording() {
        VideoCapture<Recorder> vc = videoCapture;
        if (vc == null || activeRecording != null) {
            vm.onRecordFinalized(null, 0, false, vc == null ? CameraViewModel.MSG_RECORD_FAILED : null);
            return;
        }
        if (!hasMicPermission()) {
            vm.onMicPermission(false);
            vm.onRecordFinalized(null, 0, false, getString(R.string.media_camera_mic_title));
            return;
        }
        File file = MediaFiles.newVideoCaptureFile(this);
        FileOutputOptions options = new FileOutputOptions.Builder(file)
                .setDurationLimitMillis(CameraViewModel.MAX_RECORD_MS)
                .build();
        try {
            activeRecording = vc.getOutput()
                    .prepareRecording(this, options)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this), event -> onRecordEvent(event, file));
        } catch (IllegalStateException | SecurityException e) {
            activeRecording = null;
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            vm.onRecordFinalized(null, 0, false, CameraViewModel.MSG_RECORD_FAILED);
            return;
        }
        Integer flash = vm.getFlashMode().getValue();
        applyTorch(flash != null && flash == ImageCapture.FLASH_MODE_ON);
    }

    private void onRecordEvent(@NonNull VideoRecordEvent event, @NonNull File file) {
        if (event instanceof VideoRecordEvent.Start) {
            vm.onRecordStarted();
        } else if (event instanceof VideoRecordEvent.Status) {
            vm.onRecordStatus(event.getRecordingStats().getRecordedDurationNanos());
        } else if (event instanceof VideoRecordEvent.Finalize) {
            VideoRecordEvent.Finalize f = (VideoRecordEvent.Finalize) event;
            activeRecording = null;
            applyTorch(false);
            long durationMs = f.getRecordingStats().getRecordedDurationNanos() / 1_000_000L;
            int error = f.getError();
            boolean limit = error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED;
            boolean usable = file.isFile() && file.length() > 0 && durationMs >= 1000;
            if (vm.shouldDeleteFinalizedFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                vm.onRecordFinalized(null, 0, false, null);
            } else if ((error == VideoRecordEvent.Finalize.ERROR_NONE || limit
                    || error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) && usable) {
                vm.onRecordFinalized(file.getAbsolutePath(), durationMs, limit, null);
            } else {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                vm.onRecordFinalized(null, 0, false, error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA
                        || (!usable && error == VideoRecordEvent.Finalize.ERROR_NONE)
                        ? CameraViewModel.MSG_RECORD_TOO_SHORT : CameraViewModel.MSG_RECORD_FAILED);
            }
            // 录像结束后恢复切换镜头、模式等（重绑会在下一次模式切换时进行）
        }
    }

    private void stopRecording() {
        Recording r = activeRecording;
        if (r != null) {
            r.stop();
        }
    }

    private void applyTorch(boolean on) {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(on);
        }
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

    private void blinkDot(@NonNull View dot, boolean on) {
        if (on) {
            if (dotBlink == null) {
                dotBlink = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.2f);
                dotBlink.setDuration(500);
                dotBlink.setRepeatMode(ValueAnimator.REVERSE);
                dotBlink.setRepeatCount(ValueAnimator.INFINITE);
            }
            dotBlink.start();
        } else if (dotBlink != null) {
            dotBlink.cancel();
            dot.setAlpha(1f);
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
            items.add(p.endsWith(".mp4") ? ViewerItem.localVideo(p, null) : ViewerItem.local(p, null));
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
