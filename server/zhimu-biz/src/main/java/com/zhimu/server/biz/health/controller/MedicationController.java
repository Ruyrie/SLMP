package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.query.MedicationDrugOptionQuery;
import com.zhimu.server.biz.health.query.MedicationTargetQuery;
import com.zhimu.server.biz.health.request.MedicationCreateRequest;
import com.zhimu.server.biz.health.request.MedicationPreviewRequest;
import com.zhimu.server.biz.health.request.MedicationUpdateRequest;
import com.zhimu.server.biz.health.request.NotifyManagerRequest;
import com.zhimu.server.biz.health.service.MedicationService;
import com.zhimu.server.biz.health.vo.DeleteImpactVO;
import com.zhimu.server.biz.health.vo.MedicationCreateResultVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionsVO;
import com.zhimu.server.biz.health.vo.MedicationRecordVO;
import com.zhimu.server.biz.health.vo.MedicationTargetVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.biz.health.vo.ReleaseDatePreviewVO;
import com.zhimu.server.biz.health.vo.ToastVO;
import com.zhimu.server.biz.health.vo.WithdrawalVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageQuery;
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

/** API-D17–D27 用药与休药。 */
@RestController
@RequestMapping("/api/v1")
@RequireFarm
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    /** API-D17 休药状态与按药品计算 */
    @GetMapping("/batches/{batch_id:\\d+}/withdrawal")
    public ApiResponse<WithdrawalVO> withdrawal(@PathVariable("batch_id") Long batchId) {
        return ApiResponse.ok(medicationService.withdrawal(batchId));
    }

    /** API-D18 用药记录列表 */
    @GetMapping("/batches/{batch_id:\\d+}/medication-records")
    public ApiResponse<PageVO<MedicationRecordVO>> records(@PathVariable("batch_id") Long batchId,
                                                           PageQuery query) {
        return ApiResponse.ok(medicationService.records(batchId, query));
    }

    /** API-D19 用药对象下拉 */
    @GetMapping("/medication/targets")
    public ApiResponse<List<MedicationTargetVO>> targets(MedicationTargetQuery query) {
        return ApiResponse.ok(medicationService.targets(query));
    }

    /** API-D20 可选兽药 */
    @GetMapping("/medication/drug-options")
    public ApiResponse<MedicationDrugOptionsVO> drugOptions(MedicationDrugOptionQuery query) {
        return ApiResponse.ok(medicationService.drugOptions(query));
    }

    /** API-D21 预估可出栏日期与预计用量 */
    @PostMapping("/medication-records/preview")
    public ApiResponse<ReleaseDatePreviewVO> preview(@RequestBody MedicationPreviewRequest request) {
        return ApiResponse.ok(medicationService.preview(request));
    }

    /** API-D22 登记用药 */
    @PostMapping("/medication-records")
    @Idempotent(required = true)
    public ApiResponse<MedicationCreateResultVO> create(@RequestBody MedicationCreateRequest request) {
        return ApiResponse.ok(medicationService.create(request));
    }

    /** API-D23 用药记录详情 */
    @GetMapping("/medication-records/{id:\\d+}")
    public ApiResponse<MedicationRecordVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(medicationService.detail(id));
    }

    /** API-D24 修改用药记录 */
    @PutMapping("/medication-records/{id:\\d+}")
    public ApiResponse<MedicationRecordVO> update(@PathVariable("id") Long id,
                                                  @RequestBody MedicationUpdateRequest request) {
        return ApiResponse.ok(medicationService.update(id, request));
    }

    /** API-D25 删除影响预判 */
    @GetMapping("/medication-records/{id:\\d+}/delete-impact")
    public ApiResponse<DeleteImpactVO> deleteImpact(@PathVariable("id") Long id) {
        return ApiResponse.ok(medicationService.deleteImpact(id));
    }

    /** API-D26 直接删除 */
    @DeleteMapping("/medication-records/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<ToastVO> delete(@PathVariable("id") Long id,
                                       @RequestParam(value = "version", required = false) Integer version) {
        return ApiResponse.ok(medicationService.delete(id, version));
    }

    /** API-D27 员工通知场长 */
    @PostMapping("/medication-records/{id:\\d+}/notify-manager")
    @FarmRoles({FarmRole.STAFF})
    public ApiResponse<NotifyResultVO> notifyManager(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) NotifyManagerRequest request) {
        return ApiResponse.ok(medicationService.notifyManager(id, request));
    }
}
