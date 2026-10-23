package com.zhimu.slmp.finance.ui.recordlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.ui.common.FinanceViews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * E04 筛选面板（原型未绘制，按规范补充）：一级分类多选、只看有凭证的记录，按钮“重置”“应用”。
 * 结果：逗号分隔的分类 ID、Chip 名称与是否只看有凭证。
 */
public class RecordFilterBottomSheet extends BaseBottomSheetDialogFragment {

    public static final String RESULT_IDS = "category_ids";
    public static final String RESULT_LABEL = "category_label";
    public static final String RESULT_VOUCHER = "has_voucher";
    private static final String ARG_IDS = "ids";
    private static final String ARG_NAMES = "names";
    private static final String ARG_SELECTED = "selected";
    private static final String ARG_VOUCHER = "voucher";

    private ChipGroup group;
    private MaterialCheckBox voucher;

    public static void show(@NonNull FragmentManager fm, @NonNull String requestKey, @NonNull long[] ids,
                            @NonNull String[] names, @Nullable String selected, boolean hasVoucher) {
        RecordFilterBottomSheet s = new RecordFilterBottomSheet();
        Bundle args = baseArgs(requestKey, null, null, null);
        args.putLongArray(ARG_IDS, ids);
        args.putStringArray(ARG_NAMES, names);
        args.putString(ARG_SELECTED, selected);
        args.putBoolean(ARG_VOUCHER, hasVoucher);
        s.setArguments(args);
        s.showSafely(fm, "finance_record_filter");
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.finance_e04_filter_title);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.finance_e04_filter_apply);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        Bundle a = requireArguments();
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_sheet_padding_horizontal);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, 0, pad, 0);
        TextView label = new TextView(requireContext());
        label.setText(R.string.finance_e04_filter_categories);
        label.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_TitleSmall);
        root.addView(label);
        group = new ChipGroup(requireContext());
        root.addView(group);
        long[] ids = a.getLongArray(ARG_IDS);
        String[] names = a.getStringArray(ARG_NAMES);
        List<String> selected = a.getString(ARG_SELECTED) == null ? new ArrayList<>()
                : Arrays.asList(a.getString(ARG_SELECTED).split(","));
        if (ids != null && names != null) {
            for (int i = 0; i < ids.length; i++) {
                Chip c = FinanceViews.filterChip(requireContext(), group, names[i], String.valueOf(ids[i]));
                c.setChecked(selected.contains(String.valueOf(ids[i])));
            }
        }
        voucher = new MaterialCheckBox(requireContext());
        voucher.setText(R.string.finance_e04_filter_voucher);
        voucher.setChecked(a.getBoolean(ARG_VOUCHER));
        root.addView(voucher);
        MaterialButton reset = new MaterialButton(requireContext(), null,
                androidx.appcompat.R.attr.borderlessButtonStyle);
        reset.setText(R.string.finance_e04_filter_reset);
        reset.setOnClickListener(v -> {
            group.clearCheck();
            for (int i = 0; i < group.getChildCount(); i++) {
                ((Chip) group.getChildAt(i)).setChecked(false);
            }
            voucher.setChecked(false);
        });
        root.addView(reset);
        return root;
    }

    @Override
    protected void onPrimaryButtonClick() {
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip c = (Chip) group.getChildAt(i);
            if (c.isChecked()) {
                ids.add(String.valueOf(c.getTag()));
                names.add(c.getText().toString());
            }
        }
        Bundle r = new Bundle();
        r.putString(RESULT_IDS, ids.isEmpty() ? null : String.join(",", ids));
        r.putString(RESULT_LABEL, names.isEmpty() ? null : names.size() == 1 ? names.get(0)
                : names.get(0) + " 等 " + names.size() + " 类");
        r.putBoolean(RESULT_VOUCHER, voucher.isChecked());
        setResultAndDismiss(r);
    }
}
