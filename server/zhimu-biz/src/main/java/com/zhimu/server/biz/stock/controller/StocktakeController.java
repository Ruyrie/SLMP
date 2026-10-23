package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.StocktakeConfirmRequest;
import com.zhimu.server.biz.stock.service.StocktakeService;
import com.zhimu.server.biz.stock.vo.StocktakeSheetVO;
import com.zhimu.server.biz.stock.vo.StocktakeVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-M14 盘点表、M15 确认盘点、M16 盘点记录、M17 盘点详情（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    /** M14 盘点表 */
    @GetMapping("/stocktakes/sheet")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StocktakeSheetVO> sheet() {
        return ApiResponse.ok(stocktakeService.sheet());
    }

    /** M15 确认盘点 */
    @PostMapping("/stocktakes")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    @Idempotent(required = true)
    public ApiResponse<StocktakeVO> confirm(
            @Valid @RequestBody StocktakeConfirmRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new ApiResponse<>(0, "已确认盘点，结存已更新",
                stocktakeService.confirm(request, idempotencyKey));
    }

    /** M16 盘点记录 */
    @GetMapping("/stocktakes")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<StocktakeVO>> page(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(stocktakeService.page(page, size));
    }

    /** M17 盘点详情 */
    @GetMapping("/stocktakes/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StocktakeVO> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(stocktakeService.get(id));
    }
}
