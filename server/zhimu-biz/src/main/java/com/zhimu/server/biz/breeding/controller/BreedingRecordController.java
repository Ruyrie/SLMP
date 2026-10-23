package com.zhimu.server.biz.breeding.controller;

import com.zhimu.server.biz.breeding.request.FarrowingRequest;
import com.zhimu.server.biz.breeding.request.FarrowingUpdateRequest;
import com.zhimu.server.biz.breeding.request.MatingRequest;
import com.zhimu.server.biz.breeding.request.MatingUpdateRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckUpdateRequest;
import com.zhimu.server.biz.breeding.request.WeaningRequest;
import com.zhimu.server.biz.breeding.request.WeaningUpdateRequest;
import com.zhimu.server.biz.breeding.service.BreedingRecordService;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.FarrowingDetailVO;
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

/** API-B12–B21：配种、妊检、产仔、断奶。 */
@RestController
@RequestMapping("/api/v1")
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
@RequiredArgsConstructor
public class BreedingRecordController {

    private final BreedingRecordService recordService;

    /** API-B12 登记配种 */
    @PostMapping("/matings")
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> createMating(@Valid @RequestBody MatingRequest request,
                                                      @RequestHeader(value = "Idempotency-Key", required = false)
                                                      String idempotencyKey) {
        return ApiResponse.ok(recordService.createMating(request, idempotencyKey));
    }

    /** API-B13 修改配种 */
    @PutMapping("/matings/{id:\\d+}")
    public ApiResponse<BreedingRecordVO> updateMating(@PathVariable("id") Long id,
                                                      @Valid @RequestBody MatingUpdateRequest request) {
        return ApiResponse.ok(recordService.updateMating(id, request));
    }

    /** API-B15 登记妊检 */
    @PostMapping("/pregnancy-checks")
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> createCheck(@Valid @RequestBody PregnancyCheckRequest request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey) {
        return ApiResponse.ok(recordService.createCheck(request, idempotencyKey));
    }

    /** API-B16 修改妊检 */
    @PutMapping("/pregnancy-checks/{id:\\d+}")
    public ApiResponse<BreedingRecordVO> updateCheck(@PathVariable("id") Long id,
                                                     @Valid @RequestBody PregnancyCheckUpdateRequest request) {
        return ApiResponse.ok(recordService.updateCheck(id, request));
    }

    /** API-B17 登记产仔 */
    @PostMapping("/farrowings")
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> createFarrowing(@Valid @RequestBody FarrowingRequest request,
                                                         @RequestHeader(value = "Idempotency-Key", required = false)
                                                         String idempotencyKey) {
        return ApiResponse.ok(recordService.createFarrowing(request, idempotencyKey));
    }

    /** API-B19 产仔记录详情 */
    @GetMapping("/farrowings/{id:\\d+}")
    public ApiResponse<FarrowingDetailVO> farrowing(@PathVariable("id") Long id) {
        return ApiResponse.ok(recordService.farrowing(id));
    }

    /** API-B18 修改未转批次的产仔 */
    @PutMapping("/farrowings/{id:\\d+}")
    public ApiResponse<BreedingRecordVO> updateFarrowing(@PathVariable("id") Long id,
                                                         @Valid @RequestBody FarrowingUpdateRequest request) {
        return ApiResponse.ok(recordService.updateFarrowing(id, request));
    }

    /** API-B20 登记断奶 */
    @PostMapping("/weanings")
    @Idempotent(required = true)
    public ApiResponse<RegisterResultVO> createWeaning(@Valid @RequestBody WeaningRequest request,
                                                       @RequestHeader(value = "Idempotency-Key", required = false)
                                                       String idempotencyKey) {
        return ApiResponse.ok(recordService.createWeaning(request, idempotencyKey));
    }

    /** API-B21 修改断奶 */
    @PutMapping("/weanings/{id:\\d+}")
    public ApiResponse<BreedingRecordVO> updateWeaning(@PathVariable("id") Long id,
                                                       @Valid @RequestBody WeaningUpdateRequest request) {
        return ApiResponse.ok(recordService.updateWeaning(id, request));
    }
}
