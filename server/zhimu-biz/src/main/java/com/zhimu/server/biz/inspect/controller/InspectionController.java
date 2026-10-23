package com.zhimu.server.biz.inspect.controller;

import com.zhimu.server.biz.inspect.query.InspectionDayQuery;
import com.zhimu.server.biz.inspect.query.InspectionFormQuery;
import com.zhimu.server.biz.inspect.query.PenHistoryQuery;
import com.zhimu.server.biz.inspect.request.InspectionBackfillRequest;
import com.zhimu.server.biz.inspect.request.InspectionSubmitRequest;
import com.zhimu.server.biz.inspect.service.InspectionService;
import com.zhimu.server.biz.inspect.vo.InspectionDetailVO;
import com.zhimu.server.biz.inspect.vo.InspectionFormVO;
import com.zhimu.server.biz.inspect.vo.InspectionListItemVO;
import com.zhimu.server.biz.inspect.vo.InspectionSubmitResultVO;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionOverviewVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionRowVO;
import com.zhimu.server.biz.inspect.vo.RecentIssueVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
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

/** API-I01–I03、I06–I12、I25：巡检记录、栏舍历史与补录。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    /** API-I01 巡检统计与截止时间 */
    @GetMapping("/inspections/summary")
    public ApiResponse<InspectionSummaryVO> summary(
            @RequestParam(value = "date", required = false) String date) {
        return ApiResponse.ok(inspectionService.summary(date));
    }

    /** API-I02 某日栏舍清单 */
    @GetMapping("/inspections")
    public ApiResponse<PageVO<InspectionListItemVO>> dayList(InspectionDayQuery query) {
        return ApiResponse.ok(inspectionService.dayList(query));
    }

    /** API-I03 前 3 天超时、补录、漏巡 */
    @GetMapping("/inspections/recent-issues")
    public ApiResponse<List<RecentIssueVO>> recentIssues() {
        return ApiResponse.ok(inspectionService.recentIssues());
    }

    /** API-I06 登记 / 修改 / 补录表单上下文 */
    @GetMapping("/inspections/form-context")
    public ApiResponse<InspectionFormVO> formContext(InspectionFormQuery query) {
        return ApiResponse.ok(inspectionService.formContext(query));
    }

    /** API-I07 登记当天巡检 */
    @PostMapping("/inspections")
    @Idempotent(required = true)
    public ApiResponse<InspectionSubmitResultVO> register(@RequestBody InspectionSubmitRequest request) {
        return ApiResponse.ok(inspectionService.register(request));
    }

    /** API-I08 修改巡检 */
    @PutMapping("/inspections/{id:\\d+}")
    public ApiResponse<InspectionSubmitResultVO> modify(@PathVariable("id") Long id,
                                                        @RequestBody InspectionSubmitRequest request) {
        return ApiResponse.ok(inspectionService.modify(id, request));
    }

    /** API-I09 补录漏巡 */
    @PostMapping("/inspections/backfills")
    @Idempotent(required = true)
    public ApiResponse<InspectionSubmitResultVO> backfill(@RequestBody InspectionBackfillRequest request) {
        return ApiResponse.ok(inspectionService.backfill(request));
    }

    /** API-I25 按 client_request_id 查询登记 / 补录是否已保存 */
    @GetMapping("/inspections/by-request/{id}")
    public ApiResponse<InspectionSubmitResultVO> findByRequest(@PathVariable("id") String clientRequestId) {
        return ApiResponse.ok(inspectionService.findByRequest(clientRequestId));
    }

    /** API-I10 巡检详情 */
    @GetMapping("/inspections/{id:\\d+}")
    public ApiResponse<InspectionDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(inspectionService.detail(id));
    }

    /** API-I11 栏舍巡检历史 */
    @GetMapping("/pens/{penId:\\d+}/inspections")
    public ApiResponse<PageVO<PenInspectionRowVO>> penHistory(@PathVariable("penId") Long penId,
                                                              PenHistoryQuery query) {
        return ApiResponse.ok(inspectionService.penHistory(penId, query));
    }

    /** API-I12 栏舍近 7 天概况 */
    @GetMapping("/pens/{penId:\\d+}/inspection-overview")
    public ApiResponse<PenInspectionOverviewVO> penOverview(@PathVariable("penId") Long penId,
                                                            @RequestParam(value = "to", required = false) String to) {
        return ApiResponse.ok(inspectionService.penOverview(penId, to));
    }
}
