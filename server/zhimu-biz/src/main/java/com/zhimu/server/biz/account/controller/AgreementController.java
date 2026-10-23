package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AgreementService;
import com.zhimu.server.biz.account.vo.AgreementVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-A21 当前协议与客服电话（无需登录）。 */
@PublicApi
@RestController
@RequestMapping("/api/v1/agreements")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    @GetMapping("/current")
    public ApiResponse<AgreementVO> current() {
        return ApiResponse.ok(agreementService.current());
    }
}
