package com.zhimu.slmp.inspect.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.inspect.model.request.AbnormalReportCreateRequest;
import com.zhimu.slmp.inspect.model.request.HandleSaveRequest;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisStateVO;
import com.zhimu.slmp.inspect.model.vo.HandleSaveResultVO;
import com.zhimu.slmp.inspect.model.vo.ReportCreateResultVO;
import com.zhimu.slmp.inspect.model.vo.ReportFormVO;
import com.zhimu.slmp.inspect.model.vo.SymptomVO;
import com.zhimu.slmp.network.RxResult;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 异常上报与诊断数据（模块07 文档 2.3.2）：上报表单、症状、上报、诊断详情与状态、重新诊断、处理结果。
 */
@FeatureScope
public class AbnormalReportRepository {

    public static final String BIZ_DIAGNOSIS_HANDLE = "DIAGNOSIS_HANDLE";
    public static final String BIZ_ABNORMAL_REPORT = "ABNORMAL_REPORT";

    private final InspectApi api;
    private final BaseDataRepository baseData;

    @Inject
    public AbnormalReportRepository(@NonNull InspectApi api, @NonNull BaseDataRepository baseData) {
        this.api = api;
        this.baseData = baseData;
    }

    /** API-I14 */
    @NonNull
    public Single<ReportFormVO> getReportForm(@Nullable Long inspectionId, @Nullable Long penId) {
        return api.getReportForm(inspectionId, penId).compose(RxResult.unwrap());
    }

    /** API-I15 */
    @NonNull
    public Single<List<SymptomVO>> getSymptoms(long batchId) {
        return api.getSymptoms(batchId).compose(RxResult.unwrap());
    }

    /** API-I16（幂等键为 client_request_id） */
    @NonNull
    public Single<ReportCreateResultVO> createReport(@NonNull AbnormalReportCreateRequest request) {
        return api.createReport(request, request.clientRequestId).compose(RxResult.unwrap());
    }

    /** API-I26 */
    @NonNull
    public Single<ReportCreateResultVO> findReportByRequest(@NonNull String clientRequestId) {
        return api.findReportByRequest(clientRequestId).compose(RxResult.unwrap());
    }

    /** API-I19 */
    @NonNull
    public Single<DiagnosisDetailVO> getDiagnosis(long reportId) {
        return api.getDiagnosis(reportId).compose(RxResult.unwrap());
    }

    /** API-I20 */
    @NonNull
    public Single<DiagnosisStateVO> getDiagnosisState(long reportId) {
        return api.getDiagnosisState(reportId).compose(RxResult.unwrap());
    }

    /** API-I21 */
    @NonNull
    public Single<DiagnosisStateVO> rediagnose(long reportId) {
        return api.rediagnose(reportId).compose(RxResult.unwrap());
    }

    /** API-I22 */
    @NonNull
    public Single<HandleSaveResultVO> saveHandle(long reportId, @NonNull HandleSaveRequest request) {
        return api.saveHandle(reportId, request).compose(RxResult.unwrap());
    }

    /** API-U14 处理结果的修改记录（biz_type = DIAGNOSIS_HANDLE） */
    @NonNull
    public Single<List<RecordLogVO>> getRecordLogs(long handleId) {
        return baseData.getRecordLogs(BIZ_DIAGNOSIS_HANDLE, handleId);
    }
}
