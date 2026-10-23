package com.zhimu.slmp.finance.ui.advice;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceItemKeyValueBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewAdviceCardBinding;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.FinanceFormat;

/**
 * AI 出栏建议卡的显示（E12 当前建议、历史建议面板只读）：结论只用三种文字，固定声明常驻。
 */
public final class AdviceCardBinder {

    private AdviceCardBinder() {
    }

    /**
     * @param readOnly  历史建议：标题行写生成日期，不显示“重新生成”与“登记出栏单”
     * @param generating 生成中：骨架条块，“重新生成”不可点
     */
    public static void bind(@NonNull FinanceViewAdviceCardBinding b, @Nullable SaleAdviceVO a, boolean readOnly,
                            boolean generating, boolean failed) {
        android.content.Context c = b.getRoot().getContext();
        b.financeAiRegenerate.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        b.financeAiRegenerate.setEnabled(!generating);
        String time = generating ? c.getString(R.string.finance_e12_generating)
                : a == null || a.getGeneratedAt() == null ? "" : readOnly
                ? FinanceFormat.monthDay(a.getGeneratedAt()) : DateFormatter.formatDateTime(a.getGeneratedAt());
        b.financeAiTitle.setText(c.getString(R.string.finance_e12_card_title, time));
        String source = a == null ? null : a.getSourceLabel();
        b.financeAiSource.setVisibility(source != null && !generating ? View.VISIBLE : View.GONE);
        b.financeAiSource.setText(source);
        b.financeAiSkeleton.setVisibility(generating ? View.VISIBLE : View.GONE);
        boolean ok = !generating && !failed && a != null && FinanceConst.GEN_SUCCESS.equals(a.getStatus());
        b.financeAiFailed.setVisibility(!generating && failed ? View.VISIBLE : View.GONE);
        b.financeAiContent.setVisibility(ok ? View.VISIBLE : View.GONE);
        if (!ok) {
            return;
        }
        b.financeAiConclusion.setText(a.getConclusionText() == null ? FinanceConst.conclusionText(a.getConclusion())
                : a.getConclusionText());
        boolean withdrawal = a.isInWithdrawal();
        b.financeAiTag.setVisibility(withdrawal ? View.VISIBLE : View.GONE);
        if (withdrawal) {
            b.financeAiTag.setStatus(a.getWithdrawalDaysLeft() == null ? "休药中"
                    : "休药中 · 剩 " + a.getWithdrawalDaysLeft() + " 天", StatusTagView.WARNING);
        }
        StringBuilder window = new StringBuilder();
        if (a.getWindowStart() != null) {
            window.append("建议时间段：").append(FinanceFormat.monthDay(a.getWindowStart())).append("—")
                    .append(FinanceFormat.monthDay(a.getWindowEnd()));
            if (withdrawal && a.getSellableDate() != null) {
                window.append("，不早于可出栏日期 ").append(FinanceFormat.monthDay(a.getSellableDate()));
            }
            window.append("。");
        }
        b.financeAiWindow.setText(window);
        b.financeAiWindow.setVisibility(window.length() == 0 ? View.GONE : View.VISIBLE);
        b.financeAiBasis.setVisibility(a.isBasisInsufficient() && a.getBasisNote() != null ? View.VISIBLE : View.GONE);
        b.financeAiBasis.setText(a.getBasisNote());

        LayoutInflater inflater = LayoutInflater.from(c);
        b.financeAiRows.removeAllViews();
        SaleAdviceVO.AdviceReasons r = a.getReasons();
        if (r != null) {
            addRow(inflater, b.financeAiRows, c.getString(R.string.finance_e12_key_withdrawal), r.getWithdrawal());
            addRow(inflater, b.financeAiRows, c.getString(R.string.finance_e12_key_age), r.getAge());
            addRow(inflater, b.financeAiRows, c.getString(R.string.finance_e12_key_weight), r.getWeight());
            addRow(inflater, b.financeAiRows, c.getString(R.string.finance_e12_key_price), r.getPrice());
        }
        b.financeAiRisks.removeAllViews();
        boolean hasRisk = !a.getRisks().isEmpty();
        b.financeAiDivider.setVisibility(hasRisk ? View.VISIBLE : View.GONE);
        b.financeAiRisksTitle.setVisibility(hasRisk ? View.VISIBLE : View.GONE);
        for (String risk : a.getRisks()) {
            FinanceItemKeyValueBinding row = FinanceItemKeyValueBinding.inflate(inflater, b.financeAiRisks, false);
            row.financeKvKey.setVisibility(View.GONE);
            row.financeKvValue.setText(risk);
            Drawable d = AppCompatResources.getDrawable(c, com.zhimu.slmp.base.R.drawable.ic_warning);
            if (d != null) {
                int size = (int) (18 * c.getResources().getDisplayMetrics().density);
                d = d.mutate();
                d.setBounds(0, 0, size, size);
                d.setTint(FinanceViews.color(row.getRoot(), com.zhimu.slmp.base.R.attr.colorWarning));
                row.financeKvValue.setCompoundDrawablesRelative(d, null, null, null);
                row.financeKvValue.setCompoundDrawablePadding((int) (6 * c.getResources().getDisplayMetrics().density));
            }
            ((LinearLayout.LayoutParams) row.financeKvValue.getLayoutParams()).setMarginStart(0);
            b.financeAiRisks.addView(row.getRoot());
        }
        b.financeAiRegister.setVisibility(!readOnly && FinanceConst.SELL_SOON.equals(a.getConclusion())
                ? View.VISIBLE : View.GONE);
    }

    private static void addRow(@NonNull LayoutInflater inflater, @NonNull LinearLayout parent, @NonNull String key,
                               @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        FinanceItemKeyValueBinding row = FinanceItemKeyValueBinding.inflate(inflater, parent, false);
        row.financeKvKey.setText(key);
        row.financeKvValue.setText(value);
        parent.addView(row.getRoot());
    }
}
