package com.zhimu.slmp.health.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.LinkableReportVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.math.BigDecimal;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 本模块复用的其他模块接口（在本组件内声明，字段以对方模块为准）：
 * API-M27 批号选项（模块09）、API-I24 可关联上报（模块07）、API-F16 成员（模块02）、
 * API-U01 / U02 修改申请（公共）、API-L07 栏舍下拉（模块04，待定事项 2：usage = OPTIONS 只含启用栏舍）。
 * 修改记录 API-U14 用 lib_common 的 BaseDataRepository。
 */
public interface HealthSharedApi {

    /** API-M27 批号选项；purpose：VACCINATE / MEDICATE / DISINFECT（按接口所属模块09 统一）；need_qty 选填 */
    @GET("api/v1/drug-lots/options")
    Single<ApiResponse<DrugLotOptionsVO>> getLotOptions(@Query("drug_id") long drugId,
                                                        @Query("purpose") String purpose,
                                                        @Nullable @Query("need_qty") BigDecimal needQty);

    /** API-I24 可关联的异常上报与诊断（batch_id 或 pen_id 二选一） */
    @GET("api/v1/abnormal-reports/linkable")
    Single<ApiResponse<List<LinkableReportVO>>> getLinkableReports(@Nullable @Query("batch_id") Long batchId,
                                                                   @Nullable @Query("pen_id") Long penId);

    /** API-F16 本场成员（实际执行人下拉，只有所有者、场长调用） */
    @GET("api/v1/farms/{farmId}/members")
    Single<ApiResponse<PageVO<MemberItemVO>>> getMembers(@Path("farmId") long farmId, @Query("role") String role,
                                                         @Query("page") int page, @Query("size") int size);

    /** API-U01 提交修改申请的表单上下文（取 record_version） */
    @GET("api/v1/change-requests/form")
    Single<ApiResponse<ChangeRequestFormVO>> getChangeRequestForm(@Query("biz_type") String bizType,
                                                                  @Query("biz_id") long bizId);

    /** API-U02 提交修改申请 */
    @POST("api/v1/change-requests")
    Single<ApiResponse<ChangeRequestVO>> submitChangeRequest(@Body ChangeRequestSubmitRequest body,
                                                             @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-L07 栏舍下拉（usage = OPTIONS，不分页，只含启用中栏舍；员工由服务端裁剪为负责栏舍） */
    @GET("api/v1/pens")
    Single<ApiResponse<List<PenOptionVO>>> getPenOptions(@Query("usage") String usage);
}
