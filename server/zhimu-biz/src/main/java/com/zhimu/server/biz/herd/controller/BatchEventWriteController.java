package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.request.DeathCreateRequest;
import com.zhimu.server.biz.herd.request.DeathUpdateRequest;
import com.zhimu.server.biz.herd.request.TransferCreateRequest;
import com.zhimu.server.biz.herd.request.TransferUpdateRequest;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.vo.DeathResultVO;
import com.zhimu.server.biz.herd.vo.TransferResultVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-L21–L24 转栏 / 死淘登记与修改。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BatchEventWriteController {

    private final BatchService batchService;

    /** L21 转栏登记 */
    @PostMapping("/batch-transfers")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<TransferResultVO> createTransfer(
            @RequestBody TransferCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(batchService.createTransfer(request, idempotencyKey));
    }

    /** L22 修改转栏记录 */
    @PutMapping("/batch-transfers/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<TransferResultVO> updateTransfer(@PathVariable("id") Long eventId,
                                                        @RequestBody TransferUpdateRequest request) {
        return ApiResponse.ok(batchService.updateTransfer(eventId, request));
    }

    /** L23 死淘登记（全部角色） */
    @PostMapping("/batch-deaths")
    @RequireFarm
    @Idempotent(required = true)
    public ApiResponse<DeathResultVO> createDeath(
            @RequestBody DeathCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(batchService.createDeath(request, idempotencyKey));
    }

    /** L24 修改死淘记录（员工限本人当天） */
    @PutMapping("/batch-deaths/{id:\\d+}")
    @RequireFarm
    public ApiResponse<DeathResultVO> updateDeath(@PathVariable("id") Long eventId,
                                                  @RequestBody DeathUpdateRequest request) {
        return ApiResponse.ok(batchService.updateDeath(eventId, request));
    }
}
