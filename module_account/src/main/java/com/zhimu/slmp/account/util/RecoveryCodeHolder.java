package com.zhimu.slmp.account.util;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;

import javax.inject.Inject;

/**
 * 恢复码明文的内存暂存（2.6.5 节）：A03、A11、A13 返回的 recovery_code 只经本类在内存中传给 A04 / A06 第 2 步，
 * 不放 Intent、SavedStateHandle 与日志；读取后清空。
 */
@FeatureScope
public class RecoveryCodeHolder {

    @Nullable
    private RecoveryCodeVO code;

    @Inject
    public RecoveryCodeHolder() {
    }

    public synchronized void put(@Nullable RecoveryCodeVO vo) {
        code = vo;
    }

    /** 取出并清空 */
    @Nullable
    public synchronized RecoveryCodeVO take() {
        RecoveryCodeVO c = code;
        code = null;
        return c;
    }

    public synchronized void clear() {
        code = null;
    }
}
