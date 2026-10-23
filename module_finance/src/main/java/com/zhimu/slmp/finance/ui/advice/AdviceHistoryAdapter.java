package com.zhimu.slmp.finance.ui.advice;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.function.Consumer;

/**
 * E12 历史建议：当时的结论、“ZM2610-031 · 10月12日”，已出栏的批次追加“· 已于 10月13日出栏”。
 */
public class AdviceHistoryAdapter extends PagingDataAdapter<SaleAdviceVO, AdviceHistoryAdapter.Holder> {

    private static final DiffUtil.ItemCallback<SaleAdviceVO> DIFF = new DiffUtil.ItemCallback<SaleAdviceVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull SaleAdviceVO a, @NonNull SaleAdviceVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull SaleAdviceVO a, @NonNull SaleAdviceVO b) {
            return java.util.Objects.equals(a.getConclusion(), b.getConclusion());
        }
    };

    private final Consumer<SaleAdviceVO> onClick;

    public AdviceHistoryAdapter(@NonNull Consumer<SaleAdviceVO> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListRowView row = new ListRowView(parent.getContext());
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = parent.getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin);
        lp.setMargins(m, 0, m, (int) (2 * parent.getResources().getDisplayMetrics().density));
        row.setLayoutParams(lp);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        SaleAdviceVO a = getItem(position);
        if (a == null) {
            return;
        }
        int count = getItemCount();
        h.row.setGroupPosition(count == 1 ? GroupPosition.SINGLE : position == 0 ? GroupPosition.TOP
                : position == count - 1 ? GroupPosition.BOTTOM : GroupPosition.MIDDLE);
        h.row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_auto_awesome, ListRowView.TONE_TERTIARY);
        h.row.setTitle(a.getConclusionText() == null ? FinanceConst.conclusionText(a.getConclusion())
                : a.getConclusionText());
        String sub = a.getBatchNo() + " · " + FinanceFormat.monthDay(a.getGeneratedAt());
        if (a.getBatchEndDate() != null) {
            sub += "\n· 已于 " + FinanceFormat.monthDay(a.getBatchEndDate()) + "出栏";
        }
        h.row.setSubtitle(sub);
        h.row.setSubtitleMaxLines(2);
        h.row.setShowChevron(true);
        ClickThrottle.setOnClickListener(h.row, v -> onClick.accept(a));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ListRowView row;

        Holder(@NonNull ListRowView row) {
            super(row);
            this.row = row;
        }
    }
}
