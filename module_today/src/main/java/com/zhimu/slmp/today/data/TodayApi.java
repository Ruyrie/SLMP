package com.zhimu.slmp.today.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.today.model.request.MessageOpenRequest;
import com.zhimu.slmp.today.model.request.MessageReadAllRequest;
import com.zhimu.slmp.today.model.request.PushDeviceRequest;
import com.zhimu.slmp.today.model.request.ReminderSettingPatchRequest;
import com.zhimu.slmp.today.model.request.TodoCompleteRequest;
import com.zhimu.slmp.today.model.request.TodoIgnoreRequest;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.MessageDeleteResult;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.MessagePageVO;
import com.zhimu.slmp.today.model.vo.OpenResultVO;
import com.zhimu.slmp.today.model.vo.PushDeviceVO;
import com.zhimu.slmp.today.model.vo.ReadAllResult;
import com.zhimu.slmp.today.model.vo.ReminderSettingVO;
import com.zhimu.slmp.today.model.vo.TodoCalendarResult;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.model.vo.TodoPageVO;
import com.zhimu.slmp.today.model.vo.TodoWeeklyResult;
import com.zhimu.slmp.today.model.vo.UnreadCountVO;

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
 * 模块03 接口（服务端 2.5 节）。API-T01–T06 由请求头 X-Farm-Id 确定养殖场（拦截器自动添加）；
 * API-T08、T09 带幂等键；为 null 的查询参数不发送。
 */
public interface TodayApi {

    /** API-T01 首页聚合 */
    @GET("api/v1/home")
    Single<ApiResponse<HomeVO>> getHome();

    /** API-T02 今天和已逾期 */
    @GET("api/v1/todos/today")
    Single<ApiResponse<TodoPageVO>> getTodayTodos(@Query("filter") String filter,
                                                  @Query("page") int page, @Query("size") int size);

    /** API-T03 日期汇总 */
    @GET("api/v1/todos/calendar")
    Single<ApiResponse<TodoCalendarResult>> getCalendar(@Query("start_date") String startDate,
                                                        @Query("end_date") String endDate,
                                                        @Nullable @Query("scope") String scope,
                                                        @Nullable @Query("category") String category);

    /** API-T04 某天待办列表 */
    @GET("api/v1/todos")
    Single<ApiResponse<PageVO<TodoItemVO>>> getDayTodos(@Query("date") String date,
                                                        @Nullable @Query("scope") String scope,
                                                        @Nullable @Query("category") String category,
                                                        @Nullable @Query("status") String status,
                                                        @Query("page") int page, @Query("size") int size);

    /** API-T05 周视图分组 */
    @GET("api/v1/todos/weekly")
    Single<ApiResponse<TodoWeeklyResult>> getWeekly(@Query("week_start") String weekStart,
                                                    @Nullable @Query("scope") String scope,
                                                    @Nullable @Query("category") String category,
                                                    @Query("per_day") int perDay);

    /** API-T06 已逾期分组 */
    @GET("api/v1/todos/overdue")
    Single<ApiResponse<PageVO<TodoItemVO>>> getOverdue(@Nullable @Query("scope") String scope,
                                                       @Query("page") int page, @Query("size") int size);

    /** API-T07 待办详情 */
    @GET("api/v1/todos/{id}")
    Single<ApiResponse<TodoDetailVO>> getTodoDetail(@Path("id") long todoId);

    /** API-T08 手动标记完成（幂等） */
    @POST("api/v1/todos/{id}/complete")
    Single<ApiResponse<TodoDetailVO>> completeTodo(@Path("id") long todoId,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey,
                                                   @Body TodoCompleteRequest body);

    /** API-T09 忽略（幂等） */
    @POST("api/v1/todos/{id}/ignore")
    Single<ApiResponse<TodoDetailVO>> ignoreTodo(@Path("id") long todoId,
                                                 @Header(Headers.IDEMPOTENCY_KEY) String idemKey,
                                                 @Body TodoIgnoreRequest body);

    /** API-T10 读取提醒设置 */
    @GET("api/v1/reminder-settings")
    Single<ApiResponse<ReminderSettingVO>> getReminderSetting();

    /** API-T11 修改提醒设置（单项） */
    @PATCH("api/v1/reminder-settings")
    Single<ApiResponse<ReminderSettingVO>> patchReminderSetting(@Body ReminderSettingPatchRequest body);

    /** API-T12 消息列表 */
    @GET("api/v1/messages")
    Single<ApiResponse<MessagePageVO>> getMessages(@Nullable @Query("category") String category,
                                                   @Nullable @Query("farm_id") Long farmId,
                                                   @Query("unread_only") boolean unreadOnly,
                                                   @Query("page") int page, @Query("size") int size);

    /** API-T13 未读数 */
    @GET("api/v1/messages/unread-count")
    Single<ApiResponse<UnreadCountVO>> getUnreadCount(@Nullable @Query("category") String category,
                                                      @Nullable @Query("farm_id") Long farmId);

    /** API-T14 全部标为已读 */
    @POST("api/v1/messages/read-all")
    Single<ApiResponse<ReadAllResult>> readAll(@Body MessageReadAllRequest body);

    /** API-T15 打开消息或推送 */
    @POST("api/v1/messages/{id}/open")
    Single<ApiResponse<OpenResultVO>> openMessage(@Path("id") long messageId, @Body MessageOpenRequest body);

    /** API-T16 消息详情（打开即标为已读） */
    @GET("api/v1/messages/{id}")
    Single<ApiResponse<MessageDetailVO>> getMessageDetail(@Path("id") long messageId);

    /** API-T17 删除消息 */
    @DELETE("api/v1/messages/{id}")
    Single<ApiResponse<MessageDeleteResult>> deleteMessage(@Path("id") long messageId);

    /** API-T18 撤销删除（60 秒内） */
    @POST("api/v1/messages/{id}/restore")
    Single<ApiResponse<MessageBriefVO>> restoreMessage(@Path("id") long messageId);

    /** API-T20 登记推送设备 */
    @PUT("api/v1/push-devices")
    Single<ApiResponse<PushDeviceVO>> registerPushDevice(@Body PushDeviceRequest body);

    /**
     * API-T21 解绑设备。退出登录时会话随即清除，令牌由调用方先取出并显式传入（拦截器不覆盖显式声明的 Authorization）。
     */
    @DELETE("api/v1/push-devices/{registration_id}")
    Single<ApiResponse<Object>> unbindPushDevice(@Path("registration_id") String registrationId,
                                                 @Nullable @Header(Headers.AUTHORIZATION) String authorization);
}
