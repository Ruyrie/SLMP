package com.zhimu.slmp.inspect.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.inspect.databinding.InspectItemTodayRowBinding;
import com.zhimu.slmp.inspect.model.vo.PenInspectionRowVO;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.RowStyle;

import java.util.Objects;

/**
 * I04 巡检记录（分页，按日期倒序）：日期主文字、状态标签行与摘要、尾部 more_vert；漏巡行尾部色调按钮“补录”（所有者、场长）。
 */
final class HistoryAdapter extends PagingDataAdapter<PenInspectionRowVO, HistoryAdapter.Holder> {

    interface Callback {
        void onRowClick(@NonNull PenInspectionRowVO row);

        void onMoreClick(@NonNull View anchor, @NonNull PenInspectionRowVO row);

        void onBackfillClick(@NonNull PenInspectionRowVO row);

        boolean hasMenu(@NonNull PenInspectionRowVO row);

        boolean canBackfill();
    }

    private final Callback callback;
    @Nullable
    private String highlight;

    HistoryAdapter(@NonNull Callback callback) {
        super(DIFF);
        this.callback = callback;
    }

    /** 定位后该行背景闪一次 */
    void setHighlight(@Nullable String date) {
        highlight = date;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(InspectItemTodayRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        PenInspectionRowVO row = getItem(position);
        if (row == null) {
            return;
        }
        RowStyle.position(h.b.inspectRowCard, position, getItemCount());
        String title = InspectFormat.dateWithWeek(row.getInspectDate()) + (row.isToday() ? " · 今天" : "");
        h.b.inspectRowTitle.setText(title);
        h.b.inspectRowTag.setVisibility(View.GONE);
        h.b.inspectRowActions.setVisibility(View.GONE);
        h.b.inspectRowTags.removeAllViews();
        boolean abnormal = row.hasTag(PenInspectionRowVO.TAG_ABNORMAL);
        boolean manual = "MANUAL".equals(row.getAbnormalSource());
        if (row.isMissed()) {
            RowStyle.leading(h.b.inspectRowLeading, R.drawable.ic_fact_check, RowStyle.TONE_SECONDARY);
            h.b.inspectRowTags.setVisibility(View.GONE);
            h.b.inspectRowSubtitle.setText(row.isBackfillExpired() || !row.isCanBackfill()
                    ? "漏巡 · 已超过补录期限" : "漏巡");
            boolean btn = row.isCanBackfill() && callback.canBackfill();
            h.b.inspectRowButton.setVisibility(btn ? View.VISIBLE : View.GONE);
            ClickThrottle.setOnClickListener(h.b.inspectRowButton, v -> callback.onBackfillClick(row));
            h.b.inspectRowMore.setVisibility(View.GONE);
        } else {
            RowStyle.leading(h.b.inspectRowLeading, R.drawable.ic_fact_check, abnormal ? RowStyle.TONE_ERROR
                    : RowStyle.TONE_NEUTRAL);
            h.b.inspectRowButton.setVisibility(View.GONE);
            for (String tag : row.getTags()) {
                addTag(h, tag, manual);
            }
            h.b.inspectRowTags.setVisibility(row.getTags().isEmpty() ? View.GONE : View.VISIBLE);
            StringBuilder sub = new StringBuilder();
            if (!abnormal) {
                sub.append(row.getResultSummary() == null ? "正常" : row.getResultSummary()).append(InspectFormat.DOT);
            }
            if (row.hasTag(PenInspectionRowVO.TAG_BACKFILL)) {
                sub.setLength(0);
                sub.append(nz(row.getRegistrantName())).append(" ")
                        .append(InspectFormat.monthDay(row.getRegisteredAt())).append("补录");
            } else {
                sub.append(nz(row.getRegistrantName())).append(" ").append(InspectFormat.time(row.getRegisteredAt()));
            }
            if (abnormal && manual && row.getManualReason() != null) {
                sub.append("\n手动标记 · ").append(row.getManualReason());
            }
            h.b.inspectRowSubtitle.setText(sub.toString());
            boolean more = callback.hasMenu(row);
            h.b.inspectRowMore.setVisibility(more ? View.VISIBLE : View.GONE);
            ClickThrottle.setOnClickListener(h.b.inspectRowMore, v -> callback.onMoreClick(v, row));
        }
        ClickThrottle.setOnClickListener(h.b.getRoot(), v -> callback.onRowClick(row));
        if (highlight != null && highlight.equals(row.getInspectDate())) {
            highlight = null;
            View card = h.b.inspectRowCard;
            card.post(() -> {
                h.b.inspectRowCard.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(
                        card.getContext(), R.color.zm_secondary_container));
                card.postDelayed(() -> h.b.inspectRowCard.setCardBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(card.getContext(),
                                R.color.zm_surface_container_lowest)), 300);
            });
        }
    }

    private static void addTag(@NonNull Holder h, @NonNull String tag, boolean manual) {
        StatusTagView v = new StatusTagView(h.itemView.getContext());
        switch (tag) {
            case PenInspectionRowVO.TAG_ABNORMAL:
                v.setStatus("异常", StatusTagView.ERROR);
                v.setTagIcon(manual ? com.zhimu.slmp.inspect.R.drawable.inspect_ic_flag : R.drawable.ic_error);
                break;
            case PenInspectionRowVO.TAG_OVERDUE:
                v.setStatus("超时", StatusTagView.NEUTRAL);
                v.setTagIcon(R.drawable.ic_schedule);
                break;
            case PenInspectionRowVO.TAG_BACKFILL:
                v.setStatus("补录", StatusTagView.NEUTRAL);
                v.setTagIcon(R.drawable.ic_history);
                break;
            case PenInspectionRowVO.TAG_ON_SITE:
                v.setStatus("现场", StatusTagView.NEUTRAL);
                v.setTagIcon(R.drawable.ic_location_on);
                break;
            case PenInspectionRowVO.TAG_OFF_SITE:
                v.setStatus("非现场", StatusTagView.WARNING);
                v.setTagIcon(com.zhimu.slmp.inspect.R.drawable.inspect_ic_wrong_location);
                break;
            case PenInspectionRowVO.TAG_NO_LOCATION:
                v.setStatus("无位置", StatusTagView.NEUTRAL);
                v.setTagIcon(com.zhimu.slmp.inspect.R.drawable.inspect_ic_location_off);
                break;
            case PenInspectionRowVO.TAG_PENDING_REVIEW:
                v.setStatus("待审核", StatusTagView.WARNING);
                break;
            default:
                return;
        }
        h.b.inspectRowTags.addView(v);
    }

    @NonNull
    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final InspectItemTodayRowBinding b;

        Holder(@NonNull InspectItemTodayRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final DiffUtil.ItemCallback<PenInspectionRowVO> DIFF =
            new DiffUtil.ItemCallback<PenInspectionRowVO>() {
                @Override
                public boolean areItemsTheSame(@NonNull PenInspectionRowVO a, @NonNull PenInspectionRowVO b) {
                    return Objects.equals(a.getInspectDate(), b.getInspectDate());
                }

                @Override
                public boolean areContentsTheSame(@NonNull PenInspectionRowVO a, @NonNull PenInspectionRowVO b) {
                    return a.getInspectionId() == b.getInspectionId() && a.getTags().equals(b.getTags())
                            && Objects.equals(a.getRegisteredAt(), b.getRegisteredAt())
                            && Objects.equals(a.getPendingRequestId(), b.getPendingRequestId());
                }
            };
}
