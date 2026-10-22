package com.zhimu.slmp.inspect.ui.today;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.inspect.databinding.InspectItemTodayRowBinding;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.ui.common.RowStyle;

/**
 * I01 栏舍行（养殖户版分组列表行、员工版带操作区的卡片、员工版“昨天”行）。
 */
final class TodayRowHolder extends RecyclerView.ViewHolder {

    final InspectItemTodayRowBinding b;

    TodayRowHolder(@NonNull InspectItemTodayRowBinding b) {
        super(b.getRoot());
        this.b = b;
    }

    /** 状态标签与前置图标 */
    void bindStatus(@NonNull InspectionListItemVO item, boolean overdueAsTag) {
        if (item.isAbnormal()) {
            RowStyle.leading(b.inspectRowLeading, R.drawable.ic_fact_check, RowStyle.TONE_ERROR);
            RowStyle.tag(b.inspectRowTag, "异常", StatusTagView.ERROR, item.isManualAbnormal()
                    ? com.zhimu.slmp.inspect.R.drawable.inspect_ic_flag : R.drawable.ic_error);
        } else if (item.isPending()) {
            RowStyle.leading(b.inspectRowLeading, R.drawable.ic_fact_check, RowStyle.TONE_SECONDARY);
            RowStyle.tag(b.inspectRowTag, "未巡", StatusTagView.PRIMARY, 0);
        } else if (item.isDone()) {
            RowStyle.leading(b.inspectRowLeading, R.drawable.ic_fact_check, RowStyle.TONE_NEUTRAL);
            if (overdueAsTag && item.isOverdue()) {
                RowStyle.tag(b.inspectRowTag, "超时", StatusTagView.NEUTRAL, R.drawable.ic_schedule);
            } else if (overdueAsTag && item.isBackfill()) {
                RowStyle.tag(b.inspectRowTag, "补录", StatusTagView.NEUTRAL, R.drawable.ic_history);
            } else {
                RowStyle.tag(b.inspectRowTag, "已巡", StatusTagView.NEUTRAL, R.drawable.ic_check);
            }
        } else {
            RowStyle.leading(b.inspectRowLeading, R.drawable.ic_fact_check, RowStyle.TONE_SECONDARY);
            RowStyle.tag(b.inspectRowTag, "漏巡", StatusTagView.WARNING, 0);
        }
        b.inspectRowButton.setVisibility(View.GONE);
        b.inspectRowMore.setVisibility(View.GONE);
        b.inspectRowTags.setVisibility(View.GONE);
    }
}
