package com.zhimu.slmp.media.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.api.FileApi;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.di.NetworkModule;
import com.zhimu.slmp.network.upload.ProgressRequestBody;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Retrofit;

/**
 * 文件接口仓库：API-U05 上传（带进度）、U06 刷新地址、U07 删除。
 * <p>
 * 构造方法只依赖 CoreComponent 暴露的对象，业务组件的 Dagger 组件可以直接注入（不需要额外 Module）。
 */
public class MediaRepository {

    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

    private final FileApi api;
    private final FileApi uploadApi;

    @Inject
    public MediaRepository(@NonNull Retrofit retrofit, @Named(NetworkModule.UPLOAD) @NonNull Retrofit uploadRetrofit) {
        this.api = retrofit.create(FileApi.class);
        this.uploadApi = uploadRetrofit.create(FileApi.class);
    }

    /**
     * API-U05 上传一个文件。
     *
     * @param mimeType 例如 image/jpeg
     * @param idemKey  幂等键（UploadItem 的 id）
     * @param listener 进度回调（OkHttp 线程），可为 null
     */
    @NonNull
    public Single<FileVO> upload(@NonNull File file, @NonNull String mimeType, @NonNull String scene,
                                 @NonNull String idemKey, @Nullable ProgressRequestBody.Listener listener) {
        return Single.defer(() -> {
            RequestBody fileBody = RequestBody.create(file, MediaType.parse(mimeType));
            RequestBody body = listener == null ? fileBody : new ProgressRequestBody(fileBody, listener);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);
            return uploadApi.upload(part, RequestBody.create(scene, TEXT), idemKey).compose(RxResult.unwrap());
        });
    }

    /**
     * API-U05 上传照片或视频（上传队列的 MediaUploadWorker 调用）。
     *
     * @param cover      视频封面（第 0 秒帧 JPEG），照片为 null
     * @param durationMs 视频时长，照片为 null
     * @param shotAt     拍摄时间 yyyy-MM-dd HH:mm:ss，可为 null
     * @param farmId     任务所属养殖场（X-Farm-Id），0 时由拦截器取当前养殖场
     */
    @NonNull
    public Single<FileVO> uploadMedia(@NonNull File file, @NonNull String mimeType, @NonNull String scene,
                                      @NonNull String idemKey, @Nullable ProgressRequestBody.Listener listener,
                                      @Nullable File cover, @Nullable Long durationMs, @Nullable String shotAt,
                                      long farmId) {
        return Single.defer(() -> {
            RequestBody fileBody = RequestBody.create(file, MediaType.parse(mimeType));
            RequestBody body = listener == null ? fileBody : new ProgressRequestBody(fileBody, listener);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);
            MultipartBody.Part coverPart = cover == null || !cover.isFile() ? null
                    : MultipartBody.Part.createFormData("cover", cover.getName(),
                    RequestBody.create(cover, MediaType.parse("image/jpeg")));
            RequestBody duration = durationMs == null ? null : RequestBody.create(String.valueOf(durationMs), TEXT);
            RequestBody shot = shotAt == null ? null : RequestBody.create(shotAt, TEXT);
            return uploadApi.uploadMedia(part, RequestBody.create(scene, TEXT), coverPart, duration, shot, idemKey,
                    farmId > 0 ? String.valueOf(farmId) : null).compose(RxResult.unwrap());
        });
    }

    /** API-U06 文件信息与新的临时地址 */
    @NonNull
    public Single<FileVO> getFile(long fileId) {
        return api.getFile(fileId).compose(RxResult.unwrap());
    }

    /** API-U07 删除尚未被记录引用的上传 */
    @NonNull
    public Completable deleteFile(long fileId) {
        return RxResult.completable(api.deleteFile(fileId));
    }
}
