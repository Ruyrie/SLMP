package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.request.AccountSmsCodeRequest;
import com.zhimu.server.biz.account.request.SmsCodeSendRequest;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.Clients;
import com.zhimu.server.biz.account.vo.SmsCodeSendVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** API-A01、A02 发送验证码。 */
@RestController
@RequiredArgsConstructor
public class SmsCodeController {

    private final SmsCodeService smsCodeService;

    /** API-A01 发送验证码（未登录） */
    @PublicApi
    @RateLimit(key = "sms-codes", limit = 5, by = RateLimit.LimitBy.IP)
    @PostMapping("/api/v1/sms-codes")
    public ApiResponse<SmsCodeSendVO> send(@RequestBody SmsCodeSendRequest request,
                                           @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.orApp(client);
        return ApiResponse.ok(smsCodeService.send(request));
    }

    /** API-A02 发送验证码到本人手机号 */
    @RateLimit(key = "account-sms-codes", limit = 5, by = RateLimit.LimitBy.USER)
    @PostMapping("/api/v1/account/sms-codes")
    public ApiResponse<SmsCodeSendVO> sendToSelf(@RequestBody AccountSmsCodeRequest request,
                                                 @RequestHeader(value = "X-Client", required = false) String client) {
        ClientType type = Clients.require(client);
        return ApiResponse.ok(smsCodeService.sendToSelf(request));
    }
}
