package com.zhimu.server.admin.system.interceptor;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /admin/v1/** 来源校验：X-Client 必须为 ADMIN_WEB，否则 40303“请求来源不正确”。
 * Origin 的受信站点校验由框架 OriginCheckInterceptor 负责（zhimu.security.allowed-origins）；
 * 生产部署时同时要求在网关限制 /admin/v1 只接受管理端站点来源。
 */
@Component
public class AdminClientInterceptor implements HandlerInterceptor {

    public static final String ADMIN_CLIENT = "ADMIN_WEB";
    static final String MSG_FORBIDDEN = "请求来源不正确";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String client = request.getHeader(WebHeaders.CLIENT);
        if (!StringUtils.hasText(client) || !ADMIN_CLIENT.equalsIgnoreCase(client.trim())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_FORBIDDEN);
        }
        return true;
    }
}
