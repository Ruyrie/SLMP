package com.zhimu.slmp.inspect.ui.today;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectItemTodayRowBinding;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.ui.common.RowStyle;

import java.util.Objects;

/**
 * I01 今日栏舍清单（分页）：养殖户版为分组列表行，员工版为带操作区的卡片（修改巡检 / 转上报 / 查看诊断 / 登记巡检）。
 */
final class InspectTodayAdapter extends PagingDataAdapter<InspectionListItemVO, TodayRowHolder> {

    interface Callback {
        void onItemClick(@NonNull InspectionListItemVO item);

        void onReportClick(@NonNull InspectionListItemVO item);
    }

    private final Callback callback;
    private boolean staff;
    @Nullable
    private String deadline;

    InspectTodayAdapter(@NonNull Callback callback) {
        super(DIFF);
        this.callback = callback;
    }

    void setMode(boolean staffView, @Nullable String deadlineTime) {
        if (staff != staffView || !Objects.equals(deadline, deadlineTime)) {
            staff = staffView;
            deadline = deadlineTime;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @NonNull
    @Override
    public TodayRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TodayRowHolder(InspectItemTodayRowBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TodayRowHolder h, int position) {
        InspectionListItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        RowStyle.position(h.b.inspectRowCard, position, getItemCount());
        h.bindStatus(item, false);
        h.b.inspectRowTitle.setText(item.getPenName());
        h.b.inspectRowSubtitle.setText(staff ? TodayTexts.staffSubtitle(item, deadline)
                : TodayTexts.ownerSubtitle(item, deadline));
        ClickThrottle.setOnClickListener(h.b.getRoot(), v -> callback.onItemClick(item));
        if (!staff) {
            h.b.inspectRowActions.setVisibility(View.GONE);
            return;
        }
        // 员工版操作区
        boolean any = false;
        h.b.inspectRowActionText.setVisibility(View.GONE);
        h.b.inspectRowActionTonal.setVisibility(View.GONE);
        h.b.inspectRowActionFilled.setVisibility(View.GONE);
        if (item.isAbnormal()) {
            if (item.isCanModify()) {
                h.b.inspectRowActionText.setText(R.string.inspect_action_modify);
                h.b.inspectRowActionText.setVisibility(View.VISIBLE);
                ClickThrottle.setOnClickListener(h.b.inspectRowActionText, v -> callback.onItemClick(item));
                any = true;
            }
            if (item.isReported()) {
                if (item.isCanViewDiagnosis()) {
                    h.b.inspectRowActionText.setText(R.string.inspect_action_view_diagnosis);
                    h.b.inspectRowActionText.setVisibility(View.VISIBLE);
                    ClickThrottle.setOnClickListener(h.b.inspectRowActionText, v -> callback.onReportClick(item));
                    any = true;
                }
            } else if (item.isCanReport()) {
                h.b.inspectRowActionTonal.setVisibility(View.VISIBLE);
                ClickThrottle.setOnClickListener(h.b.inspectRowActionTonal, v -> callback.onReportClick(item));
                any = true;
            }
        } else if (item.isPending() && item.isCanRegister()) {
            h.b.inspectRowActionFilled.setVisibility(View.VISIBLE);
            ClickThrottle.setOnClickListener(h.b.inspectRowActionFilled, v -> callback.onItemClick(item));
            any = true;
        } else if (item.isDone() && item.isCanModify()) {
            h.b.inspectRowActionText.setText(R.string.inspect_action_modify);
            h.b.inspectRowActionText.setVisibility(View.VISIBLE);
            ClickThrottle.setOnClickListener(h.b.inspectRowActionText, v -> callback.onItemClick(item));
            any = true;
        }
        h.b.inspectRowActions.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private static final DiffUtil.ItemCallback<InspectionListItemVO> DIFF =
            new DiffUtil.ItemCallback<InspectionListItemVO>() {
                @Override
                public boolean areItemsTheSame(@NonNull InspectionListItemVO a, @NonNull InspectionListItemVO b) {
                    return a.getPenId() == b.getPenId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull InspectionListItemVO a, @NonNull InspectionListItemVO b) {
                    return a.getVersion() == b.getVersion() && Objects.equals(a.getDisplayStatus(), b.getDisplayStatus())
                            && Objects.equals(a.getReportId(), b.getReportId())
                            && Objects.equals(a.getRegisteredAt(), b.getRegisteredAt())
                            && Objects.equals(a.getManualReason(), b.getManualReason());
                }
            };
}
