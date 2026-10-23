package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.request.AccountRecoveryRequest;
import com.zhimu.server.biz.account.service.AccountRecoveryService;
import com.zhimu.server.biz.account.support.Clients;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** API-A10 凭恢复码提交找回申请。 */
@RestController
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    @PublicApi
    @Idempotent(required = true)
    @RateLimit(key = "account-recoveries", limit = 10, by = RateLimit.LimitBy.IP)
    @PostMapping("/api/v1/account-recoveries")
    public ApiResponse<AccountRecoverySubmitVO> submit(@RequestBody AccountRecoveryRequest request,
                                                       @RequestHeader(value = "X-Client", required = false) String client) {
        return ApiResponse.ok(accountRecoveryService.submit(request, Clients.require(client)));
    }
}
