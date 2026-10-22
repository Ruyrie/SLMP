package com.zhimu.slmp.breed.ui.due;

import androidx.annotation.NonNull;

/**
 * 待出雏分段（B02“待出雏”）：API-B23 分组列表，主文字为“栏舍名 · 批次号”，点列表项进入 B07；
 * 禽类养殖场的所有者、场长在列表上方看到“登记入孵”。
 */
public class HatchDueFragment extends DueListFragment {

    @NonNull
    public static HatchDueFragment newInstance() {
        return new HatchDueFragment();
    }

    @Override
    protected boolean isHatch() {
        return true;
    }
}
