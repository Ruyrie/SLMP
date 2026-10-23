package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.service.HomeService;
import com.zhimu.server.biz.today.vo.HomeVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-T01 首页聚合：按 X-Farm-Id 建立上下文（待审核、已驳回只返回基本信息）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home")
    public ApiResponse<HomeVO> home() {
        return ApiResponse.ok(homeService.getHome(UserContext.requireUserId()));
    }
}
