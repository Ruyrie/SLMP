package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BusinessReportService;
import com.zhimu.server.biz.finance.vo.ReportVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-E37–E42：经营报表期次、指标、趋势、AI 摘要与导出。 */
@RestController
@RequestMapping("/api/v1/business-reports")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class BusinessReportController {

    private final BusinessReportService reportService;

    /** E37 已生成期次 */
    @GetMapping("/periods")
    public ApiResponse<ReportVos.ReportPeriodVO> periods(
            @RequestParam("report_type") String reportType,
            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(reportService.periods(reportType, scope));
    }

    /** E38 报表指标与对比 */
    @GetMapping("/view")
    public ApiResponse<ReportVos.BusinessReportVO> view(FinanceQueries.ReportQuery query) {
        return ApiResponse.ok(reportService.view(query));
    }

    /** E39 指标逐日趋势 */
    @GetMapping("/trend")
    public ApiResponse<ReportVos.ReportTrendVO> trend(FinanceQueries.ReportQuery query) {
        return ApiResponse.ok(reportService.trend(query));
    }

    /** E40 读取 AI 摘要 */
    @GetMapping("/ai-summary")
    public ApiResponse<ReportVos.ReportSummaryVO> summary(FinanceQueries.ReportQuery query) {
        return ApiResponse.ok(reportService.summary(query));
    }

    /** E41 重新生成 AI 摘要 */
    @PostMapping("/ai-summary/regenerate")
    public ApiResponse<ReportVos.ReportSummaryVO> regenerate(
            @RequestBody FinanceRequests.ReportQuery request) {
        return ApiResponse.ok(reportService.regenerate(request));
    }

    /** E42 导出 */
    @PostMapping("/export")
    public ApiResponse<ReportVos.ReportExportVO> export(
            @RequestBody FinanceRequests.ReportExportRequest request) {
        return ApiResponse.ok(reportService.export(request));
    }
}
