package com.zhimu.slmp.ai.data;

import com.zhimu.slmp.ai.model.request.CourseProgressRequest;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionDetailVO;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.CourseCategoryVO;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeDetailVO;
import com.zhimu.slmp.ai.model.vo.LinkableBatchesVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.model.vo.RatingResultVO;
import com.zhimu.slmp.ai.model.vo.SpeechResultVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.ai.model.vo.WatchHistoryVO;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 模块11 非流式接口（API-K01–K05、K08–K10、K12、K14–K21）。K04、K09 的 null 必须输出，请求体由 Repository 手写 JSON。
 */
public interface AiApi {

    /** API-K01 历史会话 */
    @GET("api/v1/ai/sessions")
    Single<ApiResponse<PageVO<AiSessionItemVO>>> sessions(@Query("keyword") String keyword, @Query("page") int page,
                                                          @Query("size") int size);

    /** API-K02 会话详情 */
    @GET("api/v1/ai/sessions/{id}")
    Single<ApiResponse<AiSessionDetailVO>> session(@Path("id") long sessionId);

    /** API-K03 会话问答（按轮次切片） */
    @GET("api/v1/ai/sessions/{id}/messages")
    Single<ApiResponse<AiMessageSliceVO>> messages(@Path("id") long sessionId,
                                                   @Query("before_round_no") Integer beforeRoundNo,
                                                   @Query("after_round_no") Integer afterRoundNo,
                                                   @Query("size") Integer size);

    /** API-K04 更改关联批次（body：{"batch_id": 3001 | null}） */
    @PATCH("api/v1/ai/sessions/{id}")
    Single<ApiResponse<AiSessionDetailVO>> changeBatch(@Path("id") long sessionId, @Body RequestBody body);

    /** 修改会话标题（body：{"title": "…"}，只传 title 时保留关联批次） */
    @PATCH("api/v1/ai/sessions/{id}")
    Single<ApiResponse<AiSessionDetailVO>> updateSession(@Path("id") long sessionId, @Body RequestBody body);

    /** 删除本人会话（逻辑删除，重复删除也返回成功） */
    @DELETE("api/v1/ai/sessions/{id}")
    Single<ApiResponse<Object>> deleteSession(@Path("id") long sessionId);

    /** API-K05 可关联批次 */
    @GET("api/v1/ai/linkable-batches")
    Single<ApiResponse<LinkableBatchesVO>> linkableBatches();

    /** API-K08 单轮问答 */
    @GET("api/v1/ai/messages/{id}")
    Single<ApiResponse<AiMessageVO>> message(@Path("id") long messageId);

    /** API-K09 评价（body：{"rating": "USEFUL" | "USELESS" | null}） */
    @PUT("api/v1/ai/messages/{id}/rating")
    Single<ApiResponse<RatingResultVO>> rate(@Path("id") long messageId, @Body RequestBody body);

    /** API-K10 语音转文字 */
    @Multipart
    @POST("api/v1/ai/speech-recognitions")
    Single<ApiResponse<SpeechResultVO>> recognize(@Part MultipartBody.Part audio,
                                                  @Part("duration_ms") RequestBody durationMs);

    /** API-K12 知识原文 */
    @GET("api/v1/knowledge-items/{id}")
    Single<ApiResponse<KnowledgeDetailVO>> knowledge(@Path("id") long knowledgeId,
                                                     @Query("citation_id") Long citationId);

    /** API-K14 课程分类 */
    @GET("api/v1/course-categories")
    Single<ApiResponse<List<CourseCategoryVO>>> categories();

    /** API-K15 继续观看（data 可为 null） */
    @GET("api/v1/courses/continue")
    Single<ApiResponse<ContinueWatchingVO>> continueWatching();

    /** API-K16 专题列表 */
    @GET("api/v1/course-topics")
    Single<ApiResponse<PageVO<TopicVO>>> topics(@Query("sort") String sort, @Query("page") int page,
                                               @Query("size") int size);

    /** API-K17 专题详情 */
    @GET("api/v1/course-topics/{id}")
    Single<ApiResponse<TopicDetailVO>> topic(@Path("id") long topicId);

    /** API-K18 课程列表 */
    @GET("api/v1/courses")
    Single<ApiResponse<PageVO<CourseCardVO>>> courses(@Query("category_id") Long categoryId,
                                                      @Query("learn_status") String learnStatus,
                                                      @Query("keyword") String keyword, @Query("sort") String sort,
                                                      @Query("page") int page, @Query("size") int size);

    /** API-K19 课程播放 */
    @GET("api/v1/courses/{id}")
    Single<ApiResponse<CoursePlayVO>> coursePlay(@Path("id") long courseId);

    /** API-K20 进度上报 */
    @POST("api/v1/courses/{id}/progress")
    Single<ApiResponse<ProgressResultVO>> reportProgress(@Path("id") long courseId,
                                                         @Body CourseProgressRequest request);

    /** API-K21 观看记录 */
    @GET("api/v1/course-progress")
    Single<ApiResponse<PageVO<WatchHistoryVO>>> history(@Query("page") int page, @Query("size") int size);
}
