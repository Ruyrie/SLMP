package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.service.SaleAdviceService;
import com.zhimu.server.biz.finance.vo.MarketVos;
import com.zhimu.server.biz.finance.vo.SaleAdviceVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-E30–E36：行情与 AI 出栏建议。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
public class MarketController {

    private final MarketPriceService marketPriceService;
    private final SaleAdviceService saleAdviceService;

    /** E30 行情筛选项与默认值 */
    @GetMapping("/market-prices/filters")
    public ApiResponse<MarketVos.FilterVO> filters() {
        return ApiResponse.ok(marketPriceService.filters());
    }

    /** E31 最新价、涨跌、走势、区间统计 */
    @GetMapping("/market-prices/trend")
    public ApiResponse<MarketVos.TrendVO> trend(FinanceQueries.MarketPriceQuery query) {
        return ApiResponse.ok(marketPriceService.trend(query));
    }

    /** E32 每日价格 */
    @GetMapping("/market-prices/daily")
    public ApiResponse<PageVO<MarketVos.DailyPriceVO>> daily(FinanceQueries.MarketPriceQuery query) {
        return ApiResponse.ok(marketPriceService.daily(query));
    }

    /** E33 批次最近一次建议 */
    @GetMapping("/sale-advices/latest")
    public ApiResponse<SaleAdviceVos.SaleAdviceVO> latest(
            @RequestParam("batch_id") Long batchId) {
        return ApiResponse.ok(saleAdviceService.latest(batchId));
    }

    /** E34 重新生成出栏建议 */
    @PostMapping("/sale-advices")
    public ApiResponse<SaleAdviceVos.SaleAdviceVO> regenerate(
            @RequestBody FinanceRequests.SaleAdviceRequest request) {
        return ApiResponse.ok(saleAdviceService.regenerate(request == null ? null : request.getBatchId()));
    }

    /** E35 历史建议 */
    @GetMapping("/sale-advices")
    public ApiResponse<PageVO<SaleAdviceVos.SaleAdviceVO>> history(FinanceQueries.SaleAdviceQuery query) {
        return ApiResponse.ok(saleAdviceService.history(query));
    }

    /** E36 建议全文与状态轮询 */
    @GetMapping("/sale-advices/{id:\\d+}")
    public ApiResponse<SaleAdviceVos.SaleAdviceVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(saleAdviceService.get(id));
    }
}
