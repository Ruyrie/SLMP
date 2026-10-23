package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.service.StockService;
import com.zhimu.server.biz.herd.vo.MyPensVO;
import com.zhimu.server.biz.herd.vo.ProductionOverviewVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-L01 生产页概况、API-L02 我的栏舍。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductionController {

    private final StockService stockService;

    /** L01 生产页概况；待审核、已驳回只返回场名与状态 */
    @GetMapping("/production/overview")
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED, FarmStatus.PENDING, FarmStatus.REJECTED})
    public ApiResponse<ProductionOverviewVO> overview() {
        return ApiResponse.ok(stockService.overview());
    }

    /** L02 我的栏舍（只对员工开放） */
    @GetMapping("/my-pens")
    @FarmRoles(FarmRole.STAFF)
    public ApiResponse<MyPensVO> myPens() {
        return ApiResponse.ok(stockService.myPens());
    }
}
