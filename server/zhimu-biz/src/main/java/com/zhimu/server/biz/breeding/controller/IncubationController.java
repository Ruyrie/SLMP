package com.zhimu.server.biz.breeding.controller;

import com.zhimu.server.biz.breeding.request.CandlingRequest;
import com.zhimu.server.biz.breeding.request.HatchRequest;
import com.zhimu.server.biz.breeding.request.IncubationRequest;
import com.zhimu.server.biz.breeding.request.IncubationUpdateRequest;
import com.zhimu.server.biz.breeding.service.IncubationService;
import com.zhimu.server.biz.breeding.vo.IncubationDetailVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-B26、B28–B31：孵化。 */
@RestController
@RequestMapping("/api/v1/incubations")
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
@RequiredArgsConstructor
public class IncubationController {

    private final IncubationService incubationService;

    /** API-B26 登记入孵 */
    @PostMapping
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> create(@Valid @RequestBody IncubationRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false)
                                                String idempotencyKey) {
        return ApiResponse.ok(incubationService.create(request, idempotencyKey));
    }

    /** API-B28 孵化详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<IncubationDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(incubationService.detail(id));
    }

    /** API-B29 修改入孵 / 照蛋 / 未转批次的出雏 */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<IncubationDetailVO> update(@PathVariable("id") Long id,
                                                  @Valid @RequestBody IncubationUpdateRequest request) {
        return ApiResponse.ok(incubationService.update(id, request));
    }

    /** API-B30 登记照蛋 */
    @PostMapping("/{id:\\d+}/candling")
    @Idempotent(required = true)
    public ApiResponse<IncubationDetailVO> candling(@PathVariable("id") Long id,
                                                    @Valid @RequestBody CandlingRequest request,
                                                    @RequestHeader(value = "Idempotency-Key", required = false)
                                                    String idempotencyKey) {
        return ApiResponse.ok(incubationService.candling(id, request, idempotencyKey));
    }

    /** API-B31 登记出雏 */
    @PostMapping("/{id:\\d+}/hatch")
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> hatch(@PathVariable("id") Long id,
                                               @Valid @RequestBody HatchRequest request,
                                               @RequestHeader(value = "Idempotency-Key", required = false)
                                               String idempotencyKey) {
        return ApiResponse.ok(incubationService.hatch(id, request, idempotencyKey));
    }
}
