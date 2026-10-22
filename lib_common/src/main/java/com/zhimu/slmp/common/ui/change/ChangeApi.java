package com.zhimu.slmp.common.ui.change;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 修改申请公共接口 API-U01–U04（04 号 15.3 节）。
 */
public interface ChangeApi {

    /** API-U01 提交修改申请的表单上下文 */
    @GET("api/v1/change-requests/form")
    Single<ApiResponse<ChangeRequestFormVO>> getForm(@Query("biz_type") String bizType, @Query("biz_id") long bizId);

    /** API-U02 提交修改申请 */
    @POST("api/v1/change-requests")
    Single<ApiResponse<ChangeRequestVO>> submit(@Body ChangeRequestSubmitRequest body,
                                                    @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-U03 我的修改申请（status 为空时为全部） */
    @GET("api/v1/change-requests")
    Single<ApiResponse<PageVO<ChangeRequestVO>>> list(@Nullable @Query("status") String status,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-U04 修改申请详情 */
    @GET("api/v1/change-requests/{id}")
    Single<ApiResponse<ChangeRequestVO>> detail(@Path("id") long id);
}
