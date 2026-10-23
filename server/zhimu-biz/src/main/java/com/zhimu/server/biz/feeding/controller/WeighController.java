package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.feeding.request.WeighCycleRequest;
import com.zhimu.server.biz.feeding.request.WeighRecordCreateRequest;
import com.zhimu.server.biz.feeding.request.WeighRecordUpdateRequest;
import com.zhimu.server.biz.feeding.service.WeighService;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-G18–G26：称重批次选择、称重表单、提交/详情/修改、周期、曲线、列表、对比选项。 */
@RestController
@RequireFarm
@RequiredArgsConstructor
public class WeighController {

    private final WeighService weighService;

    /** G18 称重批次选择 */
    @GetMapping("/api/v1/weigh-records/batch-options")
    public ApiResponse<List<WeighVO.BatchOption>> batchOptions(
            @RequestParam(value = "include_ended", defaultValue = "false") boolean includeEnded) {
        return ApiResponse.ok(weighService.batchOptions(includeEnded));
    }

    /** G19 称重表单上下文 */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/weigh-form")
    public ApiResponse<WeighVO.Form> form(@PathVariable("batchId") Long batchId,
                                          @RequestParam(value = "date", required = false) String date) {
        return ApiResponse.ok(weighService.form(batchId, date));
    }

    /** G20 提交称重（要求 Idempotency-Key） */
    @PostMapping("/api/v1/batches/{batchId:\\d+}/weigh-records")
    @Idempotent(required = true)
    public ApiResponse<WeighVO.Record> create(@PathVariable("batchId") Long batchId,
                                              @RequestBody WeighRecordCreateRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false)
                                              String idemKey) {
        WeighVO.Record record = weighService.create(batchId, request, idemKey);
        String message = switch (record.getGrowthChange() == null ? "" : record.getGrowthChange()) {
            case "SET_SLOW" -> "已登记称重，批次已标为生长偏慢";
            case "SET_FAST" -> "已登记称重，批次已标为生长偏快";
            case "CLEARED" -> "已登记称重，生长偏慢标记已取消";
            default -> "已登记称重，待办已自动完成";
        };
        return new ApiResponse<>(0, message, record);
    }

    /** G21 称重记录详情 */
    @GetMapping("/api/v1/weigh-records/{id:\\d+}")
    public ApiResponse<WeighVO.Record> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(weighService.detail(id));
    }

    /** G22 修改称重记录 */
    @PutMapping("/api/v1/weigh-records/{id:\\d+}")
    public ApiResponse<WeighVO.Record> update(@PathVariable("id") Long id,
                                              @RequestBody WeighRecordUpdateRequest request) {
        return new ApiResponse<>(0, "已修改称重记录，生长曲线已重新计算",
                weighService.update(id, request));
    }

    /** G23 修改 / 恢复称重周期 */
    @PutMapping("/api/v1/batches/{batchId:\\d+}/weigh-cycle")
    public ApiResponse<WeighVO.Cycle> cycle(@PathVariable("batchId") Long batchId,
                                            @RequestBody WeighCycleRequest request) {
        return new ApiResponse<>(0, "已修改称重周期，下次称重日期已重新推算",
                weighService.updateCycle(batchId, request));
    }

    /** G24 生长曲线 */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/growth-curve")
    public ApiResponse<WeighVO.Curve> curve(
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "compare_batch_ids", required = false) String compareBatchIds) {
        return ApiResponse.ok(weighService.curve(batchId, compareBatchIds));
    }

    /** G25 称重记录列表 */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/weigh-records")
    public ApiResponse<PageVO<WeighVO.RecordItem>> records(
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(weighService.records(batchId, page, size));
    }

    /** G26 对比批次选项 */
    @GetMapping("/api/v1/batches/{batchId:\\d+}/growth-compare-options")
    public ApiResponse<List<WeighVO.CompareOption>> compareOptions(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(weighService.compareOptions(batchId));
    }
}
