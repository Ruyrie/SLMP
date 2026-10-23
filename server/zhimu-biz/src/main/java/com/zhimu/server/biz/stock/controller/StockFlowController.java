package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.service.StockFlowService;
import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-M13 饲料流水、M28 兽药疫苗流水（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockFlowController {

    private final StockFlowService flowService;

    /** M13 饲料流水 */
    @GetMapping("/feed-flows")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<StockFlowItemVO>> feedFlows(
            @RequestParam(value = "feed_id", required = false) Long feedId,
            @RequestParam(value = "flow_group", defaultValue = "ALL") String flowGroup,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(flowService.feedFlows(feedId, flowGroup, page, size));
    }

    /** M28 兽药疫苗流水 */
    @GetMapping("/drug-flows")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<StockFlowItemVO>> drugFlows(
            @RequestParam(value = "drug_lot_id", required = false) Long drugLotId,
            @RequestParam(value = "drug_id", required = false) Long drugId,
            @RequestParam(value = "flow_group", defaultValue = "ALL") String flowGroup,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(flowService.drugFlows(drugLotId, drugId, flowGroup, page, size));
    }
}
