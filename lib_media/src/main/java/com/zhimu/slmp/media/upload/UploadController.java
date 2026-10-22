package com.zhimu.slmp.media.upload;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.mvvm.UiMessage;

import java.util.List;

/**
 * 照片网格的数据与操作（由宿主 ViewModel 持有，MediaPickerView 只观察和转发点击）。实现类为 {@link UploadManager}。
 * 所有方法在主线程调用。
 */
public interface UploadController {

    /** 网格内容（按网格顺序） */
    @NonNull
    LiveData<List<UploadItem>> getItems();

    /** 需要用 Snackbar 告知的提示（类型不符、超出数量、校验失败），只消费一次 */
    @NonNull
    LiveData<UiMessage> getMessage();

    /** 最多张数 */
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
}
