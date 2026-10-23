package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.DrugOutboundRequest;
import com.zhimu.server.biz.stock.request.FeedOtherOutRequest;
import com.zhimu.server.biz.stock.request.OutboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.service.StockOutboundService;
import com.zhimu.server.biz.stock.support.StockMessages;
import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** API-M12 往来单位、M20–M23 出库、M32 领用 / 退货、M33 分摊预览（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockOutboundController {

    private final StockOutboundService outboundService;

    /** M12 最近供应商 / 退回对象 */
    @GetMapping("/stock-partners/recent")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<List<String>> recentPartners(
            @RequestParam(value = "kind", defaultValue = "SUPPLIER") String kind,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(outboundService.recentPartners(kind, keyword));
    }

    /** M20 饲料其他出库 */
    @PostMapping("/feed-other-outbounds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockOutboundVO> createFeedOtherOut(
            @Valid @RequestBody FeedOtherOutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockOutboundVO vo = outboundService.createFeedOtherOut(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.feedOtherOut(vo), vo);
    }

    /** M21 出库单详情 */
    @GetMapping("/stock-outbounds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockOutboundVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(outboundService.get(id));
    }

    /** M22 当天修改出库单 */
    @PutMapping("/stock-outbounds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockOutboundVO> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody OutboundUpdateRequest request) {
        return new ApiResponse<>(0, "已修改，修改记录已保留", outboundService.update(id, request));
    }

    /** M23 当天作废出库单 */
    @PostMapping("/stock-outbounds/{id:\\d+}/void")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<Object> voidOutbound(@PathVariable("id") Long id,
                                            @Valid @RequestBody VoidRequest request) {
        outboundService.voidOutbound(id, request);
        return new ApiResponse<>(0, "已作废这条出库记录", null);
    }

    /** M32 领用 / 退货 */
    @PostMapping("/drug-outbounds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StockOutboundVO> createDrugOutbound(
            @Valid @RequestBody DrugOutboundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockOutboundVO vo = outboundService.createDrugOutbound(request, idempotencyKey);
        return new ApiResponse<>(0, StockMessages.drugOutbound(vo), vo);
    }

    /** M33 领用分摊预览 */
    @GetMapping("/drug-outbounds/allocation-preview")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<CostAllocationVO> allocationPreview(
            @RequestParam("drug_lot_id") Long drugLotId,
            @RequestParam(value = "input_qty", defaultValue = "0") BigDecimal inputQty,
            @RequestParam(value = "input_unit", defaultValue = "PACK") String inputUnit,
            @RequestParam(value = "target_type", defaultValue = "BATCH") String targetType,
            @RequestParam(value = "batch_id", required = false) Long batchId,
            @RequestParam(value = "pen_id", required = false) Long penId) {
        return ApiResponse.ok(outboundService.allocationPreview(drugLotId, inputQty, inputUnit, targetType,
                batchId, penId));
    }
}
