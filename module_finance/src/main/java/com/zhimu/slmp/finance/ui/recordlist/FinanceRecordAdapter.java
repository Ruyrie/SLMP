package com.zhimu.slmp.finance.ui.recordlist;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.databinding.FinanceItemRecordBinding;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.common.FinanceIcons;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.Objects;

/**
 * E04 收支流水列表：按记账日期分组（每组第一条显示日期标题，组内首末项圆角 20），自动 / 手工 / 已作废三种样式（图 10-6）。
 */
public class FinanceRecordAdapter extends PagingDataAdapter<FinanceRecordVO, FinanceRecordAdapter.Holder> {

    public interface Listener {
        void onClick(@NonNull FinanceRecordVO r);

        void onSource(@NonNull FinanceRecordVO r);

        void onMore(@NonNull View anchor, @NonNull FinanceRecordVO r);
    }

    private static final DiffUtil.ItemCallback<FinanceRecordVO> DIFF = new DiffUtil.ItemCallback<FinanceRecordVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull FinanceRecordVO a, @NonNull FinanceRecordVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull FinanceRecordVO a, @NonNull FinanceRecordVO b) {
            return Objects.equals(a.getStatus(), b.getStatus()) && Objects.equals(a.getAmount(), b.getAmount())
                    && Objects.equals(a.getTitle(), b.getTitle()) && Objects.equals(a.getRecordDate(), b.getRecordDate())
                    && a.getVoucherCount() == b.getVoucherCount();
        }
    };

    private final Listener listener;
    private boolean showFarm;
    @Nullable
    private Long highlightId;

    public FinanceRecordAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** 全部养殖场视角：次要文字前加养殖场标签 */
    public void setShowFarm(boolean show) {
        if (showFarm != show) {
            showFarm = show;
            notifyDataSetChanged();
        }
    }

    public void setHighlight(@Nullable Long id) {
        highlightId = id;
        notifyDataSetChanged();
    }

    /** 当前已加载项中某条记录的位置；没有时 -1 */
    public int positionOf(long id) {
        for (int i = 0; i < getItemCount(); i++) {
            FinanceRecordVO r = peek(i);
            if (r != null && r.getId() == id) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FinanceItemRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        FinanceRecordVO r = getItem(position);
        if (r == null) {
            return;
        }
        FinanceRecordVO prev = position > 0 ? peek(position - 1) : null;
        FinanceRecordVO next = position + 1 < getItemCount() ? peek(position + 1) : null;
        boolean first = prev == null || !Objects.equals(prev.getRecordDate(), r.getRecordDate());
        boolean last = next == null || !Objects.equals(next.getRecordDate(), r.getRecordDate());
        FinanceItemRecordBinding b = h.binding;
        b.financeRecordHeader.setVisibility(first ? View.VISIBLE : View.GONE);
        if (first) {
            String head = DateFormatter.formatDate(r.getRecordDate());
            if (DateFormatter.todayString().equals(r.getRecordDate())) {
                head += " · 今天";
            }
            b.financeRecordHeader.setText(head);
        }
        GroupPosition.apply(b.financeRecordCard, first && last ? GroupPosition.SINGLE : first ? GroupPosition.TOP
                : last ? GroupPosition.BOTTOM : GroupPosition.MIDDLE);
        b.getRoot().setPadding(b.getRoot().getPaddingLeft(), 0, b.getRoot().getPaddingRight(),
                last ? 0 : (int) (2 * b.getRoot().getResources().getDisplayMetrics().density));

        boolean voided = FinanceConst.STATUS_VOID.equals(r.getStatus());
        boolean manual = FinanceConst.SOURCE_MANUAL.equals(r.getSourceType());
        b.financeRecordIcon.setImageResource(FinanceIcons.of(r.getCategoryIcon()));
        int bgAttr = voided ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                : com.google.android.material.R.attr.colorSecondaryContainer;
        int fgAttr = voided ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSecondaryContainer;
        b.financeRecordIconBg.setBackgroundTintList(ColorStateList.valueOf(FinanceViews.color(b.getRoot(), bgAttr)));
        b.financeRecordIcon.setImageTintList(ColorStateList.valueOf(FinanceViews.color(b.getRoot(), fgAttr)));
        b.financeRecordTitle.setText(r.getTitle());
        b.financeRecordTitle.setTextColor(FinanceViews.color(b.getRoot(), voided
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface));
        FinanceViews.strike(b.financeRecordTitle, voided);
        b.financeRecordAmount.setText(FinanceFormat.signedRecordAmount(r.getRecordType(), r.getAmount()));
        b.financeRecordAmount.setTextColor(b.financeRecordTitle.getCurrentTextColor());
        FinanceViews.strike(b.financeRecordAmount, voided);

        b.financeRecordFarmRow.setVisibility(showFarm && r.getFarmName() != null ? View.VISIBLE : View.GONE);
        if (showFarm && r.getFarmName() != null) {
            b.financeRecordFarm.setStatus(r.getFarmName(), StatusTagView.FARM);
        }
        b.financeRecordSource.setVisibility(!manual && r.getSourceLabel() != null ? View.VISIBLE : View.GONE);
        if (!manual && r.getSourceLabel() != null) {
            b.financeRecordSource.setStatus(r.getSourceLabel(), StatusTagView.PRIMARY);
        }
        b.financeRecordLock.setVisibility(!manual && !voided ? View.VISIBLE : View.GONE);
        b.financeRecordSubtitle.setText(subtitle(r, manual, voided));
        b.financeRecordSubtitle.setVisibility(b.financeRecordSubtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);
        b.financeRecordSourceBtn.setVisibility(!manual && r.getSourceTarget() != null ? View.VISIBLE : View.GONE);
        b.financeRecordVoidTag.setVisibility(voided ? View.VISIBLE : View.GONE);
        b.financeRecordMore.setVisibility(manual && !voided && r.isEditable() ? View.VISIBLE : View.GONE);
        b.financeRecordCard.setCardBackgroundColor(FinanceViews.color(b.getRoot(),
                highlightId != null && highlightId == r.getId()
                        ? com.google.android.material.R.attr.colorSurfaceContainerLow
                        : com.google.android.material.R.attr.colorSurfaceContainerLowest));
        b.financeRecordCard.setOnClickListener(v -> listener.onClick(r));
        b.financeRecordSourceBtn.setOnClickListener(v -> listener.onSource(r));
        b.financeRecordMore.setOnClickListener(v -> listener.onMore(v, r));
    }

    /** 自动记录显示明细；手工记录“手工记录 · ZM2610-031”“手工记录 · 1 张凭证”；已作废“手工记录 · 已作废，不计入汇总” */
    @NonNull
    static String subtitle(@NonNull FinanceRecordVO r, boolean manual, boolean voided) {
        if (!manual) {
            return r.getDetailText() == null ? "" : r.getDetailText();
        }
        StringBuilder sb = new StringBuilder("手工记录");
        if (voided) {
            return sb.append(" · 已作废，不计入汇总").toString();
        }
        if (r.getBatchNo() != null) {
            sb.append(" · ").append(r.getBatchNo());
        }
        if (r.getVoucherCount() > 0) {
            sb.append(" · ").append(r.getVoucherCount()).append(" 张凭证");
        }
        return sb.toString();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FinanceItemRecordBinding binding;

        Holder(@NonNull FinanceItemRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
