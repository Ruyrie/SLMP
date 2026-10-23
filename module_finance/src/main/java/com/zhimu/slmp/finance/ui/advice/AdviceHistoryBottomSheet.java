package com.zhimu.slmp.finance.ui.advice;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.finance.databinding.FinanceViewAdviceCardBinding;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.network.GsonFactory;

/**
 * E12 历史建议全文（只读，同样式 AI 卡，标题行写生成日期，不显示“重新生成”）。
 */
public class AdviceHistoryBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_ADVICE = "advice";

    public static void show(@NonNull FragmentManager fm, @NonNull SaleAdviceVO advice) {
        AdviceHistoryBottomSheet s = new AdviceHistoryBottomSheet();
        Bundle args = baseArgs(null, advice.getBatchNo() + (advice.getPenName() == null ? "" : " · "
                + advice.getPenName()), null, null);
        args.putString(ARG_ADVICE, GsonFactory.get().toJson(advice));
        s.setArguments(args);
        s.showSafely(fm, "finance_advice_history");
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        FinanceViewAdviceCardBinding b = FinanceViewAdviceCardBinding.inflate(inflater, container, false);
        SaleAdviceVO a = GsonFactory.get().fromJson(requireArguments().getString(ARG_ADVICE), SaleAdviceVO.class);
        AdviceCardBinder.bind(b, a, true, false, a == null || FinanceConst.GEN_FAILED.equals(a.getStatus()));
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_sheet_padding_horizontal);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, 0, pad, pad);
        b.getRoot().setLayoutParams(lp);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        box.addView(b.getRoot());
        return box;
    }
}
