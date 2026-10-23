package com.zhimu.slmp.finance.ui.common;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.textfield.TextInputEditText;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.widget.ZmtTextInputLayout;
import com.zhimu.slmp.finance.R;

/**
 * 子类名称面板（E03“添加子类”、E05 改名）：一个输入框与主按钮；点主按钮经 FragmentResult 回传名称（面板保持打开），
 * 由宿主校验并调接口，失败用 {@link #setError}，成功后 {@link #dismiss(FragmentManager)}。
 */
public class NameInputBottomSheet extends BaseBottomSheetDialogFragment {

    public static final String RESULT_NAME = "name";
    private static final String TAG = "finance_name_input";
    private static final String ARG_LABEL = "label";
    private static final String ARG_PREFILL = "prefill";
    private static final String ARG_PRIMARY = "primary";

    private ZmtTextInputLayout layout;
    private TextInputEditText input;

    public static void show(@NonNull FragmentManager fm, @NonNull String requestKey, @NonNull String title,
                            @NonNull String label, @Nullable String prefill, @NonNull String primary,
                            @Nullable Bundle extras) {
        NameInputBottomSheet s = new NameInputBottomSheet();
        Bundle args = baseArgs(requestKey, title, null, extras);
        args.putString(ARG_LABEL, label);
        args.putString(ARG_PREFILL, prefill);
        args.putString(ARG_PRIMARY, primary);
        s.setArguments(args);
        s.showSafely(fm, TAG);
    }

    @Nullable
    private static NameInputBottomSheet find(@NonNull FragmentManager fm) {
        Fragment f = fm.findFragmentByTag(TAG);
        return f instanceof NameInputBottomSheet ? (NameInputBottomSheet) f : null;
    }

    public static void setError(@NonNull FragmentManager fm, @Nullable String error) {
        NameInputBottomSheet s = find(fm);
        if (s != null && s.layout != null) {
            s.layout.setError(error);
            s.layout.setErrorEnabled(error != null);
        }
    }

    public static void setLoading(@NonNull FragmentManager fm, boolean loading) {
        NameInputBottomSheet s = find(fm);
        if (s != null && s.getView() != null) {
            s.setPrimaryLoading(loading);
        }
    }

    public static void dismiss(@NonNull FragmentManager fm) {
        NameInputBottomSheet s = find(fm);
        if (s != null) {
            s.dismissAllowingStateLoss();
        }
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getArguments() == null ? null : getArguments().getString(ARG_PRIMARY);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.finance_sheet_input, container, false);
        layout = v.findViewById(R.id.finance_input_layout);
        input = v.findViewById(R.id.finance_input);
        Bundle args = requireArguments();
        layout.setHint(args.getString(ARG_LABEL));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        if (savedInstanceState == null && args.getString(ARG_PREFILL) != null) {
            input.setText(args.getString(ARG_PREFILL));
            input.setSelection(input.length());
        }
        input.requestFocus();
        return v;
    }

    @Override
    protected void onPrimaryButtonClick() {
        Bundle r = new Bundle();
        r.putString(RESULT_NAME, input.getText() == null ? "" : input.getText().toString());
        setResult(r);
    }

    @Nullable
    public static String nameOf(@NonNull Bundle result) {
        return result.getString(RESULT_NAME);
    }
}
