package com.zhimu.server.biz.inspect.controller;

import com.zhimu.server.biz.inspect.query.AbnormalReportListQuery;
import com.zhimu.server.biz.inspect.query.LinkableQuery;
import com.zhimu.server.biz.inspect.query.ReportFormQuery;
import com.zhimu.server.biz.inspect.request.AbnormalReportCreateRequest;
import com.zhimu.server.biz.inspect.request.HandleSaveRequest;
import com.zhimu.server.biz.inspect.service.AbnormalReportService;
import com.zhimu.server.biz.inspect.vo.AbnormalReportListItemVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisDetailVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleSaveResultVO;
import com.zhimu.server.biz.inspect.vo.LinkableReportVO;
import com.zhimu.server.biz.inspect.vo.ReportCreateResultVO;
import com.zhimu.server.biz.inspect.vo.ReportFormVO;
import com.zhimu.server.biz.inspect.vo.SymptomVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-I14–I16、I19–I22、I24、I26：异常上报、AI 诊断与处理。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class AbnormalReportController {

    private final AbnormalReportService abnormalReportService;

    /** API-I14 上报表单上下文 */
    @GetMapping("/abnormal-reports/form-context")
    public ApiResponse<ReportFormVO> formContext(ReportFormQuery query) {
        return ApiResponse.ok(abnormalReportService.formContext(query));
    }

    /** API-I15 按批次品类取症状 */
    @GetMapping("/symptoms")
    public ApiResponse<List<SymptomVO>> symptoms(@RequestParam("batch_id") Long batchId) {
        return ApiResponse.ok(abnormalReportService.symptoms(batchId));
    }

    /** API-I16 异常上报（之后异步 AI 诊断） */
    @PostMapping("/abnormal-reports")
    @Idempotent(required = true)
    public ApiResponse<ReportCreateResultVO> create(@RequestBody AbnormalReportCreateRequest request) {
        return ApiResponse.ok(abnormalReportService.create(request));
    }

    /** API-I26 按 client_request_id 查询上报是否已保存 */
    @GetMapping("/abnormal-reports/by-request/{id}")
    public ApiResponse<ReportCreateResultVO> findByRequest(@PathVariable("id") String clientRequestId) {
        return ApiResponse.ok(abnormalReportService.findByRequest(clientRequestId));
    }

    /** API-I24 可关联的上报（模块08 D04） */
    @GetMapping("/abnormal-reports/linkable")
    public ApiResponse<List<LinkableReportVO>> linkable(LinkableQuery query) {
        return ApiResponse.ok(abnormalReportService.linkable(query));
    }

    /** API-I27 异常上报列表（真实服务端分页 + 状态角标） */
    @GetMapping("/abnormal-reports")
    public ApiResponse<PageVO<AbnormalReportListItemVO>> list(AbnormalReportListQuery query) {
        return ApiResponse.ok(abnormalReportService.list(query));
    }

    /** API-I19 诊断详情 */
    @GetMapping("/abnormal-reports/{id:\\d+}")
    public ApiResponse<DiagnosisDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(abnormalReportService.detail(id));
    }

    /** API-I20 诊断状态轮询 */
    @GetMapping("/abnormal-reports/{id:\\d+}/diagnosis-state")
    public ApiResponse<DiagnosisStateVO> diagnosisState(@PathVariable("id") Long id) {
        return ApiResponse.ok(abnormalReportService.state(id));
    }

    /** API-I21 手动重新诊断（无请求体、无幂等键） */
    @PostMapping("/abnormal-reports/{id:\\d+}/rediagnoses")
    public ApiResponse<DiagnosisStateVO> rediagnose(@PathVariable("id") Long id) {
        return ApiResponse.ok(abnormalReportService.rediagnose(id));
    }

    /** API-I22 登记 / 修改处理结果 */
    @PutMapping("/abnormal-reports/{id:\\d+}/handle")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<HandleSaveResultVO> saveHandle(@PathVariable("id") Long id,
                                                      @RequestBody HandleSaveRequest request) {
        return ApiResponse.ok(abnormalReportService.saveHandle(id, request));
    }
}
