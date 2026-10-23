package com.zhimu.server.biz.account.request;

/** API-A06 刷新令牌：App 在请求体传 refresh_token，Web 从 Cookie 读取。 */
public record TokenRefreshRequest(String refreshToken) {
}
