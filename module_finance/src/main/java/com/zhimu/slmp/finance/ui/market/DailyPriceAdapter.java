package com.zhimu.slmp.finance.ui.market;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.TipPopup;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.model.vo.DailyPriceVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.Objects;

/**
 * E11 每日价格：价格、日期、较前一日涨跌（出栏价上涨 success、种苗价上涨 error），“已更正”标签点按弹出提示气泡。
 */
public class DailyPriceAdapter extends PagingDataAdapter<DailyPriceVO, DailyPriceAdapter.Holder> {

    private static final DiffUtil.ItemCallback<DailyPriceVO> DIFF = new DiffUtil.ItemCallback<DailyPriceVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull DailyPriceVO a, @NonNull DailyPriceVO b) {
            return Objects.equals(a.getPriceDate(), b.getPriceDate());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DailyPriceVO a, @NonNull DailyPriceVO b) {
            return Objects.equals(a.getPrice(), b.getPrice()) && a.isCorrected() == b.isCorrected();
        }
    };

    private boolean riseIsGood = true;

    public DailyPriceAdapter() {
        super(DIFF);
    }

    public void setRiseIsGood(boolean good) {
        if (riseIsGood != good) {
            riseIsGood = good;
            notifyItemRangeChanged(0, getItemCount());
        }
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
        DailyPriceVO d = getItem(position);
        if (d == null) {
            return;
        }
        ListRowView row = h.row;
        int count = getItemCount();
        row.setGroupPosition(count == 1 ? GroupPosition.SINGLE : position == 0 ? GroupPosition.TOP
                : position == count - 1 ? GroupPosition.BOTTOM : GroupPosition.MIDDLE);
        row.setTitle(FinanceFormat.price(d.getPrice()));
        row.setSubtitle(FinanceFormat.dateWeek(d.getPriceDate()));
        if (d.isCorrected()) {
            row.setStatusTag("已更正", StatusTagView.NEUTRAL, com.zhimu.slmp.base.R.drawable.ic_edit);
            row.getStatusTagView().setOnClickListener(v -> TipPopup.show(v, "管理员在 "
                    + FinanceFormat.monthDay(d.getCorrectedAt() == null ? d.getPriceDate() : d.getCorrectedAt())
                    + "更正了这一天的价格"));
        } else {
            row.setStatusTag(null, StatusTagView.NEUTRAL, 0);
        }
        String change = d.getChangeRate() == null || d.getChangeRate().signum() == 0
                || com.zhimu.slmp.finance.model.FinanceConst.FLAT.equals(d.getDirection()) ? "持平"
                : FinanceFormat.percent(d.getChangeRate().abs());
        row.setTrailingText(change);
        TextView trailing = row.findViewById(com.zhimu.slmp.base.R.id.base_row_trailing_text);
        if (trailing != null) {
            int dir = "持平".equals(change) ? 0 : com.zhimu.slmp.finance.model.FinanceConst.DOWN.equals(d.getDirection())
                    ? -1 : 1;
            FinanceViews.setTrend(trailing, change, FinanceFormat.priceTone(d.getChangeRate(), d.getDirection(),
                    riseIsGood), dir);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ListRowView row;

        Holder(@NonNull ListRowView row) {
            super(row);
            this.row = row;
        }
    }
}
