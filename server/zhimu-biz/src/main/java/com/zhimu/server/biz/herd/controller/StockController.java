package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.query.StockCategoryQuery;
import com.zhimu.server.biz.herd.query.StockPenQuery;
import com.zhimu.server.biz.herd.service.StockService;
import com.zhimu.server.biz.herd.vo.CategoryStockVO;
import com.zhimu.server.biz.herd.vo.PenStockItemVO;
import com.zhimu.server.biz.herd.vo.StockSummaryVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-L03–L05 存栏汇总 / 按栏舍 / 按品类。 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class StockController {

    private final StockService stockService;

    @GetMapping("/summary")
    public ApiResponse<StockSummaryVO> summary() {
        return ApiResponse.ok(stockService.summary());
    }

    @GetMapping("/pens")
    public ApiResponse<PageVO<PenStockItemVO>> pens(StockPenQuery query) {
        return ApiResponse.ok(stockService.pens(query));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryStockVO>> categories(StockCategoryQuery query) {
        return ApiResponse.ok(stockService.categories(query));
    }
}
