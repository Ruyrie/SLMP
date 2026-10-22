package com.zhimu.slmp.common.session;

import com.google.gson.JsonObject;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.network.model.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 刷新令牌接口，由不带 Authenticator 的刷新专用客户端创建，在 OkHttp 线程同步调用。
 */
public interface SessionApi {

    /** API-A06 刷新令牌；请求体 {"refresh_token": "..."} */
    @POST("api/v1/auth/token/refresh")
    Call<ApiResponse<AuthTokenVO>> refresh(@Body JsonObject body);
}
