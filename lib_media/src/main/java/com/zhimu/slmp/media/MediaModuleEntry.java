package com.zhimu.slmp.media;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.IAccountCleanupService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.di.MediaInjector;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.ui.viewer.ViewerItem;
import com.zhimu.slmp.media.util.MediaFiles;
import com.zhimu.slmp.media.queue.UploadQueue;
import androidx.work.WorkManager;
import io.reactivex.rxjava3.core.Completable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 多媒体组件入口：创建 MediaComponent，后台清理超过 24 小时的拍照与压缩临时文件。
 * 本组件被业务组件直接依赖（CameraContract、PickImagesContract、MediaPickerView、UploadManager、MediaViewer），
 * 向 ServiceHub 注册注销时停止上传任务的清理服务。
 */
public class MediaModuleEntry implements IModuleEntry {

    /** 照片网格演示页（只在 debug 包中存在，见 src/debug） */
    public static final String ACTION_PICKER_DEMO = "com.zhimu.slmp.media.debug.PICKER_DEMO";

    /** Mock 示例图（src/debug/assets/mock/media，release 包中不存在） */
    public static final String MOCK_IMAGE_BASE = "file:///android_asset/mock/media/";

    /** 调试入口写入上传队列时使用的页面编号 */
    public static final String DEBUG_PAGE_CODE = "DEBUG_I08";

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        MediaInjector.init(core);
        ServiceHub.register(IAccountCleanupService.class, () -> Completable.fromAction(() ->
                WorkManager.getInstance(app).cancelAllWorkByTag(UploadQueue.TAG_ALL).getResult()
                        .get(5, java.util.concurrent.TimeUnit.SECONDS)));
        Schedulers.io().scheduleDirect(() -> MediaFiles.cleanupExpired(app));
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("I08 相机 · 拍照（死淘登记，本次最多 3 张）", RouterPath.I08,
                CameraArgs.photo(MediaScene.DEATH, 3).title("死淘登记").counts(0, 6).toBundle()));
        pages.add(new DebugPage("I08 相机 · 扫码（L07 扫码查看档案）", RouterPath.I08,
                CameraArgs.scan().title("扫码查看档案")
                        .scanHint("将批次二维码放入框内，即可自动识别", "识别后直接打开批次档案")
                        .manualInput("输入批次号", "二维码模糊或破损时，也可以输入批次号")
                        .toBundle()));
        pages.add(new DebugPage("I08 相机 · 拍照与录像（登记巡检 · 3 号育肥舍，写入上传队列）", RouterPath.I08,
                CameraArgs.media(MediaScene.INSPECTION, 6, DEBUG_PAGE_CODE, 0)
                        .title("登记巡检 · 3 号育肥舍").counts(0, 6).toBundle()));
        pages.add(new DebugPage("I08 相机 · 录像模式（30 秒自动停止，需要麦克风）", RouterPath.I08,
                CameraArgs.media(MediaScene.ABNORMAL, 4, DEBUG_PAGE_CODE, 0).startWithVideo(true)
                        .title("异常上报 · 3 号育肥舍").counts(2, 6).toBundle()));
        List<ViewerItem> items = Arrays.asList(
                new ViewerItem(9001, MOCK_IMAGE_BASE + "qual_license.jpg", null, "IMAGE", null,
                        "养殖场资质图 · 东山猪场", null),
                new ViewerItem(9003, MOCK_IMAGE_BASE + "farm_env.jpg", null, "IMAGE", null,
                        "巡检 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:10 拍摄"),
                new ViewerItem(9004, MOCK_IMAGE_BASE + "death_photo.jpg", null, "IMAGE", null,
                        "死淘登记 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:28 拍摄"));
        pages.add(new DebugPage("I09 全屏预览（示例图，第 2 张起）", RouterPath.I09, MediaViewer.extras(items, 1)));
        List<ViewerItem> media = Arrays.asList(
                new ViewerItem(9005, MOCK_IMAGE_BASE + "inspection.jpg", null, "IMAGE", null,
                        "异常上报 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:08 拍摄 · 照片"),
                new ViewerItem(9003, MOCK_IMAGE_BASE + "farm_env.jpg", null, "IMAGE", null,
                        "异常上报 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:09 拍摄 · 照片"),
                new ViewerItem(9006, MOCK_IMAGE_BASE + "inspection_video.mp4",
                        MOCK_IMAGE_BASE + "inspection_video_cover.jpg", "VIDEO", 18,
                        "异常上报 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:10 拍摄 · 视频 0:18"),
                new ViewerItem(9004, MOCK_IMAGE_BASE + "death_photo.jpg", null, "IMAGE", null,
                        "异常上报 · 3 号育肥舍 · 拍摄人李明", "10月19日 09:12 拍摄 · 照片"));
        pages.add(new DebugPage("I09 视频预览（异常上报，第 3 个为视频 0:18，进入时不自动播放）", RouterPath.I09,
                MediaViewer.extras(media, 2)));
        pages.add(new DebugPage("照片网格演示（拍照 / 相册 / 上传 / 重试 / 删除 / 只读）", ACTION_PICKER_DEMO));
        return pages;
    }
}
