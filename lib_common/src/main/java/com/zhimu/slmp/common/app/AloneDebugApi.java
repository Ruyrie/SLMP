package com.zhimu.slmp.common.app;

import com.google.gson.JsonObject;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.network.model.ApiResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * 单独运行调试启动页使用的接口（正式页面使用模块01、模块02 各自的 Api）。
 */
interface AloneDebugApi {

    /** API-A04 密码登录 */
    @POST("api/v1/auth/login/password")
    Single<ApiResponse<AuthTokenVO>> loginByPassword(@Body JsonObject body);

    /** API-F02 养殖场上下文 */
    @GET("api/v1/farms/{farmId}/context")
    Single<ApiResponse<FarmContextVO>> getFarmContext(@Path("farmId") long farmId);
}
