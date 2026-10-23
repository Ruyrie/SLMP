package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.query.ImmunePlanOptionQuery;
import com.zhimu.server.biz.health.query.ImmunePlanQuery;
import com.zhimu.server.biz.health.request.ImmunePlanCreateRequest;
import com.zhimu.server.biz.health.request.ImmunePlanPostponeRequest;
import com.zhimu.server.biz.health.request.ImmunePlanUpdateRequest;
import com.zhimu.server.biz.health.request.VersionRequest;
import com.zhimu.server.biz.health.service.ImmunePlanService;
import com.zhimu.server.biz.health.vo.ImmunePlanOptionVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOverviewVO;
import com.zhimu.server.biz.health.vo.ImmunePlanVO;
import com.zhimu.server.biz.health.vo.ImmuneRegisterFormVO;
import com.zhimu.server.biz.health.vo.VaccineOptionVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-D01–D13 免疫计划。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class ImmunePlanController {

    private final ImmunePlanService immunePlanService;

    /** API-D01 批次免疫概况 */
    @GetMapping("/batches/{batch_id:\\d+}/immune-plans/overview")
    public ApiResponse<ImmunePlanOverviewVO> overview(@PathVariable("batch_id") Long batchId) {
        return ApiResponse.ok(immunePlanService.overview(batchId));
    }

    /** API-D02 免疫计划列表 */
    @GetMapping("/batches/{batch_id:\\d+}/immune-plans")
    public ApiResponse<PageVO<ImmunePlanVO>> list(@PathVariable("batch_id") Long batchId,
                                                  ImmunePlanQuery query) {
        return ApiResponse.ok(immunePlanService.list(batchId, query));
    }

    /** API-D03 确认入栏前已接种 */
    @PostMapping("/immune-plans/{id:\\d+}/pre-entry-confirm")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanOverviewVO> confirmPreEntry(@PathVariable("id") Long id,
                                                             @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(immunePlanService.preEntryConfirm(id, request == null ? null : request.getVersion()));
    }

    /** API-D04 入栏前项目加入计划 */
    @PostMapping("/immune-plans/{id:\\d+}/pre-entry-join")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanVO> joinPreEntry(@PathVariable("id") Long id,
                                                  @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(immunePlanService.preEntryJoin(id, request == null ? null : request.getVersion()));
    }

    /** API-D05 撤销确认 */
    @PostMapping("/immune-plans/{id:\\d+}/pre-entry-undo")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanOverviewVO> undoPreEntry(@PathVariable("id") Long id,
                                                          @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(immunePlanService.preEntryUndo(id, request == null ? null : request.getVersion()));
    }

    /** API-D06 调整计划日期 */
    @PostMapping("/immune-plans/{id:\\d+}/postpone")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanVO> postpone(@PathVariable("id") Long id,
                                              @RequestBody ImmunePlanPostponeRequest request) {
        return ApiResponse.ok(immunePlanService.postpone(id, request));
    }

    /** API-D07 添加免疫项目 */
    @PostMapping("/batches/{batch_id:\\d+}/immune-plans")
    @Idempotent(required = true)
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanVO> create(@PathVariable("batch_id") Long batchId,
                                            @RequestBody ImmunePlanCreateRequest request) {
        return ApiResponse.ok(immunePlanService.create(batchId, request));
    }

    /** API-D08 修改剂量、方式、截止时刻 */
    @PutMapping("/immune-plans/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ImmunePlanVO> update(@PathVariable("id") Long id,
                                            @RequestBody ImmunePlanUpdateRequest request) {
        return ApiResponse.ok(immunePlanService.update(id, request));
    }

    /** API-D09 删除项目 */
    @DeleteMapping("/immune-plans/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<Void> delete(@PathVariable("id") Long id,
                                    @RequestParam(value = "version", required = false) Integer version) {
        immunePlanService.delete(id, version);
        return ApiResponse.ok();
    }

    /** API-D11 登记接种的计划下拉 */
    @GetMapping("/immune-plans/options")
    public ApiResponse<PageVO<ImmunePlanOptionVO>> options(ImmunePlanOptionQuery query) {
        return ApiResponse.ok(immunePlanService.options(query));
    }

    /** API-D12 登记接种预填 */
    @GetMapping("/immune-plans/{id:\\d+}/register-form")
    public ApiResponse<ImmuneRegisterFormVO> registerForm(@PathVariable("id") Long id,
                                                          @RequestParam(value = "backfill", required = false) Boolean backfill) {
        return ApiResponse.ok(immunePlanService.registerForm(id, backfill));
    }

    /** API-D13 本场疫苗档案下拉 */
    @GetMapping("/vaccines/options")
    public ApiResponse<List<VaccineOptionVO>> vaccines(
            @RequestParam(value = "category_id", required = false) Long categoryId) {
        return ApiResponse.ok(immunePlanService.vaccineOptions(categoryId));
    }
}
