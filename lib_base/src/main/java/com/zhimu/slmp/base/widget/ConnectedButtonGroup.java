package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.zhimu.slmp.base.R;

/**
 * 连接按钮组（02 号《UI设计规范》5.2、7.5 节）：互斥切换（日 / 周 / 月，东山猪场 / 全部养殖场……），必须且只能选中一项。
 * <p>
 * 外观来自主题默认样式 {@code Widget.Zmt.ButtonToggleGroup}（项间 2 dp，外侧全圆角、内侧 8 dp，选中项四角全圆）；
 * 子按钮用 {@code style="@style/Widget.Zmt.Button.Connected"}（选中 primary 底、未选中 surface-container-highest）。
 * 本控件在此基础上处理选中项文字前的 18 dp 对勾：选中时显示，未选中时不占位。
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.ConnectedButtonGroup
 *     android:id="@+id/view_mode"
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:checkedButton="@id/mode_month"&gt;
 *     &lt;com.google.android.material.button.MaterialButton
 *         android:id="@+id/mode_day"
 *         style="@style/Widget.Zmt.Button.Connected"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:text="日" /&gt;
 *     ...
 * &lt;/com.zhimu.slmp.base.widget.ConnectedButtonGroup&gt;
 * </pre>
 * 切换监听用 {@link #addOnButtonCheckedListener(OnButtonCheckedListener)}。
 */
public class ConnectedButtonGroup extends MaterialButtonToggleGroup {

    public ConnectedButtonGroup(@NonNull Context context) {
        this(context, null);
    }

    public ConnectedButtonGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonToggleGroupStyle);
    }

    public ConnectedButtonGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            View child = findViewById(checkedId);
            if (child instanceof MaterialButton) {
                updateCheckIcon((MaterialButton) child);
            }
        });
    }

    @Override
    public void addView(@NonNull View child, int index, @NonNull ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child instanceof MaterialButton) {
            updateCheckIcon((MaterialButton) child);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof MaterialButton) {
                updateCheckIcon((MaterialButton) child);
            }
        }
    }

    private static void updateCheckIcon(@NonNull MaterialButton button) {
        if (button.isChecked()) {
            if (button.getIcon() == null) {
                button.setIconResource(R.drawable.ic_check);
            }
        } else {
            button.setIcon(null);
        }
    }
}
