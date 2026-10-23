package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.request.FeedLossRequest;
import com.zhimu.server.biz.stock.service.StockLossService;
import com.zhimu.server.biz.stock.support.StockMessages;
import com.zhimu.server.biz.stock.vo.StockLossVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-M18 登记报损、M19 详情、M31 登记报废（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockLossController {

    private final StockLossService lossService;

    /** M18 登记报损 */
    @PostMapping("/feed-losses")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockLossVO> createFeedLoss(
            @Valid @RequestBody FeedLossRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockLossVO vo = lossService.createFeedLoss(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.feedLoss(vo), vo);
    }

    /** M19 报损 / 报废详情 */
    @GetMapping("/stock-losses/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockLossVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(lossService.get(id));
    }

    /** M31 登记报废 */
    @PostMapping("/drug-scraps")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockLossVO> createDrugScrap(
            @Valid @RequestBody DrugScrapRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockLossVO vo = lossService.createDrugScrap(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.drugScrap(vo), vo);
    }
}
