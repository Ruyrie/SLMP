package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 修改申请 handler 注册表：启动时收集全部 {@link ChangeRequestHandler}，按 biz_type 建立索引。
 * <p>
 * 缺失的记录类型只告警、不阻止启动（第 1 批 17 种真实 handler 随模块04–10 注册）；
 * 同一 biz_type 注册了多个实现属于编程错误，启动失败。运行时没有 handler 时返回 42207，不抛 500。
 */
@Slf4j
@Component
public class ChangeRequestHandlerRegistry implements SmartInitializingSingleton {

    private final Map<BizType, ChangeRequestHandler> handlers = new EnumMap<>(BizType.class);

    public ChangeRequestHandlerRegistry(List<ChangeRequestHandler> handlerBeans) {
        for (ChangeRequestHandler handler : handlerBeans) {
            Set<BizType> types = handler.supportedBizTypes();
            if (types == null || types.isEmpty()) {
                log.warn("修改申请 handler {} 没有声明 biz_type，已忽略", handler.getClass().getName());
                continue;
            }
            for (BizType type : types) {
                ChangeRequestHandler old = handlers.putIfAbsent(type, handler);
                if (old != null && old != handler) {
                    throw new IllegalStateException("修改申请 handler 重复注册：" + type
                            + " 已由 " + old.getClass().getName() + " 注册，又出现 " + handler.getClass().getName());
                }
            }
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> missing = new TreeSet<>();
        for (BizType type : ChangeBizTypes.APPLICATION_TYPES) {
            if (!handlers.containsKey(type)) {
                missing.add(type.name());
            }
        }
        if (handlers.isEmpty()) {
            log.warn("尚未注册任何修改申请 handler：U01/U02 将返回 42207，17 种业务记录的 handler 需随模块04–10 补齐");
        } else if (!missing.isEmpty()) {
            log.warn("修改申请 handler 未覆盖 {} 种记录类型：{}", missing.size(), missing);
        } else {
            log.info("修改申请 handler 已覆盖全部 {} 种记录类型", ChangeBizTypes.APPLICATION_TYPES.size());
        }
    }

    /** 没有对应实现时返回 null */
    public ChangeRequestHandler get(BizType bizType) {
        return bizType == null ? null : handlers.get(bizType);
    }

    /** 没有对应实现时抛 42207（不阻止启动，但不能提交申请） */
    public ChangeRequestHandler require(BizType bizType) {
        ChangeRequestHandler handler = get(bizType);
        if (handler == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "该记录类型暂不支持提交修改申请");
        }
        return handler;
    }

    public boolean supports(BizType bizType) {
        return get(bizType) != null;
    }

    public Collection<ChangeRequestHandler> handlers() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(handlers.values()));
    }

    /** 已注册的 biz_type */
    public Set<BizType> registeredTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
