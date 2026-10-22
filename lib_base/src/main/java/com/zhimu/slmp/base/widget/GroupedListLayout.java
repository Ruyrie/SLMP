package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.zhimu.slmp.base.R;

/**
 * 静态分组列表容器（02 号《UI设计规范》2.4、5.2 节）：纵向排列子项，项间留 2 dp 缝隙，
 * 并按可见子项的位置自动设置首、中、末、单个四种圆角。子项隐藏（GONE）后其余项自动重排圆角，
 * 适用于“员工无权的字段不渲染”的键值列表、设置列表。
 * <p>
 * 子项为 {@link ListRowView}、{@link KeyValueRowView} 或任意 {@link MaterialCardView}。
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.GroupedListLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"&gt;
 *     &lt;com.zhimu.slmp.base.widget.KeyValueRowView ... /&gt;
 *     &lt;com.zhimu.slmp.base.widget.KeyValueRowView ... /&gt;
 * &lt;/com.zhimu.slmp.base.widget.GroupedListLayout&gt;
 * </pre>
 * XML 属性：{@code app:groupGap}（默认 2 dp）。
 */
public class GroupedListLayout extends LinearLayout {

    public GroupedListLayout(@NonNull Context context) {
        this(context, null);
    }

    public GroupedListLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int gap = context.getResources().getDimensionPixelSize(R.dimen.base_list_gap);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GroupedListLayout);
            gap = a.getDimensionPixelSize(R.styleable.GroupedListLayout_groupGap, gap);
            a.recycle();
        }
        setGroupGap(gap);
    }

    /** 设置项间缝隙（像素） */
    public void setGroupGap(int gapPx) {
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(0);
        divider.setSize(gapPx, gapPx);
        setDividerDrawable(divider);
        setShowDividers(SHOW_DIVIDER_MIDDLE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updatePositions();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** 按可见子项重新设置圆角；子项可见性变化后会在下一次测量时自动调用 */
    public void updatePositions() {
        int visible = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != GONE) {
                visible++;
            }
        }
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int position = GroupPosition.of(index++, visible);
            if (child instanceof ListRowView) {
                ((ListRowView) child).setGroupPosition(position);
            } else if (child instanceof KeyValueRowView) {
                ((KeyValueRowView) child).setGroupPosition(position);
            } else if (child instanceof MaterialCardView) {
                Object old = child.getTag(R.id.base_tag_group_position);
                if (!(old instanceof Integer) || (Integer) old != position) {
                    GroupPosition.apply((MaterialCardView) child, position);
                    child.setTag(R.id.base_tag_group_position, position);
                }
            }
        }
    }
}
