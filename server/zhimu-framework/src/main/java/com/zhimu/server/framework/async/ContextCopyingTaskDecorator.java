package com.zhimu.server.framework.async;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 把提交任务时的 TraceContext、UserContext、AdminContext、FarmContext、RequestContext 与日志 MDC 复制到异步线程，
 * 执行完恢复线程原来的上下文（线程池线程原本为空，即清理；拒绝策略 CallerRuns 在调用方线程执行时不会误清调用方的上下文）。
 */
public class ContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Snapshot captured = Snapshot.capture();
        return () -> {
            Snapshot previous = Snapshot.capture();
            captured.apply();
            try {
                runnable.run();
            } finally {
                previous.apply();
            }
        };
    }

    record Snapshot(String traceId,
                    UserContext.LoginUser user,
                    AdminContext.LoginAdmin admin,
                    FarmContext.FarmMember farm,
                    RequestContext.Info request,
                    Map<String, String> mdc) {

        static Snapshot capture() {
            return new Snapshot(TraceContext.get(), UserContext.get(), AdminContext.get(), FarmContext.get(),
                    RequestContext.get(), MDC.getCopyOfContextMap());
        }

        void apply() {
            if (traceId == null) {
                TraceContext.clear();
            } else {
                TraceContext.set(traceId);
            }
            if (user == null) {
                UserContext.clear();
            } else {
                UserContext.set(user);
            }
            if (admin == null) {
                AdminContext.clear();
            } else {
                AdminContext.set(admin);
            }
            if (farm == null) {
                FarmContext.clear();
            } else {
                FarmContext.set(farm);
            }
            if (request == null) {
                RequestContext.clear();
            } else {
                RequestContext.set(request);
            }
            if (mdc == null || mdc.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(mdc);
            }
        }
    }
}
