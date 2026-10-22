package com.zhimu.slmp.common.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.network.model.ApiResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 公共基础数据接口（04 号文档 15.3 节）。
 */
public interface BaseDataApi {

    /** API-U09 启用的品类与品种树（L05、L08、F02） */
    @GET("api/v1/base/categories")
    Single<ApiResponse<List<CategoryVO>>> getCategories();

    /** API-U10 字典项（L05、L11 等）；species、category_id 可选 */
    @GET("api/v1/base/dicts/{dict_type}")
    Single<ApiResponse<List<DictItemVO>>> getDicts(@Path("dict_type") String dictType,
                                                   @Nullable @Query("species") String species,
                                                   @Nullable @Query("category_id") Long categoryId);

    /** API-U14 某条记录的修改记录（参数 biz_type、biz_id） */
    @GET("api/v1/record-logs")
    Single<ApiResponse<List<RecordLogVO>>> getRecordLogs(@Query("biz_type") String bizType,
                                                         @Query("biz_id") long bizId);
}
