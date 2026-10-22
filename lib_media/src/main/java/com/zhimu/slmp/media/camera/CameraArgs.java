package com.zhimu.slmp.media.camera;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.zhimu.slmp.common.router.RouterPath;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 相机页 I08 的启动参数（用 {@link CameraContract} 启动）。
 * <pre>
 * CameraArgs.photo(MediaScene.DEATH, 4).title("死淘登记").counts(2, 6);
 * CameraArgs.scan().title("扫码查看档案")
 *         .scanHint("将批次二维码放入框内，即可自动识别", "识别后直接打开批次档案")
 *         .manualInput("输入批次号", "二维码模糊或破损时，也可以输入批次号");
 * </pre>
 */
public final class CameraArgs {

    /** 拍照 */
    public static final String MODE_PHOTO = "PHOTO";
    /** 扫码（ZXing 识别二维码，返回原文） */
    public static final String MODE_SCAN = "SCAN";
    /** 录像（模块07：进入时为录像模式，可切换到拍照；需要上传队列模式，见 {@link #media}） */
    public static final String MODE_VIDEO = "VIDEO";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({MODE_PHOTO, MODE_SCAN, MODE_VIDEO})
    public @interface Mode {
    }

    static final String KEY_MODE = RouterPath.EXTRA_MODE;
    static final String KEY_TITLE = RouterPath.EXTRA_TITLE;
    static final String KEY_SCENE = "scene";
    static final String KEY_MAX_COUNT = "max_count";
    static final String KEY_ADDED_COUNT = "added_count";
    static final String KEY_TOTAL_COUNT = "total_count";
    static final String KEY_SCAN_HINT = "scan_hint";
    static final String KEY_SCAN_SUB_HINT = "scan_sub_hint";
    static final String KEY_MANUAL_TEXT = "manual_text";
    static final String KEY_MANUAL_HINT = "manual_hint";
    static final String KEY_ALLOW_VIDEO = "allow_video";
    static final String KEY_HAS_VIDEO = "has_video";
    static final String KEY_PAGE_CODE = "page_code";
    static final String KEY_FARM_ID = "farm_id";

    @NonNull
    private String mode;
    @Nullable
    private String scene;
    private int maxCount = 1;
    private int addedCount = -1;
    private int totalCount = -1;
    @Nullable
    private String title;
    @Nullable
    private String scanHint;
    @Nullable
    private String scanSubHint;
    @Nullable
    private String manualText;
    @Nullable
    private String manualHint;
    private boolean allowVideo;
    private boolean hasVideo;
    @Nullable
    private String pageCode;
    private long farmId;

    private CameraArgs(@NonNull String mode) {
        this.mode = mode;
    }

    /**
     * 拍照模式。
     *
     * @param scene    上传场景（MediaScene），只用于说明，相机页不上传
     * @param maxCount 本次最多可拍张数（拍满自动返回）
     */
    @NonNull
    public static CameraArgs photo(@Nullable String scene, int maxCount) {
        CameraArgs a = new CameraArgs(MODE_PHOTO);
        a.scene = scene;
        a.maxCount = Math.max(1, maxCount);
        return a;
    }

    /**
     * 拍照与录像（模块07 照片和视频网格）：拍摄结果由相机页直接写入上传队列 upload_task（page_code、farm_id），
     * 网格观察数据库自动出现；录像 30 秒自动停止，需要麦克风权限。
     *
     * @param maxCount 本次最多还能添加几个（照片和视频合计）
     * @param pageCode 上传队列的页面编号（与网格的 MediaQueueController 一致）
     */
    @NonNull
    public static CameraArgs media(@Nullable String scene, int maxCount, @NonNull String pageCode, long farmId) {
        CameraArgs a = new CameraArgs(MODE_PHOTO);
        a.scene = scene;
        a.maxCount = Math.max(1, maxCount);
        a.allowVideo = true;
        a.pageCode = pageCode;
        a.farmId = farmId;
        return a;
    }

    /** 进入时为录像模式（添加面板选“录像”） */
    @NonNull
    public CameraArgs startWithVideo(boolean video) {
        if (allowVideo) {
            this.mode = video ? MODE_VIDEO : MODE_PHOTO;
        }
        return this;
    }

    /** 网格中已有视频：录像段置灰，点击提示“已有 1 段视频，删除后才能再录” */
    @NonNull
    public CameraArgs hasVideo(boolean has) {
        this.hasVideo = has;
        return this;
    }

    /** 扫码模式：识别到二维码后震动并返回原文 */
    @NonNull
    public static CameraArgs scan() {
        return new CameraArgs(MODE_SCAN);
    }

    /** 拍照页：说明胶囊的前缀（如“死淘登记 · 3 号育肥舍”）；扫码页：顶部标题（如“扫码查看档案”） */
    @NonNull
    public CameraArgs title(@Nullable String title) {
        this.title = title;
        return this;
    }

    /** 拍照页说明胶囊显示“已添加 added / total”（added 为进入相机前网格中已有的张数） */
    @NonNull
    public CameraArgs counts(int added, int total) {
        this.addedCount = added;
        this.totalCount = total;
        return this;
    }

    /** 扫码页取景框下方的说明（两行） */
    @NonNull
    public CameraArgs scanHint(@Nullable String hint, @Nullable String subHint) {
        this.scanHint = hint;
        this.scanSubHint = subHint;
        return this;
    }

    /** 扫码页底部“手动输入”入口：按钮文字与上方说明；点击后返回 {@link CameraResult#TYPE_MANUAL_INPUT} */
    @NonNull
    public CameraArgs manualInput(@Nullable String buttonText, @Nullable String hint) {
        this.manualText = buttonText;
        this.manualHint = hint;
        return this;
    }

    @NonNull
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(KEY_MODE, mode);
        b.putString(KEY_SCENE, scene);
        b.putInt(KEY_MAX_COUNT, maxCount);
        b.putInt(KEY_ADDED_COUNT, addedCount);
        b.putInt(KEY_TOTAL_COUNT, totalCount);
        b.putString(KEY_TITLE, title);
        b.putString(KEY_SCAN_HINT, scanHint);
        b.putString(KEY_SCAN_SUB_HINT, scanSubHint);
        b.putString(KEY_MANUAL_TEXT, manualText);
        b.putString(KEY_MANUAL_HINT, manualHint);
        b.putBoolean(KEY_ALLOW_VIDEO, allowVideo);
        b.putBoolean(KEY_HAS_VIDEO, hasVideo);
        b.putString(KEY_PAGE_CODE, pageCode);
        b.putLong(KEY_FARM_ID, farmId);
        return b;
    }

    /** 页面参数来源（SavedStateHandle::get 或 Bundle::get） */
    public interface Source {
        @Nullable
        Object get(@NonNull String key);
    }

    /** 从页面参数读取；缺少 mode 时按拍照处理 */
    @NonNull
    public static CameraArgs from(@NonNull Source src) {
        Object mode = src.get(KEY_MODE);
        CameraArgs a = new CameraArgs(MODE_SCAN.equals(mode) ? MODE_SCAN
                : MODE_VIDEO.equals(mode) ? MODE_VIDEO : MODE_PHOTO);
        a.scene = string(src.get(KEY_SCENE));
        a.maxCount = Math.max(1, integer(src.get(KEY_MAX_COUNT), 1));
        a.addedCount = integer(src.get(KEY_ADDED_COUNT), -1);
        a.totalCount = integer(src.get(KEY_TOTAL_COUNT), -1);
        a.title = string(src.get(KEY_TITLE));
        a.scanHint = string(src.get(KEY_SCAN_HINT));
        a.scanSubHint = string(src.get(KEY_SCAN_SUB_HINT));
        a.manualText = string(src.get(KEY_MANUAL_TEXT));
        a.manualHint = string(src.get(KEY_MANUAL_HINT));
        a.allowVideo = Boolean.TRUE.equals(src.get(KEY_ALLOW_VIDEO));
        a.hasVideo = Boolean.TRUE.equals(src.get(KEY_HAS_VIDEO));
        a.pageCode = string(src.get(KEY_PAGE_CODE));
        Object farm = src.get(KEY_FARM_ID);
        a.farmId = farm instanceof Long ? (Long) farm : 0;
        if (!a.allowVideo && MODE_VIDEO.equals(a.mode)) {
            a.mode = MODE_PHOTO;
        }
        return a;
    }

    @Nullable
    private static String string(@Nullable Object o) {
        return o instanceof String && !((String) o).isEmpty() ? (String) o : null;
    }

    private static int integer(@Nullable Object o, int def) {
        return o instanceof Integer ? (Integer) o : def;
    }

    // ===== 读取（相机页使用） =====

    @NonNull
    public String getMode() {
        return mode;
    }

    public boolean isScan() {
        return MODE_SCAN.equals(mode);
    }

    /** 进入时为录像模式 */
    public boolean isVideo() {
        return MODE_VIDEO.equals(mode);
    }

    /** 显示拍照 / 录像切换 */
    public boolean isAllowVideo() {
        return allowVideo;
    }

    /** 网格中已有视频 */
    public boolean isHasVideo() {
        return hasVideo;
    }

    /** 上传队列模式：拍摄结果直接写入 upload_task */
    public boolean isQueueMode() {
        return pageCode != null;
    }

    @Nullable
    public String getPageCode() {
        return pageCode;
    }

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getScene() {
        return scene;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getScanHint() {
        return scanHint;
    }

    @Nullable
    public String getScanSubHint() {
        return scanSubHint;
    }

    @Nullable
    public String getManualText() {
        return manualText;
    }

    @Nullable
    public String getManualHint() {
        return manualHint;
    }
}
