package com.zhimu.slmp.finance.data;

import com.zhimu.slmp.finance.model.request.ReportExportRequest;
import com.zhimu.slmp.finance.model.vo.ReportExportVO;
import com.zhimu.slmp.network.model.ApiResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * API-E42 经营报表导出：用读超时 45 秒的派生客户端（服务端最多等 30 秒生成文件，默认 30 秒读超时不够），见 FinanceModule。
 */
public interface ReportExportApi {

    long READ_TIMEOUT_SEC = 45;

    /** API-E42 导出 */
    @POST("api/v1/business-reports/export")
    Single<ApiResponse<ReportExportVO>> export(@Body ReportExportRequest body);
}
