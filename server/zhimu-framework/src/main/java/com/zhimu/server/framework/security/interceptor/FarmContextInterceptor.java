package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.web.TraceFilter;
import com.zhimu.server.framework.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 养殖场上下文（/api/v1/**，在 AuthInterceptor 之后）：养殖场 ID 优先取路径变量 farmId，没有时取 X-Farm-Id。
 * <ul>
 * <li>@RequireFarm / @FarmRoles（方法上的优先）：不是成员 40302；状态不允许 40305（冻结 40306）；角色不符 40303。</li>
 * <li>没有这两个注解但能取到养殖场 ID：尽力加载成员信息，成功才写入 FarmContext（用于员工字段裁剪），失败不报错。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmContextInterceptor implements HandlerInterceptor {

    public static final String PATH_VARIABLE = "farmId";

    static final String MSG_PENDING = "养殖场正在审核中，审核通过后才能使用";
    static final String MSG_REJECTED = "养殖场审核未通过，请修改后重新提交";
    static final String MSG_DISABLED = "养殖场已停用，重新启用后才能登记";
    static final String MSG_FROZEN_SUFFIX = "已被平台冻结，暂时不能使用。原因见消息中心";

    private static final Set<FarmStatus> READ_DEFAULT = EnumSet.of(FarmStatus.NORMAL, FarmStatus.DISABLED);
    private static final Set<FarmStatus> WRITE_DEFAULT = EnumSet.of(FarmStatus.NORMAL);

    private final FarmMemberCache memberCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireFarm requireFarm = HandlerSupport.find(method, RequireFarm.class);
        FarmRoles farmRoles = HandlerSupport.find(method, FarmRoles.class);
        Long farmId = resolveFarmId(request);
        Long userId = UserContext.userId();

        if (requireFarm == null && farmRoles == null) {
            if (farmId != null && userId != null) {
                tryLoad(farmId, userId);
            }
            return true;
        }

        FarmContext.FarmMember member = farmId == null || userId == null ? null : memberCache.get(farmId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FARM_MEMBER);
        }
        checkStatus(member, allowedStatus(requireFarm, request));
        if (farmRoles != null && !contains(farmRoles.value(), member.role())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN);
        }
        enter(member);
        return true;
    }

    private void tryLoad(Long farmId, Long userId) {
        try {
            FarmContext.FarmMember member = memberCache.get(farmId, userId);
            if (member != null) {
                enter(member);
            }
        } catch (RuntimeException e) {
            log.debug("加载养殖场成员信息失败 farm_id={} user_id={}", farmId, userId, e);
        }
    }

    private static void enter(FarmContext.FarmMember member) {
        FarmContext.set(member);
        MDC.put(TraceFilter.MDC_FARM_ID, String.valueOf(member.farmId()));
    }

    private static Set<FarmStatus> allowedStatus(RequireFarm requireFarm, HttpServletRequest request) {
        if (requireFarm != null && requireFarm.allowStatus().length > 0) {
            return EnumSet.of(requireFarm.allowStatus()[0], requireFarm.allowStatus());
        }
        return HandlerSupport.isReadMethod(request) ? READ_DEFAULT : WRITE_DEFAULT;
    }

    private static void checkStatus(FarmContext.FarmMember member, Set<FarmStatus> allowed) {
        FarmStatus status = member.farmStatus();
        if (status != null && allowed.contains(status)) {
            return;
        }
        if (status == FarmStatus.FROZEN) {
            String name = StringUtils.hasText(member.farmName()) ? member.farmName() : "养殖场";
            throw new BizException(ErrorCode.FARM_FROZEN, name + MSG_FROZEN_SUFFIX);
        }
        String message = status == null ? ErrorCode.FARM_STATUS_FORBIDDEN.getDefaultMessage() : switch (status) {
            case PENDING -> MSG_PENDING;
            case REJECTED -> MSG_REJECTED;
            case DISABLED -> MSG_DISABLED;
            default -> ErrorCode.FARM_STATUS_FORBIDDEN.getDefaultMessage();
        };
        throw new BizException(ErrorCode.FARM_STATUS_FORBIDDEN, message);
    }

    /** 路径变量 farmId 优先，其次请求头 X-Farm-Id；不是正整数时视为没有 */
    @SuppressWarnings("unchecked")
    static Long resolveFarmId(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map) {
            Long fromPath = parseId(((Map<String, String>) map).get(PATH_VARIABLE));
            if (fromPath != null) {
                return fromPath;
            }
        }
        return parseId(request.getHeader(WebHeaders.FARM_ID));
    }

    private static Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean contains(FarmRole[] roles, FarmRole role) {
        for (FarmRole r : roles) {
            if (r == role) {
                return true;
            }
        }
        return false;
    }
}
