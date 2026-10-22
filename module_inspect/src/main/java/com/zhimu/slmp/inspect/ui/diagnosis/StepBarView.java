package com.zhimu.slmp.inspect.ui.diagnosis;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.zhimu.slmp.base.R;

/**
 * I06 诊断状态步骤条：待诊断 / 已诊断 / 已处理 / 已结案四个节点均分；已完成节点 primary 实心圆 + check，
 * 当前节点在待诊断时为 hourglass_top，未到节点 outline-variant 圆环 + 序号；连接线已过 primary、未到 outline-variant；
 * 当前节点标签 primary。
 */
public class StepBarView extends LinearLayout {

    private static final String[] LABELS = {"待诊断", "已诊断", "已处理", "已结案"};

    public StepBarView(@NonNull Context context) {
        this(context, null);
    }

    public StepBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setPadding(0, dp(8), 0, dp(8));
        setStep(0, true);
    }

    /**
     * @param current 当前步骤 0–3
     * @param waiting 当前为待诊断（hourglass_top）
     */
    public void setStep(int current, boolean waiting) {
        removeAllViews();
        for (int i = 0; i < LABELS.length; i++) {
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout row = new FrameLayout(getContext());
            // 连接线（左半、右半）
            if (i > 0) {
                View left = new View(getContext());
                left.setBackgroundColor(color(i <= current ? R.color.zm_primary : R.color.zm_outline_variant));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, dp(2), Gravity.CENTER_VERTICAL | Gravity.START);
                row.addView(left, lp);
                left.setTag("left");
            }
            if (i < LABELS.length - 1) {
                View right = new View(getContext());
                right.setBackgroundColor(color(i < current ? R.color.zm_primary : R.color.zm_outline_variant));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, dp(2), Gravity.CENTER_VERTICAL | Gravity.END);
                row.addView(right, lp);
                right.setTag("right");
            }
            boolean done = i < current || (i == current && !(waiting && i == 0));
            FrameLayout node = new FrameLayout(getContext());
            node.setBackgroundResource(done || i == current ? com.zhimu.slmp.inspect.R.drawable.inspect_bg_step_done
                    : com.zhimu.slmp.inspect.R.drawable.inspect_bg_step_todo);
            if (done || i == current) {
                ImageView icon = new ImageView(getContext());
                icon.setImageResource(waiting && i == current ? R.drawable.ic_hourglass_top : R.drawable.ic_check);
                icon.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.zm_on_primary));
                node.addView(icon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            } else {
                TextView num = new TextView(getContext());
                num.setText(String.valueOf(i + 1));
                num.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall);
                num.setTextColor(color(R.color.zm_on_surface_variant));
                num.setGravity(Gravity.CENTER);
                node.addView(num, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
            row.addView(node, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            col.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(24)));
            TextView label = new TextView(getContext());
            label.setText(LABELS[i]);
            label.setTextAppearance(i == current ? R.style.TextAppearance_Zmt_LabelMedium : R.style.TextAppearance_Zmt_BodySmall);
            label.setTextColor(color(i == current ? R.color.zm_primary : R.color.zm_on_surface_variant));
            LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(6);
            col.addView(label, llp);
            addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        // 连接线宽度：节点两侧各半列减去节点半径
        post(() -> {
            for (int i = 0; i < getChildCount(); i++) {
                LinearLayout col = (LinearLayout) getChildAt(i);
                FrameLayout row = (FrameLayout) col.getChildAt(0);
                int half = Math.max(0, row.getWidth() / 2 - dp(14));
                for (int j = 0; j < row.getChildCount(); j++) {
                    View v = row.getChildAt(j);
                    if ("left".equals(v.getTag()) || "right".equals(v.getTag())) {
                        v.getLayoutParams().width = half;
                        v.requestLayout();
                    }
                }
            }
        });
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
