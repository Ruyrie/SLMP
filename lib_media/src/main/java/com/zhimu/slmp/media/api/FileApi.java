package com.zhimu.slmp.media.api;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

/**
 * 公共文件接口（04 号服务端规范 8.2 节）：上传用 CoreComponent 的 uploadRetrofit 创建，查询与删除用默认 Retrofit。
 */
public interface FileApi {

    /** API-U05 上传文件（multipart：file、scene），返回 FileVO；校验失败 40002 / 40003 */
    @Multipart
    @POST("api/v1/files")
    Single<ApiResponse<FileVO>> upload(@Part MultipartBody.Part file,
                                       @Part("scene") RequestBody scene,
                                       @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /**
     * API-U05 上传照片或视频（上传队列使用）：视频另带 cover（第 0 秒帧 JPEG）与 duration_ms；
     * X-Farm-Id 取任务所属养殖场（切换养殖场后任务按原养殖场继续）。为 null 的部分不发送。
     */
    @Multipart
    @POST("api/v1/files")
    Single<ApiResponse<FileVO>> uploadMedia(@Part MultipartBody.Part file,
                                            @Part("scene") RequestBody scene,
                                            @Nullable @Part MultipartBody.Part cover,
                                            @Nullable @Part("duration_ms") RequestBody durationMs,
                                            @Nullable @Part("shot_at") RequestBody shotAt,
                                            @Header(Headers.IDEMPOTENCY_KEY) String idemKey,
                                            @Nullable @Header(Headers.FARM_ID) String farmId);

    /** API-U06 文件信息与新的临时地址（地址过期时刷新） */
    @GET("api/v1/files/{id}")
    Single<ApiResponse<FileVO>> getFile(@Path("id") long fileId);

    /** API-U07 删除尚未被记录引用的上传 */
    @DELETE("api/v1/files/{id}")
    Single<ApiResponse<Object>> deleteFile(@Path("id") long fileId);
}
