package com.zhimu.slmp.media.ui.camera;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.camera.CameraResult;
import com.zhimu.slmp.media.process.MediaValidator;
import com.zhimu.slmp.media.queue.UploadQueue;
import com.zhimu.slmp.media.queue.UploadTaskStates;
import com.zhimu.slmp.media.upload.UploadItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * I08 相机页（模块07 文档 2.4.9、2.7.1）：拍照、录像、扫码。CameraX 对象由 CameraActivity 持有并随生命周期释放，
 * ViewModel 只保存状态并发命令（{@link #getCameraCommand()}）。
 * <ul>
 *     <li>拍照（原有用法）：拍下的照片路径在关闭时带回调用方</li>
 *     <li>拍照与录像（{@link CameraArgs#media}，上传队列模式）：拍下即写入 upload_task，录像 30 秒自动停止并显示
 *     “0:12 / 0:30”；录完后上下文胶囊显示“正在压缩视频 45%”，完成后计数加 1；照片和视频合计不超过上限、视频最多 1 段</li>
 *     <li>录像需要麦克风（2026-10-22 确认）：被拒时录像模式显示“录像需要麦克风权限”与“去设置开启”，不开启就不录像，仍可拍照</li>
 * </ul>
 * 已拍内容、镜头、闪光、模式保存在 SavedStateHandle 中，页面重建后不丢失。
 */
public class CameraViewModel extends BaseViewModel {

    public static final String MSG_CAPTURE_FAILED = "拍照失败，请重试";
    public static final String MSG_OPEN_FAILED = "相机打开失败，请稍后重试";
    public static final String MSG_RECORD_FAILED = "录像失败，请重试";
    public static final String MSG_RECORD_TOO_SHORT = "录像时间太短，请重新录制";

    /** 相机命令 */
    public static final int CMD_TAKE_PHOTO = 1;
    public static final int CMD_START_RECORD = 2;
    public static final int CMD_STOP_RECORD = 3;
    /** 模式切换后重新绑定用例 */
    public static final int CMD_REBIND = 4;

    /** 快门形态 */
    public static final int SHUTTER_PHOTO = 0;
    public static final int SHUTTER_VIDEO = 1;
    public static final int SHUTTER_RECORDING = 2;

    /** 录像时长上限 */
    public static final long MAX_RECORD_MS = MediaValidator.MAX_VIDEO_DURATION_MS;
    public static final int PROGRESS_MAX = 1000;

    private static final String STATE_CAPTURED = "media_camera_captured";
    private static final String STATE_LENS = "media_camera_lens";
    private static final String STATE_FLASH = "media_camera_flash";
    private static final String STATE_MODE = "media_camera_mode";
    private static final String STATE_QUEUED = "media_camera_queued";
    private static final String STATE_VIDEO_TASK = "media_camera_video_task";
    private static final String STATE_VIDEO_PATH = "media_camera_video_path";

    @Nullable
    private final UploadQueue queue;
    @NonNull
    private CameraArgs args = CameraArgs.photo(null, 1);
    private final ArrayList<String> captured = new ArrayList<>();
    /** 上传队列模式写入的个数（照片 + 视频） */
    private int queuedCount;
    private long videoTaskId = -1;
    @Nullable
    private String videoPath;
    private boolean discardRecording;
    private boolean recordLimitReached;
    @Nullable
    private String videoTaskStatus;
    private int videoTaskProgress;
    @Nullable
    private String videoTaskError;
    @Nullable
    private Disposable videoTaskSub;

    private final MutableLiveData<String> mode = new MutableLiveData<>(CameraArgs.MODE_PHOTO);
    private final MutableLiveData<String> contextText = new MutableLiveData<>();
    private final MutableLiveData<String> remainingText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shutterEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> shutterStyle = new MutableLiveData<>(SHUTTER_PHOTO);
    private final MutableLiveData<Boolean> capturing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> recording = new MutableLiveData<>(false);
    private final MutableLiveData<String> elapsedText = new MutableLiveData<>(formatElapsed(0));
    private final MutableLiveData<Integer> recordProgress = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> flashMode = new MutableLiveData<>(ImageCapture.FLASH_MODE_OFF);
    private final MutableLiveData<Integer> lensFacing = new MutableLiveData<>(CameraSelector.LENS_FACING_BACK);
    private final MutableLiveData<Boolean> hasFlash = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canSwitchLens = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> torchOn = new MutableLiveData<>(false);
    private final MutableLiveData<String> lastThumb = new MutableLiveData<>();
    /** null 未知、true 已授权、false 被拒绝 */
    private final MutableLiveData<Boolean> cameraGranted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> permissionDenied = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> micGranted = new MutableLiveData<>(false);
    /** 录像模式且麦克风被拒：显示“录像需要麦克风权限”与“去设置开启” */
    private final MutableLiveData<Boolean> micDenied = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(true);

    private final SingleLiveEvent<Void> takePicture = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> cameraCommand = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> requestMic = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscardRecording = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> vibrate = new SingleLiveEvent<>();
    private final SingleLiveEvent<CameraResult> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<String>> openViewer = new SingleLiveEvent<>();

    /** 只拍照、扫码时没有 Dagger（单元测试） */
    @VisibleForTesting
    public CameraViewModel() {
        this.queue = null;
    }

    @Inject
    public CameraViewModel(@NonNull UploadQueue queue) {
        this.queue = queue;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        args = CameraArgs.from(handle::get);
        ArrayList<String> saved = handle.get(STATE_CAPTURED);
        if (saved != null) {
            captured.addAll(saved);
        }
        Integer lens = handle.get(STATE_LENS);
        if (lens != null) {
            lensFacing.setValue(lens);
        }
        Integer flash = handle.get(STATE_FLASH);
        if (flash != null) {
            flashMode.setValue(flash);
        }
        String savedMode = handle.get(STATE_MODE);
        mode.setValue(savedMode != null ? savedMode : args.isVideo() ? CameraArgs.MODE_VIDEO : CameraArgs.MODE_PHOTO);
        Integer q = handle.get(STATE_QUEUED);
        queuedCount = q == null ? 0 : q;
        Long vt = handle.get(STATE_VIDEO_TASK);
        videoTaskId = vt == null ? -1 : vt;
        videoPath = handle.get(STATE_VIDEO_PATH);
        if (videoPath != null) {
            lastThumb.setValue(videoPath);
        } else if (!captured.isEmpty()) {
            lastThumb.setValue(captured.get(captured.size() - 1));
        }
        if (videoTaskId > 0) {
            observeVideoTask(videoTaskId);
        }
        updateTexts();
    }

    // ===== 参数 =====

    public boolean isScanMode() {
        return args.isScan();
    }

    /** 显示拍照 / 录像切换 */
    public boolean isAllowVideo() {
        return args.isAllowVideo();
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

    /** PHOTO / VIDEO */
    public LiveData<String> getMode() {
        return mode;
    }

    public boolean isVideoMode() {
        return CameraArgs.MODE_VIDEO.equals(mode.getValue());
    }

    /** 说明胶囊：“登记巡检 · 3 号育肥舍 · 已添加 2 / 6” */
    public LiveData<String> getContextText() {
        return contextText;
    }

    /** 底部提示：“还可以拍 4 张”“照片和视频合计最多 6 个”“录像不超过 30 秒，自动压缩为 720p” */
    public LiveData<String> getRemainingText() {
        return remainingText;
    }

    public LiveData<Boolean> getShutterEnabled() {
        return shutterEnabled;
    }

    /** {@link #SHUTTER_PHOTO} / {@link #SHUTTER_VIDEO} / {@link #SHUTTER_RECORDING} */
    public LiveData<Integer> getShutterStyle() {
        return shutterStyle;
    }

    public LiveData<Boolean> getRecording() {
        return recording;
    }

    /** “0:12 / 0:30” */
    public LiveData<String> getElapsedText() {
        return elapsedText;
    }

    /** 录像进度 0–1000 */
    public LiveData<Integer> getRecordProgress() {
        return recordProgress;
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

    /** 最近一张照片或视频的路径 */
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

    public LiveData<Boolean> getMicGranted() {
        return micGranted;
    }

    public LiveData<Boolean> getMicDenied() {
        return micDenied;
    }

    /** 扫码中（扫描线动画与分析器开关） */
    public LiveData<Boolean> getScanning() {
        return scanning;
    }

    /** 原有拍照用法的拍照命令 */
    public SingleLiveEvent<Void> getTakePicture() {
        return takePicture;
    }

    public SingleLiveEvent<Integer> getCameraCommand() {
        return cameraCommand;
    }

    /** 切到录像时申请 RECORD_AUDIO */
    public SingleLiveEvent<Void> getRequestMic() {
        return requestMic;
    }

    /** 录像中关闭：“放弃这段录像？” */
    public SingleLiveEvent<Void> getShowDiscardRecording() {
        return showDiscardRecording;
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

    /** 本次还能拍几个 */
    public int getRemaining() {
        return Math.max(0, args.getMaxCount() - captured.size() - (args.isQueueMode() ? videoCount() : 0));
    }

    private int videoCount() {
        return videoPath != null ? 1 : 0;
    }

    /** 已有视频（进入前网格中已有或本次已录） */
    public boolean isVideoTaken() {
        return args.isHasVideo() || videoPath != null;
    }

    // ===== 权限与相机 =====

    public void onPermission(boolean granted) {
        cameraGranted.setValue(granted);
        permissionDenied.setValue(!granted);
        updateTexts();
    }

    /** 麦克风权限结果（申请回调或从设置返回后检查） */
    public void onMicPermission(boolean granted) {
        micGranted.setValue(granted);
        micDenied.setValue(!granted && isVideoMode() && micAsked);
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

    // ===== 模式 =====

    /** 点“拍照”“录像”或在取景区左右滑动；录像中不可切换 */
    public void switchMode(@NonNull String target) {
        if (!args.isAllowVideo() || Boolean.TRUE.equals(recording.getValue())
                || Boolean.TRUE.equals(capturing.getValue()) || target.equals(mode.getValue())) {
            return;
        }
        if (CameraArgs.MODE_VIDEO.equals(target) && isVideoTaken()) {
            contextText.setValue("已有 1 段视频，删除后才能再录");
            return;
        }
        mode.setValue(target);
        getSavedState().set(STATE_MODE, target);
        if (CameraArgs.MODE_VIDEO.equals(target) && !Boolean.TRUE.equals(micGranted.getValue())) {
            requestMic.call();
        }
        micDenied.setValue(CameraArgs.MODE_VIDEO.equals(target) && Boolean.FALSE.equals(micGranted.getValue())
                && micAsked);
        cameraCommand.setValue(CMD_REBIND);
        updateTexts();
    }

    /** 已经申请过麦克风（之后被拒时显示说明） */
    private boolean micAsked;

    public void onMicRequested() {
        micAsked = true;
    }

    /** 左右滑动切换 */
    public void onSwipe(boolean toLeft) {
        switchMode(toLeft ? CameraArgs.MODE_VIDEO : CameraArgs.MODE_PHOTO);
    }

    // ===== 快门 =====

    /** 快门：拍照模式拍一张（回调返回前不能再次拍照）；录像模式开始或停止录像 */
    public void onShutter() {
        if (Boolean.TRUE.equals(recording.getValue())) {
            cameraCommand.setValue(CMD_STOP_RECORD);
            return;
        }
        if (!Boolean.TRUE.equals(shutterEnabled.getValue())) {
            if (isVideoMode() && isVideoTaken()) {
                contextText.setValue("已有 1 段视频，删除后才能再录");
            }
            return;
        }
        capturing.setValue(true);
        updateTexts();
        if (isVideoMode()) {
            discardRecording = false;
            recordLimitReached = false;
            cameraCommand.setValue(CMD_START_RECORD);
        } else if (args.isAllowVideo()) {
            cameraCommand.setValue(CMD_TAKE_PHOTO);
        } else {
            takePicture.call();
        }
    }

    public void onPhotoSaved(@NonNull String path) {
        capturing.setValue(false);
        captured.add(path);
        getSavedState().set(STATE_CAPTURED, new ArrayList<>(captured));
        lastThumb.setValue(path);
        if (args.isQueueMode() && queue != null) {
            enqueue(path, UploadItem.MEDIA_IMAGE);
        }
        updateTexts();
        if (getRemaining() <= 0 && !args.isQueueMode()) {
            finishEvent.setValue(CameraResult.photos(captured));
        }
    }

    public void onPhotoFailed() {
        capturing.setValue(false);
        updateTexts();
        showMessage(MSG_CAPTURE_FAILED);
    }

    // ===== 录像 =====

    /** 录像已开始 */
    public void onRecordStarted() {
        capturing.setValue(false);
        recording.setValue(true);
        elapsedText.setValue(formatElapsed(0));
        recordProgress.setValue(0);
        updateTexts();
    }

    /** VideoRecordEvent.Status：刷新“0:12 / 0:30”与进度条 */
    public void onRecordStatus(long recordedNanos) {
        long ms = recordedNanos / 1_000_000L;
        elapsedText.setValue(formatElapsed(ms));
        recordProgress.setValue((int) Math.min(PROGRESS_MAX, ms * PROGRESS_MAX / MAX_RECORD_MS));
    }

    /**
     * VideoRecordEvent.Finalize。
     *
     * @param path         输出文件；失败时为 null
     * @param limitReached 达到 30 秒上限（视为正常）
     * @param errorMessage 失败原因，正常时为 null
     */
    public void onRecordFinalized(@Nullable String path, long durationMs, boolean limitReached,
                                  @Nullable String errorMessage) {
        recording.setValue(false);
        capturing.setValue(false);
        recordProgress.setValue(0);
        elapsedText.setValue(formatElapsed(0));
        if (discardRecording) {
            discardRecording = false;
            updateTexts();
            if (pendingClose) {
                pendingClose = false;
                onClose();
            }
            return;
        }
        if (path == null || errorMessage != null) {
            updateTexts();
            showMessage(errorMessage != null ? errorMessage : MSG_RECORD_FAILED);
            return;
        }
        videoPath = path;
        recordLimitReached = limitReached;
        getSavedState().set(STATE_VIDEO_PATH, path);
        lastThumb.setValue(path);
        if (args.isQueueMode() && queue != null) {
            enqueue(path, UploadItem.MEDIA_VIDEO);
        }
        updateTexts();
    }

    /** 录像中点关闭：先确认 */
    private boolean pendingClose;

    /** “放弃这段录像？”选“放弃”：停止录像并删除文件，然后关闭 */
    public void onDiscardRecordingConfirmed() {
        discardRecording = true;
        pendingClose = true;
        cameraCommand.setValue(CMD_STOP_RECORD);
    }

    /** 录像过程中页面进入后台：停止并保留已录内容 */
    public void onStopWhileRecording() {
        if (Boolean.TRUE.equals(recording.getValue())) {
            cameraCommand.setValue(CMD_STOP_RECORD);
        }
    }

    public boolean shouldDeleteFinalizedFile() {
        return discardRecording;
    }

    @NonNull
    public static String formatElapsed(long ms) {
        long s = Math.max(0, Math.min(MAX_RECORD_MS, ms)) / 1000;
        return String.format(Locale.ROOT, "%d:%02d / 0:%02d", s / 60, s % 60, MAX_RECORD_MS / 1000);
    }

    // ===== 上传队列 =====

    private void enqueue(@NonNull String path, @NonNull String mediaType) {
        UploadQueue q = queue;
        String page = args.getPageCode();
        if (q == null || page == null) {
            return;
        }
        String scene = args.getScene() == null ? "" : args.getScene();
        queuedCount++;
        getSavedState().set(STATE_QUEUED, queuedCount);
        boolean video = UploadItem.MEDIA_VIDEO.equals(mediaType);
        launch(q.enqueue(path, mediaType, scene, page, args.getFarmId()), id -> {
            if (video) {
                videoTaskId = id;
                getSavedState().set(STATE_VIDEO_TASK, id);
                observeVideoTask(id);
            }
        }, e -> {
            queuedCount--;
            getSavedState().set(STATE_QUEUED, queuedCount);
            showMessage(video ? MSG_RECORD_FAILED : MSG_CAPTURE_FAILED);
            updateTexts();
        });
    }

    /** 观察刚录的视频任务：转码中显示“正在压缩视频 45%”，完成后“已添加 3 / 6” */
    private void observeVideoTask(long taskId) {
        UploadQueue q = queue;
        String page = args.getPageCode();
        if (q == null || page == null) {
            return;
        }
        if (videoTaskSub != null) {
            videoTaskSub.dispose();
        }
        videoTaskStatus = UploadTaskStates.WAITING;
        videoTaskSub = q.observe(page, args.getFarmId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    UploadTaskEntity found = null;
                    for (UploadTaskEntity t : list) {
                        if (t.id == taskId) {
                            found = t;
                        }
                    }
                    if (found == null) {
                        videoTaskStatus = null;
                    } else {
                        videoTaskStatus = found.status;
                        videoTaskProgress = found.progress;
                        videoTaskError = found.error;
                    }
                    updateTexts();
                }, e -> {
                });
        disposables.add(videoTaskSub);
    }

    // ===== 其他操作 =====

    /** 闪光灯：关闭 → 自动 → 开启（录像模式中“开启”为常亮补光） */
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
        if (!Boolean.TRUE.equals(canSwitchLens.getValue()) || Boolean.TRUE.equals(capturing.getValue())
                || Boolean.TRUE.equals(recording.getValue())) {
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

    /** 关闭或返回：录像中先确认；原有用法把已拍照片带回；上传队列模式已写入队列 */
    public void onClose() {
        if (Boolean.TRUE.equals(recording.getValue())) {
            showDiscardRecording.call();
            return;
        }
        if (args.isQueueMode()) {
            finishEvent.setValue(CameraResult.queued(queuedCount));
        } else {
            finishEvent.setValue(CameraResult.photos(captured));
        }
    }

    /** 左下角缩略图：查看本次拍的照片和视频 */
    public void onThumbClick() {
        List<String> all = new ArrayList<>(captured);
        if (videoPath != null) {
            all.add(videoPath);
        }
        if (!all.isEmpty()) {
            openViewer.setValue(all);
        }
    }

    @Nullable
    public String getVideoPath() {
        return videoPath;
    }

    // ===== 文字 =====

    private void updateTexts() {
        if (args.isScan()) {
            return;
        }
        if (!args.isAllowVideo()) {
            updatePhotoOnlyTexts();
            return;
        }
        boolean video = isVideoMode();
        boolean rec = Boolean.TRUE.equals(recording.getValue());
        int total = args.getTotalCount() > 0 ? args.getTotalCount() : args.getMaxCount();
        int added = Math.max(0, args.getAddedCount()) + queuedCountShown();
        boolean full = getRemaining() <= 0;
        String count;
        if (full) {
            count = String.format(Locale.ROOT, "已添加 %d / %d，删除后再添加", Math.min(added, total), total);
        } else {
            count = String.format(Locale.ROOT, "已添加 %d / %d", added, total);
        }
        String processing = processingText();
        if (processing != null) {
            contextText.setValue(processing);
        } else {
            String title = args.getTitle();
            contextText.setValue(!TextUtils.isEmpty(title) ? title + " · " + count : count);
        }
        remainingText.setValue(video ? "录像不超过 30 秒，自动压缩为 720p"
                : String.format(Locale.ROOT, "照片和视频合计最多 %d 个", total));
        boolean granted = Boolean.TRUE.equals(cameraGranted.getValue());
        boolean busy = Boolean.TRUE.equals(capturing.getValue());
        if (video) {
            shutterStyle.setValue(rec ? SHUTTER_RECORDING : SHUTTER_VIDEO);
            shutterEnabled.setValue(rec || (granted && !busy && !full && !isVideoTaken()
                    && Boolean.TRUE.equals(micGranted.getValue())));
        } else {
            shutterStyle.setValue(SHUTTER_PHOTO);
            shutterEnabled.setValue(granted && !busy && !full);
        }
    }

    /** 视频转码完成前不计入“已添加”（文档：完成后计数加 1） */
    private int queuedCountShown() {
        if (videoPath != null && UploadTaskStates.COMPRESSING.equals(videoTaskStatus)
                || UploadTaskStates.WAITING.equals(videoTaskStatus) && videoPath != null) {
            return Math.max(0, queuedCount - 1);
        }
        return queuedCount;
    }

    @Nullable
    private String processingText() {
        if (videoPath == null || videoTaskStatus == null) {
            return null;
        }
        if (UploadTaskStates.FAILED.equals(videoTaskStatus)) {
            return videoTaskError != null ? videoTaskError : "视频处理失败，可在表单中点按重试";
        }
        if (UploadTaskStates.WAITING.equals(videoTaskStatus) || UploadTaskStates.COMPRESSING.equals(videoTaskStatus)) {
            if (videoTaskProgress <= 0) {
                return recordLimitReached ? "已录满 30 秒，正在压缩视频" : "正在压缩视频";
            }
            return String.format(Locale.ROOT, "正在压缩视频 %d%%", videoTaskProgress);
        }
        return null;
    }

    private void updatePhotoOnlyTexts() {
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
        shutterStyle.setValue(SHUTTER_PHOTO);
        shutterEnabled.setValue(Boolean.TRUE.equals(cameraGranted.getValue())
                && !Boolean.TRUE.equals(capturing.getValue()) && remaining > 0);
    }
}
