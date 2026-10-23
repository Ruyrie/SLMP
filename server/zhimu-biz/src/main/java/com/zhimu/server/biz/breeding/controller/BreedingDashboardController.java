package com.zhimu.server.biz.breeding.controller;

import com.zhimu.server.biz.breeding.query.BreedingDueQuery;
import com.zhimu.server.biz.breeding.service.BreedingDashboardService;
import com.zhimu.server.biz.breeding.vo.BreedingOverviewVO;
import com.zhimu.server.biz.breeding.vo.FarrowDueItemVO;
import com.zhimu.server.biz.breeding.vo.HatchDueItemVO;
import com.zhimu.server.biz.breeding.vo.PendingCheckVO;
import com.zhimu.server.biz.breeding.vo.SubmissionVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-B01、B14、B22、B23、B32：繁育概况与待办列表。 */
@RestController
@RequestMapping("/api/v1/breeding")
@RequiredArgsConstructor
public class BreedingDashboardController {

    private final BreedingDashboardService dashboardService;

    /** API-B01 繁育概况（全部角色，员工裁剪） */
    @GetMapping("/overview")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER, FarmRole.STAFF})
    public ApiResponse<BreedingOverviewVO> overview() {
        return ApiResponse.ok(dashboardService.overview());
    }

    /** API-B14 待妊检 */
    @GetMapping("/pending-checks")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<PendingCheckVO>> pendingChecks(@Valid PageQuery query) {
        return ApiResponse.ok(dashboardService.pendingChecks(query));
    }

    /** API-B22 待产（员工限负责栏舍） */
    @GetMapping("/farrow-due")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER, FarmRole.STAFF})
    public ApiResponse<PageVO<FarrowDueItemVO>> farrowDue(@Valid BreedingDueQuery query) {
        return ApiResponse.ok(dashboardService.farrowDue(query));
    }

    /** API-B23 待出雏（员工限负责栏舍） */
    @GetMapping("/hatch-due")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER, FarmRole.STAFF})
    public ApiResponse<PageVO<HatchDueItemVO>> hatchDue(@Valid BreedingDueQuery query) {
        return ApiResponse.ok(dashboardService.hatchDue(query));
    }

    /** API-B32 提交结果查询（type 为 SUBMISSION_TYPE） */
    @GetMapping("/submissions/{client_request_id}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<SubmissionVO> submission(
            @PathVariable("client_request_id") String clientRequestId,
            @RequestParam("type") String type) {
        return ApiResponse.ok(dashboardService.submission(clientRequestId, type));
    }
}
