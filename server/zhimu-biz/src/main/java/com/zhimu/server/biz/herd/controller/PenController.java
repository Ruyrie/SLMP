package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.query.PenQuery;
import com.zhimu.server.biz.herd.service.PenService;
import com.zhimu.server.biz.herd.vo.ExecutorOptionVO;
import com.zhimu.server.biz.herd.vo.PenDetailVO;
import com.zhimu.server.biz.herd.vo.PenFormOptionsVO;
import com.zhimu.server.biz.herd.vo.PenOptionVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-L07 栏舍下拉、L08 详情、L10 表单选项、L25 执行人选项。 */
@RestController
@RequestMapping("/api/v1/pens")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class PenController {

    private final PenService penService;

    /** L07 栏舍下拉（usage = OPTIONS，不分页） */
    @GetMapping
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
}
