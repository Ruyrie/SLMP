package com.zhimu.slmp.breed.ui.due;

import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * 待产分段（B02“待产”，B01“待产”分段复用）：API-B22 分组列表，“今天”组可登记产仔。
 */
public class FarrowDueFragment extends DueListFragment {

    @NonNull
    public static FarrowDueFragment newInstance(boolean embedded) {
        FarrowDueFragment f = new FarrowDueFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_EMBEDDED, embedded);
        f.setArguments(args);
        return f;
    }

    @Override
    protected boolean isHatch() {
        return false;
    }
}
