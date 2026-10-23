package com.zhimu.slmp.finance.ui.common;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceItemSaleOrderBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewColumnBinding;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderPageVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 出栏单卡片（E09 待收款、E14 出栏单列表共用，卡片字号、标签颜色和进度条一致）：
 * E09 带约定收款、未收金额与“登记收款”；E14 按出栏月份分组、带“待审核”与逾期提示、已收齐显示收齐日期、已作废写作废原因。
 */
public class SaleOrderCardAdapter extends PagingDataAdapter<SaleOrderItemVO, SaleOrderCardAdapter.Holder> {

    public static final int MODE_RECEIVABLE = 0;
    public static final int MODE_LIST = 1;

    public interface Listener {
        void onClick(@NonNull SaleOrderItemVO o);

        void onReceive(@NonNull SaleOrderItemVO o);
    }

    private static final DiffUtil.ItemCallback<SaleOrderItemVO> DIFF = new DiffUtil.ItemCallback<SaleOrderItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull SaleOrderItemVO a, @NonNull SaleOrderItemVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull SaleOrderItemVO a, @NonNull SaleOrderItemVO b) {
            return Objects.equals(a.getPaymentStatus(), b.getPaymentStatus())
                    && Objects.equals(a.getReceivedAmount(), b.getReceivedAmount())
                    && Objects.equals(a.getSaleAmount(), b.getSaleAmount())
                    && a.isHasPendingChange() == b.isHasPendingChange()
                    && Objects.equals(a.getDueState(), b.getDueState());
        }
    };

    private final int mode;
    private final Listener listener;
    @NonNull
    private Map<String, SaleOrderPageVO.MonthGroup> groups = Collections.emptyMap();
    @Nullable
    private String keyword;
    @Nullable
    private Long highlightId;

    public SaleOrderCardAdapter(int mode, @NonNull Listener listener) {
        super(DIFF);
        this.mode = mode;
        this.listener = listener;
    }

    public void setGroups(@Nullable Map<String, SaleOrderPageVO.MonthGroup> g) {
        groups = g == null ? Collections.emptyMap() : g;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setKeyword(@Nullable String k) {
        keyword = k;
    }

    public void setHighlight(@Nullable Long id) {
        highlightId = id;
        notifyItemRangeChanged(0, getItemCount());
    }

    public int positionOf(long id) {
        for (int i = 0; i < getItemCount(); i++) {
            SaleOrderItemVO o = peek(i);
            if (o != null && o.getId() == id) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FinanceItemSaleOrderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        SaleOrderItemVO o = getItem(position);
        if (o == null) {
            return;
        }
        FinanceItemSaleOrderBinding b = h.binding;
        View root = b.getRoot();
        boolean voided = FinanceFormat.isVoided(o);
        boolean overdue = FinanceFormat.isOverdue(o) && !voided;
        int onSurface = FinanceViews.color(root, com.google.android.material.R.attr.colorOnSurface);
        int variant = FinanceViews.color(root, com.google.android.material.R.attr.colorOnSurfaceVariant);

        // 月份分组标题（E14）
        String month = FinanceFormat.monthOf(o.getSaleDate());
        SaleOrderItemVO prev = position > 0 ? peek(position - 1) : null;
        boolean header = mode == MODE_LIST && month != null
                && (prev == null || !Objects.equals(month, FinanceFormat.monthOf(prev.getSaleDate())));
        b.financeOrderHeader.setVisibility(header ? View.VISIBLE : View.GONE);
        if (header) {
            SaleOrderPageVO.MonthGroup g = groups.get(month);
            String label = g != null && g.getLabel() != null ? g.getLabel() : FinanceFormat.monthLabel(month);
            b.financeOrderHeaderTitle.setText(g == null ? label : label + " · " + g.getCount() + " 张");
            b.financeOrderHeaderAmount.setText(g == null || g.getSaleAmount() == null ? ""
                    : "销售金额 " + FinanceFormat.yuan(g.getSaleAmount()));
        }

        b.financeOrderBatch.setText(FinanceViews.highlight(root, o.getBatchNo(), keyword));
        b.financeOrderBatch.setTextColor(voided ? variant : onSurface);
        b.financeOrderPending.setVisibility(mode == MODE_LIST && o.isHasPendingChange() && !voided ? View.VISIBLE
                : View.GONE);
        if (mode == MODE_RECEIVABLE) {
            FinanceViews.bindDueTag(b.financeOrderStatus, o);
        } else {
            b.financeOrderStatus.setVisibility(View.VISIBLE);
            FinanceViews.bindPaymentTag(b.financeOrderStatus, o);
        }
        b.financeOrderPen.setText(FinanceViews.highlight(root, o.getPenName() + " · " + o.getBuyerName(), keyword));

        if (mode == MODE_RECEIVABLE) {
            String due = o.getDueDate() != null ? "约定收款 " + FinanceFormat.monthDay(o.getDueDate())
                    : "约定收款 未填写 · 按 " + FinanceFormat.monthDay(o.getEffectiveDueDate()) + "提醒";
            b.financeOrderLine.setText(due + "    销售金额 " + FinanceFormat.yuan(o.getSaleAmount()));
            b.financeOrderOverdue.setVisibility(View.GONE);
        } else {
            b.financeOrderLine.setText("出栏 " + FinanceFormat.monthDay(o.getSaleDate()) + "    "
                    + FinanceFormat.quantityWeight(o));
            b.financeOrderLine.setTextColor(voided ? variant : onSurface);
            b.financeOrderOverdue.setVisibility(overdue ? View.VISIBLE : View.GONE);
            if (overdue) {
                b.financeOrderOverdue.setText(FinanceFormat.dueTag(o));
            }
        }
        b.financeOrderProgress.setVisibility(voided ? View.GONE : View.VISIBLE);
        b.financeOrderProgress.setProgress(FinanceFormat.progress(o.getReceivedAmount(), o.getSaleAmount()));

        LinearLayout amounts = b.financeOrderAmounts;
        amounts.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(root.getContext());
        if (mode == MODE_RECEIVABLE) {
            column(inflater, amounts, "已收", FinanceFormat.yuan(o.getReceivedAmount()), false, false, false);
            column(inflater, amounts, "未收", FinanceFormat.yuan(o.getUnpaidAmount()), true, overdue, false);
            MaterialButton receive = (MaterialButton) inflater.inflate(R.layout.finance_view_receive_button, amounts,
                    false);
            receive.setOnClickListener(v -> listener.onReceive(o));
            amounts.addView(receive);
        } else if (voided) {
            column(inflater, amounts, "销售金额", FinanceFormat.yuan(o.getSaleAmount()), false, false, true);
            String key = FinanceFormat.monthDay(o.getVoidedAt()) + "作废" + (o.isVoidByReview() ? " · 经审核" : "");
            FinanceViewColumnBinding c = column(inflater, amounts, key, o.getVoidReason() == null ? ""
                    : o.getVoidReason(), false, false, false);
            ((LinearLayout.LayoutParams) c.getRoot().getLayoutParams()).weight = 2;
            c.financeColValue.setTextColor(variant);
        } else {
            column(inflater, amounts, "销售金额", FinanceFormat.yuan(o.getSaleAmount()), false, false, false);
            column(inflater, amounts, "已收", FinanceFormat.yuan(o.getReceivedAmount()), false, false, false);
            if (o.getPaidOffDate() != null && o.getUnpaidAmount() != null && o.getUnpaidAmount().signum() <= 0) {
                column(inflater, amounts, "收齐", FinanceFormat.monthDay(o.getPaidOffDate()), false, false, false);
            } else {
                column(inflater, amounts, "未收", FinanceFormat.yuan(o.getUnpaidAmount()), true, overdue, false);
            }
        }
        b.financeOrderCard.setCardBackgroundColor(FinanceViews.color(root, highlightId != null && highlightId == o.getId()
                ? com.google.android.material.R.attr.colorSurfaceContainerLow
                : com.google.android.material.R.attr.colorSurfaceContainerLowest));
        b.financeOrderCard.setOnClickListener(v -> listener.onClick(o));
    }

    @NonNull
    private static FinanceViewColumnBinding column(@NonNull LayoutInflater inflater, @NonNull LinearLayout parent,
                                                   @NonNull String key, @NonNull String value, boolean emphasis,
                                                   boolean error, boolean strike) {
        FinanceViewColumnBinding c = FinanceViewColumnBinding.inflate(inflater, parent, false);
        c.financeColLabel.setText(key);
        c.financeColValue.setText(value);
        c.financeColValue.setTextAppearance(emphasis ? com.zhimu.slmp.base.R.style.TextAppearance_Zmt_TitleMedium_Number
                : com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium_Number);
        int color = FinanceViews.color(parent, error ? androidx.appcompat.R.attr.colorError : strike
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface);
        c.financeColValue.setTextColor(ColorStateList.valueOf(color));
        FinanceViews.strike(c.financeColValue, strike);
        parent.addView(c.getRoot());
        return c;
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final FinanceItemSaleOrderBinding binding;

        Holder(@NonNull FinanceItemSaleOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
