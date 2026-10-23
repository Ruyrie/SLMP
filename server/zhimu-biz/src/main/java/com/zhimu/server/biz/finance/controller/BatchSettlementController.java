package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BatchSettlementService;
import com.zhimu.server.biz.finance.vo.SettlementVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-E26–E28：批次核算。 */
@RestController
@RequestMapping("/api/v1/batch-settlements")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class BatchSettlementController {

    private final BatchSettlementService settlementService;

    /** E26 批次选择与待确认补录数 */
    @GetMapping
    public ApiResponse<SettlementVos.OptionPageVO> options(FinanceQueries.SettlementQuery query) {
        return ApiResponse.ok(settlementService.options(query));
    }

    /** E27 批次核算 */
    @GetMapping("/{batchId:\\d+}")
    public ApiResponse<SettlementVos.BatchSettlementVO> detail(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(settlementService.detail(batchId));
    }

    /** E28 确认补录并重算 */
    @PostMapping("/{batchId:\\d+}/confirm-supplements")
    public ApiResponse<SettlementVos.BatchSettlementVO> confirm(
            @PathVariable("batchId") Long batchId,
            @RequestBody FinanceRequests.SupplementConfirmRequest request) {
        return ApiResponse.ok(settlementService.confirm(batchId, request));
    }
}
