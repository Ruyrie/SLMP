package com.zhimu.slmp.network;

import androidx.annotation.Nullable;

/**
 * 客户端上下文（lib_common 的 FarmContext 实现）：HeaderInterceptor 据此添加 X-Farm-Id。
 */
public interface ClientContextProvider {

    /** 当前养殖场 ID，没有养殖场时为 null */
    @Nullable
    Long currentFarmId();
}
