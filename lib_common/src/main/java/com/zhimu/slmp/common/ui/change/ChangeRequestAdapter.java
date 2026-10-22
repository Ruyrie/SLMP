package com.zhimu.slmp.common.ui.change;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.databinding.CommonChangeItemRequestBinding;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeRequestItemVO;

import java.util.function.Consumer;

/**
 * U02 申请列表（Paging 3）：首项上方两角 20 dp、末项下方两角 20 dp、其余 4 dp，项间 2 dp（GroupedListDecoration）。
 */
public class ChangeRequestAdapter extends PagingDataAdapter<ChangeRequestItemVO, ChangeRequestAdapter.Holder> {

    private static final DiffUtil.ItemCallback<ChangeRequestItemVO> DIFF = new DiffUtil.ItemCallback<ChangeRequestItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChangeRequestItemVO a, @NonNull ChangeRequestItemVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChangeRequestItemVO a, @NonNull ChangeRequestItemVO b) {
            return a.getId() == b.getId() && eq(a.getStatus(), b.getStatus()) && eq(a.getReviewedAt(), b.getReviewedAt());
        }
    };

    private final Consumer<ChangeRequestItemVO> onClick;
    private final Consumer<ChangeRequestItemVO> onResubmit;

    public ChangeRequestAdapter(@NonNull Consumer<ChangeRequestItemVO> onClick,
                                @NonNull Consumer<ChangeRequestItemVO> onResubmit) {
        super(DIFF);
        this.onClick = onClick;
        this.onResubmit = onResubmit;
    }

    private static boolean eq(@Nullable Object a, @Nullable Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(CommonChangeItemRequestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChangeRequestItemVO item = getItem(position);
        if (item != null) {
            holder.bind(item, GroupPosition.of(position, getItemCount()));
        }
    }

    /** 末行：“10月19日 09:28 提交 · 审核期间原记录照常有效”“10月18日 15:20 驳回”“10月17日 通过 · 已重新计算批次收益” */
    @NonNull
    static String footerOf(@NonNull ChangeRequestItemVO item) {
        String status = item.getStatus();
        if (ChangeRequestItemVO.STATUS_REJECTED.equals(status)) {
            return DateFormatter.formatDateTime(item.getReviewedAt()) + " 驳回";
        }
        if (ChangeRequestItemVO.STATUS_APPROVED.equals(status)) {
            String summary = item.getRecalcSummary();
            return DateFormatter.formatDate(item.getReviewedAt(), false) + " 通过"
                    + (summary == null || summary.isEmpty() ? "" : " · " + summary);
        }
        return DateFormatter.formatDateTime(item.getSubmittedAt()) + " 提交 · 审核期间原记录照常有效";
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final CommonChangeItemRequestBinding b;

        Holder(@NonNull CommonChangeItemRequestBinding binding) {
            super(binding.getRoot());
            this.b = binding;
            b.commonChangeItemBefore.setPaintFlags(b.commonChangeItemBefore.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        void bind(@NonNull ChangeRequestItemVO item, int position) {
            GroupPosition.apply(b.getRoot(), position);
            View root = b.getRoot();
            boolean pending = ChangeRequestItemVO.STATUS_PENDING.equals(item.getStatus());
            int bg = MaterialColors.getColor(root, pending
                    ? com.google.android.material.R.attr.colorSecondaryContainer
                    : com.google.android.material.R.attr.colorSurfaceContainerHighest);
            int fg = MaterialColors.getColor(root, pending
                    ? com.google.android.material.R.attr.colorOnSecondaryContainer
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            GradientDrawable leading = new GradientDrawable();
            leading.setColor(bg);
            leading.setCornerRadius(root.getResources().getDimension(com.zhimu.slmp.base.R.dimen.base_radius_md));
            b.commonChangeItemIcon.setBackground(leading);
            b.commonChangeItemIcon.setImageResource(ChangeBizTypes.iconOf(item.getBizType()));
            b.commonChangeItemIcon.setImageTintList(ColorStateList.valueOf(fg));

            String type = ChangeBizTypes.textOf(item.getBizType(), item.getBizTypeText());
            b.commonChangeItemTitle.setText(type + " · " + (item.isVoid() ? "作废"
                    : (item.getFieldName() == null ? "" : item.getFieldName())));
            renderStatus(item.getStatus(), item.getStatusText());

            b.commonChangeItemFarm.setVisibility(item.getFarmName() == null ? View.GONE : View.VISIBLE);
            b.commonChangeItemFarm.setText(item.getFarmName());
            b.commonChangeItemLocator.setText(item.getRecordLocator());

            b.commonChangeItemVoidTag.setVisibility(item.isVoid() ? View.VISIBLE : View.GONE);
            b.commonChangeItemBefore.setText(item.isVoid() ? root.getContext().getString(R.string.common_change_valid)
                    : item.getBeforeDisplay());
            b.commonChangeItemAfter.setText(item.isVoid() ? root.getContext().getString(R.string.common_change_voided)
                    : item.getAfterDisplay());

            boolean rejected = ChangeRequestItemVO.STATUS_REJECTED.equals(item.getStatus());
            String reject = item.getRejectReason();
            b.commonChangeItemReject.setVisibility(rejected && reject != null ? View.VISIBLE : View.GONE);
            b.commonChangeItemReject.setText(reject == null ? null
                    : root.getContext().getString(R.string.common_change_reject_prefix, reject));
            b.commonChangeItemFooter.setText(footerOf(item));
            b.commonChangeItemResubmit.setVisibility(rejected ? View.VISIBLE : View.GONE);
            b.commonChangeItemResubmit.setOnClickListener(v -> onResubmit.accept(item));
            root.setOnClickListener(v -> onClick.accept(item));
        }

        private void renderStatus(@Nullable String status, @Nullable String text) {
            StatusTagView tag = b.commonChangeItemStatus;
            if (ChangeRequestItemVO.STATUS_PENDING.equals(status)) {
                tag.setStatus(text == null ? "待审核" : text, StatusTagView.WARNING);
                tag.setTagIcon(0);
            } else if (ChangeRequestItemVO.STATUS_REJECTED.equals(status)) {
                tag.setStatus(text == null ? "已驳回" : text, StatusTagView.ERROR);
                tag.setTagIcon(0);
            } else {
                tag.setStatus(text == null ? "已通过" : text, StatusTagView.NEUTRAL);
                tag.setTagIcon(ChangeRequestItemVO.STATUS_APPROVED.equals(status)
                        ? com.zhimu.slmp.base.R.drawable.ic_check : 0);
            }
        }
    }
}
