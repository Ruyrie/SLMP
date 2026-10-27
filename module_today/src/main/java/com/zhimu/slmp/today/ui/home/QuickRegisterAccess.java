package com.zhimu.slmp.today.ui.home;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.today.R;

/** Entry checks use the same server-provided farm permissions as record forms. */
final class QuickRegisterAccess {
    private QuickRegisterAccess() { }

    @StringRes
    static int blockedReason(@Nullable FarmContextVO context) {
        if (context == null || context.getFarmId() <= 0) {
            return R.string.today_quick_no_farm;
        }
        boolean knownRole = FarmContextVO.ROLE_OWNER.equals(context.getRole())
                || FarmContextVO.ROLE_MANAGER.equals(context.getRole())
                || FarmContextVO.ROLE_STAFF.equals(context.getRole());
        if (!knownRole || !FarmContextVO.STATUS_NORMAL.equals(context.getStatus())
                || context.getPermissions() == null || !context.getPermissions().isWritable()) {
            return R.string.today_quick_not_writable;
        }
        return 0;
    }
}
