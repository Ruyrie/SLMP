package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.DrugInboundRequest;
import com.zhimu.server.biz.stock.request.FeedInboundRequest;
import com.zhimu.server.biz.stock.request.InboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.service.StockInboundService;
import com.zhimu.server.biz.stock.support.StockMessages;
import com.zhimu.server.biz.stock.vo.StockInboundVO;
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

/** API-M08 / M30 入库、M09 详情、M10 修改、M11 作废（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockInboundController {

    private final StockInboundService inboundService;

    /** M08 饲料入库 */
    @PostMapping("/feed-inbounds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockInboundVO> createFeedInbound(
            @Valid @RequestBody FeedInboundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockInboundVO vo = inboundService.createFeedInbound(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.feedInbound(vo), vo);
    }

    /** M30 兽药疫苗入库 */
    @PostMapping("/drug-inbounds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockInboundVO> createDrugInbound(
            @Valid @RequestBody DrugInboundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockInboundVO vo = inboundService.createDrugInbound(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.drugInbound(vo), vo);
    }

    /** M09 入库单详情 */
    @GetMapping("/stock-inbounds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockInboundVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(inboundService.get(id));
    }

    /** M10 修改未出库入库单 */
    @PutMapping("/stock-inbounds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockInboundVO> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody InboundUpdateRequest request) {
        return new ApiResponse<>(0, "已保存入库单修改", inboundService.update(id, request));
    }

    /** M11 作废入库单 */
    @PostMapping("/stock-inbounds/{id:\\d+}/void")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<Object> voidInbound(@PathVariable("id") Long id,
                                           @Valid @RequestBody VoidRequest request) {
        inboundService.voidInbound(id, request);
        return new ApiResponse<>(0, "已作废", null);
    }
}
