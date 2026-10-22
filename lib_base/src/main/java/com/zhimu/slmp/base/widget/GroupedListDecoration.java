package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;

/**
 * 分组列表的 2 dp 项间缝隙（02 号《UI设计规范》2.4 节：同组列表项之间留 2 dp，透出页面 surface 底色，不用通栏分隔线）。
 * <p>
 * 用法：{@code recyclerView.addItemDecoration(new GroupedListDecoration(context));}
 * 有分组标题等非列表项时，重写 {@link #isGroupItem(RecyclerView, View, int)} 只对列表项留缝隙。
 */
public class GroupedListDecoration extends RecyclerView.ItemDecoration {

    private final int gap;

    public GroupedListDecoration(@NonNull Context context) {
        this.gap = context.getResources().getDimensionPixelSize(R.dimen.base_list_gap);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION || position == 0) {
            return;
        }
        if (isGroupItem(parent, view, position) && isGroupItem(parent, null, position - 1)) {
            outRect.top = gap;
        }
    }

    /**
     * 该位置是否为分组列表项（默认全部是）。
     *
     * @param view 该位置的视图；查询上一项时为 null
     */
    protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
        return true;
    }
}
