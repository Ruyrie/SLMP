package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.FeedSaveRequest;
import com.zhimu.server.biz.stock.service.StockFeedService;
import com.zhimu.server.biz.stock.vo.FeedDetailVO;
import com.zhimu.server.biz.stock.vo.FeedOptionVO;
import com.zhimu.server.biz.stock.vo.FeedStockItemVO;
import com.zhimu.server.biz.stock.vo.FeedStockOverviewVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-M01–M06 饲料库存概览、结存列表、下拉与档案（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockFeedController {

    private final StockFeedService feedService;

    /** M01 饲料库存概览 */
    @GetMapping("/feed-stock/overview")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FeedStockOverviewVO> overview() {
        return ApiResponse.ok(feedService.overview());
    }

    /** M02 饲料结存列表（预警在前） */
    @GetMapping("/feeds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<FeedStockItemVO>> page(@RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(feedService.page(page, size));
    }

    /** M03 饲料下拉：purpose INBOUND / OTHER_OUT */
    @GetMapping("/feeds/options")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<List<FeedOptionVO>> options(
            @RequestParam(value = "purpose", defaultValue = "INBOUND") String purpose,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(feedService.options(purpose, keyword));
    }

    /** M04 饲料档案详情 */
    @GetMapping("/feeds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FeedDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(feedService.detail(id));
    }

    /** M05 新增饲料 */
    @PostMapping("/feeds")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FeedDetailVO> create(@Valid @RequestBody FeedSaveRequest request) {
        FeedDetailVO vo = feedService.create(request);
        return new ApiResponse<>(0, "已新增饲料：" + vo.getName(), vo);
    }

    /** M06 修改饲料档案 */
    @PutMapping("/feeds/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<FeedDetailVO> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody FeedSaveRequest request) {
        FeedDetailVO vo = feedService.update(id, request);
        return new ApiResponse<>(0, "已保存" + vo.getName() + "的档案", vo);
    }
}
