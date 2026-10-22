package com.zhimu.slmp;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.ui.BaseFragment;

/**
 * 底部导航中尚未注册（对应组件还没有加入工程）的标签占位页：“该功能将在后续版本开放”。
 */
public class PlaceholderTabFragment extends BaseFragment {

    private static final String ARG_TITLE = "title";

    @NonNull
    public static PlaceholderTabFragment newInstance(@NonNull String title) {
        PlaceholderTabFragment f = new PlaceholderTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.addView(text(requireArguments().getString(ARG_TITLE, ""),
                com.google.android.material.R.attr.textAppearanceTitleLarge));
        TextView msg = text(getString(R.string.app_tab_placeholder),
                com.google.android.material.R.attr.textAppearanceBodyMedium);
        msg.setPadding(0, Math.round(8 * getResources().getDisplayMetrics().density), 0, 0);
        root.addView(msg);
        return root;
    }

    private TextView text(String s, int appearanceAttr) {
        TextView tv = new TextView(requireContext());
        tv.setText(s);
        tv.setGravity(Gravity.CENTER);
        TypedValue v = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(appearanceAttr, v, true)) {
            tv.setTextAppearance(v.resourceId);
        }
        return tv;
    }
}
