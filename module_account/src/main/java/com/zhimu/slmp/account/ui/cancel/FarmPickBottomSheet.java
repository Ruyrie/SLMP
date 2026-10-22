package com.zhimu.slmp.account.ui.cancel;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.model.vo.CancelOwnedFarmVO;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;

import java.util.List;

/**
 * A09“选择要转让 / 停用的养殖场”面板（第 4 级）：每项养殖场名与“N 名成员”，单选即生效；
 * 基于 lib_base 的 OptionSheetDialogFragment，结果经 FragmentResult（requestKey {@link #REQUEST_KEY}）回传 farm_id。
 */
public final class FarmPickBottomSheet {

    public static final String REQUEST_KEY = "account_cancel_pick_farm";
    private static final String TAG = "account_cancel_pick_farm";

    private FarmPickBottomSheet() {
    }

    /**
     * @param action {@link CancelAccountViewModel#ACTION_TRANSFER} 或 {@link CancelAccountViewModel#ACTION_DISABLE}
     */
    public static void show(@NonNull Context context, @NonNull FragmentManager fm, @NonNull String action,
                            @NonNull List<CancelOwnedFarmVO> farms) {
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQUEST_KEY)
                .title(context.getString(CancelAccountViewModel.ACTION_DISABLE.equals(action)
                        ? R.string.account_cancel_pick_disable : R.string.account_cancel_pick_transfer));
        for (CancelOwnedFarmVO f : farms) {
            b.option(String.valueOf(f.getFarmId()), f.getName() == null ? "" : f.getName(),
                    context.getString(R.string.account_cancel_members, f.getMemberCount()));
        }
        b.build().showSafely(fm, TAG);
    }

    /** 读取结果中的 farm_id，取消时返回 null */
    @Nullable
    public static Long farmIdOf(@NonNull Bundle result) {
        String v = OptionSheetDialogFragment.getValue(result);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
