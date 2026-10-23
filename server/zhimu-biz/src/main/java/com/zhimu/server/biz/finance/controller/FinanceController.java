package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.FinanceCategoryService;
import com.zhimu.server.biz.finance.service.FinanceRecordService;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API-E01–E13、E43：经营概览、收支流水、分类与子类、按请求编号查询。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class FinanceController {

    private final FinanceRecordService recordService;
    private final FinanceCategoryService categoryService;

    /** E01 经营概览 */
    @GetMapping("/business-overview")
    public ApiResponse<OverviewVos.BusinessOverviewVO> overview() {
        return ApiResponse.ok(recordService.overview());
    }

    /** E02 月度汇总 */
    @GetMapping("/finance-records/monthly-summary")
    public ApiResponse<OverviewVos.FinanceSummaryVO> monthlySummary(
            @RequestParam("month") String month,
            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(recordService.monthlySummary(month, scope));
    }

    /** E03 收支流水 */
    @GetMapping("/finance-records")
    public ApiResponse<FinanceRecordVos.RecordPageVO> list(FinanceQueries.FinanceRecordQuery query) {
        return ApiResponse.ok(recordService.listRecords(query));
    }

    /** E04 收支详情 */
    @GetMapping("/finance-records/{id:\\d+}")
    public ApiResponse<FinanceRecordVos.DetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(recordService.detail(id));
    }

    /** E05 记一笔 */
    @PostMapping("/finance-records")
    @Idempotent
    public ApiResponse<FinanceRecordVos.RecordVO> create(
            @RequestBody FinanceRequests.FinanceRecordRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(recordService.create(request, idempotencyKey));
    }

    /** E06 修改手工记录 */
    @PutMapping("/finance-records/{id:\\d+}")
    public ApiResponse<FinanceRecordVos.RecordVO> update(@PathVariable("id") Long id,
                                                         @RequestBody FinanceRequests.FinanceRecordRequest request) {
        return ApiResponse.ok(recordService.update(id, request));
    }

    /** E07 作废手工记录 */
    @PostMapping("/finance-records/{id:\\d+}/void")
    public ApiResponse<FinanceRecordVos.RecordVO> voidRecord(@PathVariable("id") Long id,
                                                             @RequestBody FinanceRequests.VersionRequest request) {
        return ApiResponse.ok(recordService.voidRecord(id, request == null ? null : request.getVersion()));
    }

    /** E09 疑似重复检查 */
    @GetMapping("/finance-records/duplicate-check")
    public ApiResponse<FinanceRecordVos.DuplicateCheckVO> duplicateCheck(
            @RequestParam("record_type") String recordType,
            @RequestParam("category_id") Long categoryId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("record_date") String recordDate,
            @RequestParam(value = "exclude_id", required = false) Long excludeId) {
        return ApiResponse.ok(categoryService.duplicateCheck(recordType, categoryId, amount,
                LocalDate.parse(recordDate), excludeId));
    }

    /** E10 一级分类与本场子类 */
    @GetMapping("/finance-categories")
    public ApiResponse<List<FinanceRecordVos.CategoryVO>> categories(
            @RequestParam(value = "direction", required = false) String direction) {
        return ApiResponse.ok(categoryService.list(direction));
    }

    /** E11 添加子类 */
    @PostMapping("/expense-subcategories")
    @Idempotent
    public ApiResponse<FinanceRecordVos.SubcategoryVO> addSubcategory(
            @RequestBody FinanceRequests.SubcategoryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(categoryService.add(request, idempotencyKey));
    }

    /** E12 子类改名 */
    @PutMapping("/expense-subcategories/{id:\\d+}")
    public ApiResponse<FinanceRecordVos.SubcategoryVO> renameSubcategory(
            @PathVariable("id") Long id, @RequestBody FinanceRequests.SubcategoryRequest request) {
        return ApiResponse.ok(categoryService.rename(id, request));
    }

    /** E13 删除子类 */
    @DeleteMapping("/expense-subcategories/{id:\\d+}")
    public ApiResponse<Void> deleteSubcategory(@PathVariable("id") Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }

    /** E43 按请求编号查询 */
    @GetMapping("/business-submissions/{client_request_id}")
    public ApiResponse<FinanceRecordVos.SubmissionVO> submission(
            @PathVariable("client_request_id") String clientRequestId) {
        return ApiResponse.ok(recordService.submission(clientRequestId));
    }
}
