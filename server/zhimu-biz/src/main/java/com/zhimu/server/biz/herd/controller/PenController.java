package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.request.PenCreateRequest;
import com.zhimu.server.biz.herd.request.PenStatusRequest;
import com.zhimu.server.biz.herd.request.PenUpdateRequest;
import com.zhimu.server.biz.herd.service.PenService;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模块04 栏舍接口（API-L07、L08、L10–L13、L25、L27）。 */
@RestController
@RequestMapping("/api/v1/pens")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class PenController {

    private final PenService penService;

    /** L07 栏舍下拉（usage = OPTIONS，不分页）；员工可调用，只返回自己负责的栏舍 */
    @GetMapping
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER, FarmRole.STAFF})
    public ApiResponse<List<PenOptionVO>> options(PenQuery query) {
        return ApiResponse.ok(penService.options(query));
    }

    /** L10 栏舍表单选项 */
    @GetMapping("/form-options")
    public ApiResponse<PenFormOptionsVO> formOptions() {
        return ApiResponse.ok(penService.formOptions());
    }

    /** L08 栏舍详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<PenDetailVO> detail(@PathVariable("id") Long penId) {
        return ApiResponse.ok(penService.detail(penId));
    }

    /** L25 实际执行人选项 */
    @GetMapping("/{id:\\d+}/executors")
    public ApiResponse<List<ExecutorOptionVO>> executors(@PathVariable("id") Long penId) {
        return ApiResponse.ok(penService.executors(penId));
    }

    /** L11 新增栏舍 */
    @PostMapping
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<PenDetailVO> create(@RequestBody PenCreateRequest request) {
        return ApiResponse.ok(penService.create(request));
    }

    /** L12 编辑栏舍 */
    @PutMapping("/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PenDetailVO> update(@PathVariable("id") Long penId,
                                           @RequestBody PenUpdateRequest request) {
        return ApiResponse.ok(penService.update(penId, request));
    }

    /** L13 停用栏舍 */
    @PostMapping("/{id:\\d+}/disable")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PenDetailVO> disable(@PathVariable("id") Long penId,
                                            @RequestBody PenStatusRequest request) {
        return ApiResponse.ok(penService.disable(penId, request));
    }

    /** L27 启用栏舍 */
    @PostMapping("/{id:\\d+}/enable")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PenDetailVO> enable(@PathVariable("id") Long penId,
                                           @RequestBody PenStatusRequest request) {
        return ApiResponse.ok(penService.enable(penId, request));
    }
}
