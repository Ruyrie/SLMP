package com.zhimu.slmp.media.upload;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.mvvm.UiMessage;

import java.util.List;

/**
 * 照片网格的数据与操作（由宿主 ViewModel 持有，MediaPickerView 只观察和转发点击）。实现类：
 * {@link UploadManager}（只有照片，选中即压缩上传，F02、F06、L11 使用）与
 * {@link MediaQueueController}（照片和视频，Room upload_task + WorkManager 后台上传，模块07 使用）。
 * 所有方法在主线程调用。
 */
public interface UploadController {

    /** 网格内容（按网格顺序） */
    @NonNull
    LiveData<List<UploadItem>> getItems();

    /** 需要用 Snackbar 告知的提示（类型不符、超出数量、校验失败），只消费一次 */
    @NonNull
    LiveData<UiMessage> getMessage();

    /** 最多张数（照片和视频合计） */
    int getMaxCount();

    /** 上传场景（MediaScene） */
    @NonNull
    String getScene();

    /** 还能添加几张 */
    int getRemaining();

    /** 从相册选择的图片（content://），校验后立即压缩上传 */
    void addUris(@NonNull List<Uri> uris);

    /** 相机页拍摄的照片（cacheDir/capture 下的文件路径） */
    void addCapturedFiles(@NonNull List<String> paths);

    /** 失败项点按重试 */
    void retry(@NonNull String itemId);

    /** 删除一项：上传中的取消；已上传的在撤销时限（4 秒）过后调用 API-U07；remote 项只从网格移除 */
    void remove(@NonNull String itemId);

    /** 撤销最近一次删除 */
    void undoRemove();

    // ===== 提交前检查 =====

    /** 已上传完成的 file_id，按网格顺序 */
    @NonNull
    List<Long> getFileIds();

    /** 还有等待、压缩或上传中的项 */
    boolean hasUploading();

    /** 有上传失败的项 */
    boolean hasFailed();

    int getFailedCount();

    // ===== 视频（MediaQueueController 支持；UploadManager 保持默认值，只收照片） =====

    /** 最多几段视频；0 表示只收照片 */
    default int getMaxVideoCount() {
        return 0;
    }

    /** 网格中已有视频（含上传中） */
    default boolean hasVideo() {
        List<UploadItem> list = getItems().getValue();
        if (list != null) {
            for (UploadItem item : list) {
                if (item.isVideo()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 上传队列的页面编号：不为 null 时相机页把拍摄结果直接写入 upload_task（page_code 为此值），
     * 网格通过观察数据库更新；为 null 时相机页把照片路径带回，由 {@link #addCapturedFiles} 加入。
     */
    @Nullable
    default String getQueuePageCode() {
        return null;
    }

    /** 上传队列任务所属的养殖场（与 page_code 一起区分任务） */
    default long getQueueFarmId() {
        return 0;
    }
}
