package com.zhimu.slmp.ai.data;

import com.zhimu.slmp.ai.model.request.AiAskRequest;
import com.zhimu.slmp.network.Headers;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

/**
 * 模块11 流式接口（API-K06、K07，text/event-stream）：由流式 OkHttpClient（读超时 180 秒）创建，返回原始响应体逐行读取。
 */
public interface AiStreamApi {

    /** API-K06 提问 */
    @Streaming
    @POST("api/v1/ai/messages")
    @retrofit2.http.Headers("Accept: text/event-stream")
    Call<ResponseBody> ask(@Header(Headers.IDEMPOTENCY_KEY) String idemKey, @Body AiAskRequest request);

    /** API-K07 重新生成（无请求体、不需要幂等键） */
    @Streaming
    @POST("api/v1/ai/messages/{id}/retry")
    @retrofit2.http.Headers("Accept: text/event-stream")
    Call<ResponseBody> retry(@Path("id") long messageId);
}
