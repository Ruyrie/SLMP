package com.zhimu.slmp.breed.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.model.request.AnimalExitRequest;
import com.zhimu.slmp.breed.model.request.BreedingAnimalRequest;
import com.zhimu.slmp.breed.model.request.CandlingRequest;
import com.zhimu.slmp.breed.model.request.FarrowingRequest;
import com.zhimu.slmp.breed.model.request.FarrowingUpdateRequest;
import com.zhimu.slmp.breed.model.request.HatchRequest;
import com.zhimu.slmp.breed.model.request.IncubationRequest;
import com.zhimu.slmp.breed.model.request.IncubationUpdateRequest;
import com.zhimu.slmp.breed.model.request.MatingRequest;
import com.zhimu.slmp.breed.model.request.MatingUpdateRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckUpdateRequest;
import com.zhimu.slmp.breed.model.request.WeaningRequest;
import com.zhimu.slmp.breed.model.request.WeaningUpdateRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.FarrowingDetailVO;
import com.zhimu.slmp.breed.model.vo.FlockOptionVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.PendingCheckVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.model.vo.SubmissionVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 模块05 繁育管理接口（服务端模块05 文档 2.5 节，前缀 /api/v1）；Authorization、X-Farm-Id 由拦截器添加。
 * API-B07、B09、B12、B15、B17、B20、B26、B30、B31 带 Idempotency-Key（B12、B15、B17、B20、B26、B31 的键即 API-B32 的
 * client_request_id）。API-L06、L07 为模块04 接口，B07 种禽群与入栏鸡舍、B04 改选入栏栏舍使用。
 */
public interface BreedApi {

    // ===== 查询 =====

    /** API-B01 繁育概况（B01、B02，员工裁剪） */
    @GET("api/v1/breeding/overview")
    Single<ApiResponse<BreedingOverviewVO>> getOverview();

    /** API-B02 种畜列表（B01）：status 为 ANIMAL_FILTER */
    @GET("api/v1/breeding-animals")
    Single<ApiResponse<PageVO<BreedingAnimalItemVO>>> getAnimals(@Query("status") String status,
                                                                @Nullable @Query("keyword") String keyword,
                                                                @Query("page") int page, @Query("size") int size);

    /** API-B04 登记下拉候选（B03、B04、B05）：purpose 为 MATING / FARROWING / WEANING */
    @GET("api/v1/breeding-animals/options")
    Single<ApiResponse<PageVO<BreedingAnimalOptionVO>>> getOptions(@Query("purpose") String purpose,
                                                                  @Nullable @Query("keyword") String keyword,
                                                                  @Query("page") int page, @Query("size") int size);

    /** API-B05 种畜档案（B06、档案表单） */
    @GET("api/v1/breeding-animals/{id}")
    Single<ApiResponse<BreedingAnimalDetailVO>> getAnimal(@Path("id") long animalId);

    /** API-B06 繁育记录（B06） */
    @GET("api/v1/breeding-animals/{id}/records")
    Single<ApiResponse<PageVO<BreedingRecordVO>>> getRecords(@Path("id") long animalId,
                                                            @Query("page") int page, @Query("size") int size);

    /** API-B11 登记表单带入：type 为 REGISTER_TYPE */
    @GET("api/v1/breeding-animals/{id}/register-context")
    Single<ApiResponse<RegisterContextVO>> getRegisterContext(@Path("id") long animalId, @Query("type") String type);

    /** API-B14 待妊检（B03） */
    @GET("api/v1/breeding/pending-checks")
    Single<ApiResponse<PageVO<PendingCheckVO>>> getPendingChecks(@Query("page") int page, @Query("size") int size);

    /** API-B19 产仔记录详情（B04 修改模式） */
    @GET("api/v1/farrowings/{id}")
    Single<ApiResponse<FarrowingDetailVO>> getFarrowing(@Path("id") long farrowingId);

    /** API-B22 待产（B01 待产分段、B02） */
    @GET("api/v1/breeding/farrow-due")
    Single<ApiResponse<PageVO<FarrowDueItemVO>>> getFarrowDue(@Nullable @Query("keyword") String keyword,
                                                             @Query("page") int page, @Query("size") int size);

    /** API-B23 待出雏（B02） */
    @GET("api/v1/breeding/hatch-due")
    Single<ApiResponse<PageVO<HatchDueItemVO>>> getHatchDue(@Nullable @Query("keyword") String keyword,
                                                           @Query("page") int page, @Query("size") int size);

    /** API-B28 孵化详情（B07） */
    @GET("api/v1/incubations/{id}")
    Single<ApiResponse<IncubationDetailVO>> getIncubation(@Path("id") long incubationId);

    /** API-B32 提交结果查询：type 为 SUBMISSION_TYPE */
    @GET("api/v1/breeding/submissions/{client_request_id}")
    Single<ApiResponse<SubmissionVO>> getSubmission(@Path("client_request_id") String clientRequestId,
                                                    @Query("type") String type);

    // ===== 档案 =====

    /** API-B07 新增母猪 */
    @POST("api/v1/breeding-animals")
    Single<ApiResponse<BreedingAnimalDetailVO>> createAnimal(@Body BreedingAnimalRequest req,
                                                             @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B08 编辑档案 */
    @PUT("api/v1/breeding-animals/{id}")
    Single<ApiResponse<BreedingAnimalDetailVO>> updateAnimal(@Path("id") long animalId,
                                                             @Body BreedingAnimalRequest req);

    /** API-B09 登记死亡 / 淘汰 */
    @POST("api/v1/breeding-animals/{id}/exit")
    Single<ApiResponse<BreedingAnimalDetailVO>> exitAnimal(@Path("id") long animalId, @Body AnimalExitRequest req,
                                                           @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    // ===== 登记与修改 =====

    /** API-B12 登记配种 */
    @POST("api/v1/matings")
    Single<ApiResponse<RegisterResultVO>> createMating(@Body MatingRequest req,
                                                       @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B13 修改配种 */
    @PUT("api/v1/matings/{id}")
    Single<ApiResponse<BreedingRecordVO>> updateMating(@Path("id") long recordId, @Body MatingUpdateRequest req);

    /** API-B15 登记妊检 */
    @POST("api/v1/pregnancy-checks")
    Single<ApiResponse<RegisterResultVO>> createPregCheck(@Body PregnancyCheckRequest req,
                                                          @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B16 修改妊检 */
    @PUT("api/v1/pregnancy-checks/{id}")
    Single<ApiResponse<BreedingRecordVO>> updatePregCheck(@Path("id") long recordId,
                                                          @Body PregnancyCheckUpdateRequest req);

    /** API-B17 登记产仔 */
    @POST("api/v1/farrowings")
    Single<ApiResponse<RegisterResultVO>> createFarrowing(@Body FarrowingRequest req,
                                                          @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B18 修改未转批次的产仔 */
    @PUT("api/v1/farrowings/{id}")
    Single<ApiResponse<BreedingRecordVO>> updateFarrowing(@Path("id") long recordId, @Body FarrowingUpdateRequest req);

    /** API-B20 登记断奶 */
    @POST("api/v1/weanings")
    Single<ApiResponse<RegisterResultVO>> createWeaning(@Body WeaningRequest req,
                                                        @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B21 修改断奶 */
    @PUT("api/v1/weanings/{id}")
    Single<ApiResponse<BreedingRecordVO>> updateWeaning(@Path("id") long recordId, @Body WeaningUpdateRequest req);

    // ===== 孵化 =====

    /** API-B26 登记入孵 */
    @POST("api/v1/incubations")
    Single<ApiResponse<RegisterResultVO>> createIncubation(@Body IncubationRequest req,
                                                           @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B29 修改入孵 / 照蛋 / 未转批次的出雏 */
    @PUT("api/v1/incubations/{id}")
    Single<ApiResponse<IncubationDetailVO>> updateIncubation(@Path("id") long incubationId,
                                                             @Body IncubationUpdateRequest req);

    /** API-B30 登记照蛋 */
    @POST("api/v1/incubations/{id}/candling")
    Single<ApiResponse<IncubationDetailVO>> candle(@Path("id") long incubationId, @Body CandlingRequest req,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-B31 登记出雏 */
    @POST("api/v1/incubations/{id}/hatch")
    Single<ApiResponse<RegisterResultVO>> hatch(@Path("id") long incubationId, @Body HatchRequest req,
                                                @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    // ===== 模块04 接口 =====

    /** API-L06 种禽群下拉：purpose = BREEDING、species = POULTRY、status = ACTIVE */
    @GET("api/v1/batches")
    Single<ApiResponse<PageVO<FlockOptionVO>>> getFlocks(@Query("purpose") String purpose,
                                                        @Query("species") String species,
                                                        @Query("status") String status,
                                                        @Query("page") int page, @Query("size") int size);

    /** API-L07 栏舍下拉（usage = OPTIONS，不分页）；category_id 标出品类冲突的栏舍 */
    @GET("api/v1/pens")
    Single<ApiResponse<List<PenOptionVO>>> getPenOptions(@Query("usage") String usage,
                                                        @Nullable @Query("category_id") Long categoryId);
}
