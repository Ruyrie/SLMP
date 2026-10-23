package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.SaleOrderService;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-E14–E23：出栏单与收款。 */
@RestController
@RequestMapping("/api/v1/sale-orders")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class SaleOrderController {

    private final SaleOrderService saleOrderService;

    /** E14 可出栏批次与休药状态 */
    @GetMapping("/batch-options")
    public ApiResponse<List<SaleOrderVos.BatchOptionVO>> batchOptions() {
        return ApiResponse.ok(saleOrderService.batchOptions());
    }

    /** E15 登记预填 */
    @GetMapping("/prefill")
    public ApiResponse<SaleOrderVos.PrefillVO> prefill(
            @RequestParam(value = "batch_id", required = false) Long batchId,
            @RequestParam(value = "sale_date", required = false) String saleDate) {
        return ApiResponse.ok(saleOrderService.prefill(batchId, saleDate));
    }

    /** E16 登记出栏单 */
    @PostMapping
    @Idempotent
    public ApiResponse<SaleOrderVos.CreatedVO> create(
            @RequestBody FinanceRequests.SaleOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(saleOrderService.create(request, idempotencyKey));
    }

    /** E17 出栏单列表 */
    @GetMapping
    public ApiResponse<SaleOrderVos.OrderPageVO> list(FinanceQueries.SaleOrderQuery query) {
        return ApiResponse.ok(saleOrderService.list(query));
    }

    /** E18 待收款 */
    @GetMapping("/receivables")
    public ApiResponse<SaleOrderVos.ReceivableVO> receivables(FinanceQueries.ReceivableQuery query) {
        return ApiResponse.ok(saleOrderService.receivables(query));
    }

    /** E19 出栏单详情 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<SaleOrderVos.DetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(saleOrderService.detail(id));
    }

    /** E20 修改未收款出栏单 */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<SaleOrderVos.DetailVO> update(@PathVariable("id") Long id,
                                                     @RequestBody FinanceRequests.SaleOrderRequest request) {
        return ApiResponse.ok(saleOrderService.update(id, request));
    }

    /** E21 作废未收款出栏单 */
    @PostMapping("/{id:\\d+}/void")
    public ApiResponse<SaleOrderVos.DetailVO> voidOrder(
            @PathVariable("id") Long id, @RequestBody FinanceRequests.SaleOrderVoidRequest request) {
        return ApiResponse.ok(saleOrderService.voidOrder(id, request));
    }

    /** E22 登记收款 */
    @PostMapping("/{id:\\d+}/receipts")
    @Idempotent
    public ApiResponse<SaleOrderVos.DetailVO> addReceipt(
            @PathVariable("id") Long id, @RequestBody FinanceRequests.SaleReceiptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(saleOrderService.addReceipt(id, request, idempotencyKey));
    }

    /** E23 修改申请预计结果 */
    @PostMapping("/{id:\\d+}/change-preview")
    public ApiResponse<SaleOrderVos.ChangePreviewVO> preview(
            @PathVariable("id") Long id, @RequestBody FinanceRequests.SaleChangePreviewRequest request) {
        return ApiResponse.ok(saleOrderService.preview(id, request));
    }
}

