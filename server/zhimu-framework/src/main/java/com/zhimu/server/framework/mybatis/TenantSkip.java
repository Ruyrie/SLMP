package com.zhimu.server.framework.mybatis;

import java.util.function.Supplier;

/**
 * 在当前线程临时关闭多租户条件，用于“全部养殖场”统计等跨养殖场查询；关闭后须自行按养殖场 ID 列表过滤。
 * <pre>
 * List&lt;Todo&gt; todos = TenantSkip.call(() -&gt; todoMapper.selectList(
 *         Wrappers.&lt;Todo&gt;lambdaQuery().in(Todo::getFarmId, farmIds)));
 * </pre>
 * 支持嵌套；不会传递到异步线程。
 */
public final class TenantSkip {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private TenantSkip() {
    }

    public static void run(Runnable action) {
        enter();
        try {
            action.run();
        } finally {
            exit();
        }
    }

    public static <T> T call(Supplier<T> action) {
        enter();
        try {
            return action.get();
        } finally {
            exit();
        }
    }

    /** 当前线程是否处于 TenantSkip 范围内 */
    public static boolean isSkipped() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    private static void enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    private static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }
}
