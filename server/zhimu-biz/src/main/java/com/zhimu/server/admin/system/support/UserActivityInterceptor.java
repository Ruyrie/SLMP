package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.service.UserActivityStatService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 登录与养殖场权限校验之后记录用户活跃；统计失败不影响业务请求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityInterceptor implements HandlerInterceptor {
    private static final String TRACKED = UserActivityInterceptor.class.getName() + ".tracked";
    private final UserActivityStatService service;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equals(request.getMethod())
                || request.getAttribute(TRACKED) != null || UserContext.userId() == null) return true;
        request.setAttribute(TRACKED, true);
        var member = FarmContext.get();
        Long userId = UserContext.userId();
        Long farmId = member != null && userId.equals(member.userId()) ? member.farmId() : null;
        try {
            service.touch(userId, farmId);
        } catch (RuntimeException e) {
            log.warn("记录用户日活跃失败 userId={}", userId, e);
        }
        return true;
    }
}
