package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.query.AdminFarmQuery;
import com.zhimu.server.admin.system.request.FarmFreezeRequest;
import com.zhimu.server.admin.system.service.FarmAdminService;
import com.zhimu.server.admin.system.vo.AdminFarmDetailVO;
import com.zhimu.server.admin.system.vo.AdminFarmItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.admin.system.vo.FreezeResultVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 养殖场管理（ADM-S15–S19）。冻结、解冻、导出需要 WA-S04:HANDLE（超管专属）。 */
@RestController
@RequestMapping("/admin/v1/farms")
@RequiredArgsConstructor
public class FarmAdminController {

    private final FarmAdminService farmAdminService;

    /** ADM-S15 养殖场列表：只返回汇总，不返回账目与凭证。 */
    @AdminPermission("WA-S04:VIEW")
    @GetMapping
    public ApiResponse<PageVO<AdminFarmItemVO>> list(@Valid AdminFarmQuery query) {
        return ApiResponse.ok(farmAdminService.list(query));
    }

    /** ADM-S16 养殖场详情。 */
    @AdminPermission("WA-S04:VIEW")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<AdminFarmDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(farmAdminService.detail(id));
    }

    /** ADM-S17 冻结养殖场。 */
    @AdminPermission("WA-S04:HANDLE")
    @PostMapping("/{id:\\d+}/freeze")
    public ApiResponse<FreezeResultVO> freeze(@PathVariable("id") Long id,
                                               @Valid @RequestBody FarmFreezeRequest request) {
        FreezeResultVO result = farmAdminService.freeze(id, request);
        return new ApiResponse<>(0, "已冻结" + result.getFarmName() + "，原因已发到 "
                + result.getMemberCount() + " 名成员的消息中心", result);
    }

    /** ADM-S18 解冻养殖场。 */
    @AdminPermission("WA-S04:HANDLE")
    @PostMapping("/{id:\\d+}/unfreeze")
    public ApiResponse<Void> unfreeze(@PathVariable("id") Long id,
                                      @Valid @RequestBody FarmFreezeRequest request) {
        String name = farmAdminService.unfreeze(id, request);
        return new ApiResponse<>(0, "已解冻" + name, null);
    }

    /** ADM-S19 导出养殖场：当前筛选，最多 10,000 条。 */
    @AdminPermission("WA-S04:HANDLE")
    @PostMapping("/export")
    public ApiResponse<ExportVO> export(@Valid AdminFarmQuery query) {
        ExportVO result = farmAdminService.export(query);
        return new ApiResponse<>(0, "已导出 " + result.getRowCount() + " 条记录", result);
    }
}
