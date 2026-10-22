package com.zhimu.slmp.health.ui.medicationarchive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.databinding.HealthItemWithdrawalDrugBinding;
import com.zhimu.slmp.health.model.vo.WithdrawalDrugVO;
import com.zhimu.slmp.health.ui.common.ToneStyle;
import com.zhimu.slmp.health.util.HealthFormat;

import java.util.List;

/**
 * D05“按药品计算”分组列表（条目少，直接填充到 LinearLayout）：决定可出栏日期的一项图标底 warning 并标“最晚”，
 * 已过期限的标“已到期”（success），其余不显示标签。
 */
public final class WithdrawalDrugAdapter {

    public static final String TAG_LATEST = "LATEST";
    public static final String TAG_ENDED = "ENDED";

    private WithdrawalDrugAdapter() {
    }

    public static void bind(@NonNull LinearLayout container, @Nullable List<WithdrawalDrugVO> drugs) {
        container.removeAllViews();
        if (drugs == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        int gap = container.getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_list_gap);
        for (int i = 0; i < drugs.size(); i++) {
            WithdrawalDrugVO d = drugs.get(i);
            HealthItemWithdrawalDrugBinding b = HealthItemWithdrawalDrugBinding.inflate(inflater, container, false);
            GroupPosition.apply(b.healthCard, GroupPosition.of(i, drugs.size()));
            boolean latest = TAG_LATEST.equals(d.getTag());
            ToneStyle.apply(b.healthIcon, latest ? ToneStyle.WARNING : ToneStyle.NEUTRAL);
            b.healthTitle.setText(d.getDrugName());
            b.healthSubtitle.setText("末次 " + HealthFormat.date(d.getLastDate()) + HealthFormat.DOT + "休药 "
                    + d.getWithdrawalDays() + " 天");
            b.healthEndDate.setText(HealthFormat.date(d.getEndDate()));
            if (latest) {
                b.healthTag.setStatus(d.getTagText() == null ? "最晚" : d.getTagText(), StatusTagView.WARNING);
                b.healthTag.setVisibility(View.VISIBLE);
            } else if (TAG_ENDED.equals(d.getTag())) {
                b.healthTag.setStatus(d.getTagText() == null ? "已到期" : d.getTagText(), StatusTagView.SUCCESS);
                b.healthTag.setVisibility(View.VISIBLE);
            } else {
                b.healthTag.setVisibility(View.GONE);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : gap;
            container.addView(b.getRoot(), lp);
        }
    }
}
