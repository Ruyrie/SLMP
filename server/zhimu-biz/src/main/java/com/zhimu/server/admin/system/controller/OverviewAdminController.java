package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.query.FarmMapQuery;
import com.zhimu.server.admin.system.query.TrendQuery;
import com.zhimu.server.admin.system.service.OverviewService;
import com.zhimu.server.admin.system.vo.FarmMapVO;
import com.zhimu.server.admin.system.vo.OverviewVO;
import com.zhimu.server.admin.system.vo.RegionDetailVO;
import com.zhimu.server.admin.system.vo.TrendVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台概览（ADM-S06–S09）。 */
@RestController
@RequestMapping("/admin/v1/overview")
@RequiredArgsConstructor
public class OverviewAdminController {

    private final OverviewService overviewService;

    /** ADM-S06 平台概览；各块独立容错。 */
    @AdminPermission("WA-S02:VIEW")
    @GetMapping
    public ApiResponse<OverviewVO> overview() {
        return ApiResponse.ok(overviewService.overview());
    }

    /** ADM-S07 养殖场分布聚合。 */
    @AdminPermission("WA-S02:VIEW")
    @GetMapping("/farm-map")
    public ApiResponse<FarmMapVO> farmMap(@Valid FarmMapQuery query) {
        return ApiResponse.ok(overviewService.farmMap(query));
    }

    /** ADM-S08 地区明细浮层。 */
    @AdminPermission("WA-S02:VIEW")
    @GetMapping("/regions/{regionCode}")
    public ApiResponse<RegionDetailVO> regionDetail(@PathVariable("regionCode") String regionCode) {
        return ApiResponse.ok(overviewService.regionDetail(regionCode));
    }

    /** ADM-S09 近 30 天趋势。 */
    @AdminPermission("WA-S02:VIEW")
    @GetMapping("/trend")
    public ApiResponse<TrendVO> trend(@Valid TrendQuery query) {
        return ApiResponse.ok(overviewService.trend(query));
    }
}
