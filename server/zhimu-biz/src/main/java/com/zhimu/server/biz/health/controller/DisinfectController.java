package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.query.DisinfectPlanQuery;
import com.zhimu.server.biz.health.query.DisinfectTaskQuery;
import com.zhimu.server.biz.health.query.DisinfectantQuery;
import com.zhimu.server.biz.health.request.DisinfectPlanPauseRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanPreviewRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanSaveRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordCreateRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordUpdateRequest;
import com.zhimu.server.biz.health.request.VersionRequest;
import com.zhimu.server.biz.health.service.DisinfectService;
import com.zhimu.server.biz.health.vo.DisinfectPlanDetailVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanPreviewVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanVO;
import com.zhimu.server.biz.health.vo.DisinfectRecordVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskOptionVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskPageVO;
import com.zhimu.server.biz.health.vo.DisinfectantOptionVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-D28–D40 消毒计划、任务与记录。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class DisinfectController {

    private final DisinfectService disinfectService;

    /** API-D28 消毒计划列表 */
    @GetMapping("/disinfect-plans")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<DisinfectPlanVO>> plans(DisinfectPlanQuery query) {
        return ApiResponse.ok(disinfectService.plans(query));
    }

    /** API-D29 计划详情 */
    @GetMapping("/disinfect-plans/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanDetailVO> plan(@PathVariable("id") Long id) {
        return ApiResponse.ok(disinfectService.plan(id));
    }

    /** API-D30 任务数与提醒预估 */
    @PostMapping("/disinfect-plans/preview")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanPreviewVO> preview(@RequestBody DisinfectPlanPreviewRequest request) {
        return ApiResponse.ok(disinfectService.preview(request));
    }

    /** API-D31 新建并启用 */
    @PostMapping("/disinfect-plans")
    @Idempotent(required = true)
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanVO> create(@RequestBody DisinfectPlanSaveRequest request) {
        return ApiResponse.ok(disinfectService.create(request));
    }

    /** API-D32 修改计划 */
    @PutMapping("/disinfect-plans/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanVO> update(@PathVariable("id") Long id,
                                               @RequestBody DisinfectPlanSaveRequest request) {
        return ApiResponse.ok(disinfectService.update(id, request));
    }

    /** API-D33 暂停计划 */
    @PostMapping("/disinfect-plans/{id:\\d+}/pause")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanVO> pause(@PathVariable("id") Long id,
                                              @RequestBody(required = false) DisinfectPlanPauseRequest request) {
        return ApiResponse.ok(disinfectService.pause(id, request));
    }

    /** API-D34 启用计划 */
    @PostMapping("/disinfect-plans/{id:\\d+}/resume")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DisinfectPlanVO> resume(@PathVariable("id") Long id,
                                               @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(disinfectService.resume(id, request == null ? null : request.getVersion()));
    }

    /** API-D35 消毒剂下拉 */
    @GetMapping("/disinfectants/options")
    public ApiResponse<List<DisinfectantOptionVO>> disinfectants(DisinfectantQuery query) {
        return ApiResponse.ok(disinfectService.disinfectants(query));
    }

    /** API-D36 消毒任务列表 */
    @GetMapping("/disinfect-tasks")
    public ApiResponse<DisinfectTaskPageVO> tasks(DisinfectTaskQuery query) {
        return ApiResponse.ok(disinfectService.tasks(query));
    }

    /** API-D37 登记消毒的任务下拉 */
    @GetMapping("/disinfect-tasks/options")
    public ApiResponse<List<DisinfectTaskOptionVO>> taskOptions() {
        return ApiResponse.ok(disinfectService.taskOptions());
    }

    /** API-D38 登记消毒 */
    @PostMapping("/disinfect-records")
    @Idempotent(required = true)
    public ApiResponse<DisinfectRecordVO> createRecord(@RequestBody DisinfectRecordCreateRequest request) {
        return ApiResponse.ok(disinfectService.createRecord(request));
    }

    /** API-D39 消毒记录查看 */
    @GetMapping("/disinfect-records/{id:\\d+}")
    public ApiResponse<DisinfectRecordVO> record(@PathVariable("id") Long id) {
        return ApiResponse.ok(disinfectService.record(id));
    }

    /** API-D40 修改消毒记录 */
    @PutMapping("/disinfect-records/{id:\\d+}")
    public ApiResponse<DisinfectRecordVO> updateRecord(@PathVariable("id") Long id,
                                                       @RequestBody DisinfectRecordUpdateRequest request) {
        return ApiResponse.ok(disinfectService.updateRecord(id, request));
    }
}
