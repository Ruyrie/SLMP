package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.request.ImmuneRecordCreateRequest;
import com.zhimu.server.biz.health.request.ImmuneRecordUpdateRequest;
import com.zhimu.server.biz.health.request.NotifyManagerRequest;
import com.zhimu.server.biz.health.service.ImmuneRecordService;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.common.api.ApiResponse;
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

/** API-D14–D16 接种记录。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class ImmuneRecordController {

    private final ImmuneRecordService immuneRecordService;

    /** API-D14 登记接种 / 补免 */
    @PostMapping("/immune-records")
    @Idempotent(required = true)
    public ApiResponse<ImmuneRecordVO> create(@RequestBody ImmuneRecordCreateRequest request) {
        return ApiResponse.ok(immuneRecordService.create(request));
    }

    /** API-D15 接种记录查看 */
    @GetMapping("/immune-records/{id:\\d+}")
    public ApiResponse<ImmuneRecordVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(immuneRecordService.detail(id));
    }

    /** API-D16 修改接种记录 */
    @PutMapping("/immune-records/{id:\\d+}")
    public ApiResponse<ImmuneRecordVO> update(@PathVariable("id") Long id,
                                              @RequestBody ImmuneRecordUpdateRequest request) {
        return ApiResponse.ok(immuneRecordService.update(id, request));
    }

    /** 员工通知场长处理免疫记录（请求 / 响应与 API-D27 用药记录一致） */
    @PostMapping("/immune-records/{id:\\d+}/notify-manager")
    @FarmRoles({FarmRole.STAFF})
    public ApiResponse<NotifyResultVO> notifyManager(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) NotifyManagerRequest request) {
        return ApiResponse.ok(immuneRecordService.notifyManager(id, request));
    }
}
