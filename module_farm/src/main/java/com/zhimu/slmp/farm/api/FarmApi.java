package com.zhimu.slmp.farm.api;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.model.request.CreateFarmRequest;
import com.zhimu.slmp.farm.model.request.CreateTransferRequest;
import com.zhimu.slmp.farm.model.request.FarmVersionRequest;
import com.zhimu.slmp.farm.model.request.InviteMemberRequest;
import com.zhimu.slmp.farm.model.request.SubmitFarmChangeRequest;
import com.zhimu.slmp.farm.model.request.UpdateFarmBasicRequest;
import com.zhimu.slmp.farm.model.request.UpdateMemberPensRequest;
import com.zhimu.slmp.farm.model.request.UpdateMemberRoleRequest;
import com.zhimu.slmp.farm.model.request.UpdateRegistrationRequest;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.model.vo.FarmBasicVO;
import com.zhimu.slmp.farm.model.vo.FarmChangeVO;
import com.zhimu.slmp.farm.model.vo.FarmDetailVO;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.farm.model.vo.InviteAcceptVO;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.model.vo.MemberChangeResultVO;
import com.zhimu.slmp.farm.model.vo.MemberDetailVO;
import com.zhimu.slmp.farm.model.vo.MemberItemVO;
import com.zhimu.slmp.farm.model.vo.MemberRemoveResultVO;
import com.zhimu.slmp.farm.model.vo.MemberSummaryVO;
import com.zhimu.slmp.farm.model.vo.NameCheckVO;
import com.zhimu.slmp.farm.model.vo.PenOptionVO;
import com.zhimu.slmp.farm.model.vo.RemovalImpactVO;
import com.zhimu.slmp.farm.model.vo.TransferAcceptVO;
import com.zhimu.slmp.farm.model.vo.TransferPageVO;
import com.zhimu.slmp.farm.model.vo.TransferVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 模块02 养殖场与成员管理接口（API-F01–F32，服务端模块02 文档 2.5 节）。
 */
public interface FarmApi {

    // ===== 列表、上下文与切换 =====

    /** API-F01 我的养殖场列表（relation：ALL / CREATED / JOINED） */
    @GET("api/v1/farms")
    Single<ApiResponse<PageVO<FarmListItemVO>>> listFarms(@Query("relation") String relation,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-F02 养殖场上下文 */
    @GET("api/v1/farms/{farmId}/context")
    Single<ApiResponse<FarmContextVO>> getContext(@Path("farmId") long farmId);

    /** API-F03 切换当前养殖场 */
    @POST("api/v1/farms/{farmId}/switch")
    Single<ApiResponse<FarmContextVO>> switchFarm(@Path("farmId") long farmId);

    /** API-F04 名称重名检查 */
    @GET("api/v1/farms/name-check")
    Single<ApiResponse<NameCheckVO>> checkName(@Query("name") String name,
                                               @Nullable @Query("exclude_farm_id") Long excludeFarmId);

    // ===== 新建与注册审核 =====

    /** API-F05 新建养殖场并提交审核 */
    @POST("api/v1/farms")
    Single<ApiResponse<FarmAuditVO>> createFarm(@Body CreateFarmRequest request,
                                                @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-F06 审核结果 */
    @GET("api/v1/farms/{farmId}/audit")
    Single<ApiResponse<FarmAuditVO>> getAudit(@Path("farmId") long farmId);

    /** API-F07 修改注册信息 / 重新提交 */
    @PUT("api/v1/farms/{farmId}/registration")
    Single<ApiResponse<Object>> updateRegistration(@Path("farmId") long farmId,
                                                   @Body UpdateRegistrationRequest request,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-F08 删除未通过审核的养殖场 */
    @DELETE("api/v1/farms/{farmId}")
    Single<ApiResponse<Object>> deleteFarm(@Path("farmId") long farmId);

    // ===== 养殖场信息与资料变更 =====

    /** API-F09 养殖场信息 */
    @GET("api/v1/farms/{farmId}")
    Single<ApiResponse<FarmDetailVO>> getFarmDetail(@Path("farmId") long farmId);

    /** API-F10 修改基本信息 */
    @PATCH("api/v1/farms/{farmId}/basic")
    Single<ApiResponse<FarmBasicVO>> updateBasic(@Path("farmId") long farmId, @Body UpdateFarmBasicRequest request);

    /** API-F11 停用养殖场 */
    @POST("api/v1/farms/{farmId}/disable")
    Single<ApiResponse<FarmDetailVO>> disableFarm(@Path("farmId") long farmId, @Body FarmVersionRequest request);

    /** API-F12 重新启用 */
    @POST("api/v1/farms/{farmId}/enable")
    Single<ApiResponse<FarmDetailVO>> enableFarm(@Path("farmId") long farmId, @Body FarmVersionRequest request);

    /** API-F13 最近一次资料变更（没有记录时 data 为空） */
    @GET("api/v1/farms/{farmId}/changes/latest")
    Single<ApiResponse<FarmChangeVO>> getLatestChange(@Path("farmId") long farmId);

    /** API-F14 提交资料变更 */
    @POST("api/v1/farms/{farmId}/changes")
    Single<ApiResponse<FarmChangeVO>> submitChange(@Path("farmId") long farmId, @Body SubmitFarmChangeRequest request,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    // ===== 成员与邀请 =====

    /** API-F15 成员概况 */
    @GET("api/v1/farms/{farmId}/member-summary")
    Single<ApiResponse<MemberSummaryVO>> getMemberSummary(@Path("farmId") long farmId);

    /** API-F16 成员列表（role：ALL / MANAGER / STAFF） */
    @GET("api/v1/farms/{farmId}/members")
    Single<ApiResponse<PageVO<MemberItemVO>>> listMembers(@Path("farmId") long farmId, @Query("role") String role,
                                                          @Nullable @Query("keyword") String keyword,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-F17 成员详情 */
    @GET("api/v1/farms/{farmId}/members/{memberId}")
    Single<ApiResponse<MemberDetailVO>> getMemberDetail(@Path("farmId") long farmId, @Path("memberId") long memberId);

    /** API-F18 可分配栏舍（不传 member_id 为邀请场景） */
    @GET("api/v1/farms/{farmId}/pen-options")
    Single<ApiResponse<List<PenOptionVO>>> getPenOptions(@Path("farmId") long farmId,
                                                         @Nullable @Query("member_id") Long memberId);

    /** API-F19 邀请列表 */
    @GET("api/v1/farms/{farmId}/invites")
    Single<ApiResponse<PageVO<InviteItemVO>>> listInvites(@Path("farmId") long farmId,
                                                          @Nullable @Query("keyword") String keyword,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-F20 发送邀请 / 重新邀请 */
    @POST("api/v1/farms/{farmId}/invites")
    Single<ApiResponse<InviteItemVO>> sendInvite(@Path("farmId") long farmId, @Body InviteMemberRequest request,
                                                 @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-F21 同意邀请（模块03 T06 经 IFarmInviteService 调用） */
    @POST("api/v1/farm-invites/{inviteId}/accept")
    Single<ApiResponse<InviteAcceptVO>> acceptInvite(@Path("inviteId") long inviteId);

    /** API-F22 拒绝邀请 */
    @POST("api/v1/farm-invites/{inviteId}/reject")
    Single<ApiResponse<Object>> rejectInvite(@Path("inviteId") long inviteId);

    /** API-F23 修改角色 */
    @PUT("api/v1/farms/{farmId}/members/{memberId}/role")
    Single<ApiResponse<MemberChangeResultVO>> updateRole(@Path("farmId") long farmId, @Path("memberId") long memberId,
                                                         @Body UpdateMemberRoleRequest request);

    /** API-F24 调整负责栏舍 */
    @PUT("api/v1/farms/{farmId}/members/{memberId}/pens")
    Single<ApiResponse<MemberChangeResultVO>> updatePens(@Path("farmId") long farmId, @Path("memberId") long memberId,
                                                         @Body UpdateMemberPensRequest request);

    /** API-F25 移出影响 */
    @GET("api/v1/farms/{farmId}/members/{memberId}/removal-impact")
    Single<ApiResponse<RemovalImpactVO>> getRemovalImpact(@Path("farmId") long farmId, @Path("memberId") long memberId);

    /** API-F26 移出成员 */
    @DELETE("api/v1/farms/{farmId}/members/{memberId}")
    Single<ApiResponse<MemberRemoveResultVO>> removeMember(@Path("farmId") long farmId,
                                                           @Path("memberId") long memberId,
                                                           @Query("version") int version);

    /** API-F27 主动退出 */
    @POST("api/v1/farms/{farmId}/quit")
    Single<ApiResponse<Object>> quitFarm(@Path("farmId") long farmId);

    // ===== 转让所有权 =====

    /** API-F28 转让页数据 */
    @GET("api/v1/farms/{farmId}/transfer")
    Single<ApiResponse<TransferPageVO>> getTransferPage(@Path("farmId") long farmId);

    /** API-F29 发起转让 */
    @POST("api/v1/farms/{farmId}/transfers")
    Single<ApiResponse<TransferVO>> createTransfer(@Path("farmId") long farmId, @Body CreateTransferRequest request,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-F30 接受转让（模块03 T07 经 IFarmInviteService 调用） */
    @POST("api/v1/farm-transfers/{transferId}/accept")
    Single<ApiResponse<TransferAcceptVO>> acceptTransfer(@Path("transferId") long transferId);

    /** API-F31 拒绝转让 */
    @POST("api/v1/farm-transfers/{transferId}/reject")
    Single<ApiResponse<Object>> rejectTransfer(@Path("transferId") long transferId);

    /** API-F32 撤回转让 */
    @POST("api/v1/farm-transfers/{transferId}/cancel")
    Single<ApiResponse<TransferVO>> cancelTransfer(@Path("transferId") long transferId);
}
