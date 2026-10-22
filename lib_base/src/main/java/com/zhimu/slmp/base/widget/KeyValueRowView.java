package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.R;

/**
 * 键值列表行（02 号《UI设计规范》2.4 节）：单行 56 dp，左侧标签 body-m 14 sp on-surface-variant（x 32），
 * 右侧值 body-l 16 sp on-surface（x 128 起左对齐，或 {@code app:kvValueAlign="end"} 右对齐），值下可加 body-s 说明。
 * <p>
 * 值为空时默认隐藏整行（员工无权查看的字段后端不返回，前端不渲染、不显示“—”，8.2 节）；
 * 放在 {@link GroupedListLayout} 中时，隐藏后其余行自动重排圆角。
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.KeyValueRowView
 *     app:kvKey="来源模块"
 *     app:kvValue="用药与休药期管理" /&gt;
 * </pre>
 * 代码：{@link #setKey}、{@link #setValue}、{@link #setNote}、{@link #setHideWhenEmpty}、{@link #setGroupPosition}。
 */
public class KeyValueRowView extends MaterialCardView {

    public static final int ALIGN_START = 0;
    public static final int ALIGN_END = 1;

    private final TextView key;
    private final TextView value;
    private final TextView note;
    private final ImageView chevron;
    private boolean hideWhenEmpty = true;
    private int groupPosition = -1;

    public KeyValueRowView(@NonNull Context context) {
        this(context, null);
    }

    public KeyValueRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public KeyValueRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.base_view_key_value_row, this, true);
        key = findViewById(R.id.base_kv_key);
        value = findViewById(R.id.base_kv_value);
        note = findViewById(R.id.base_kv_note);
        chevron = findViewById(R.id.base_kv_chevron);
        setCardBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerLowest));
        setCardElevation(0f);
        setStrokeWidth(0);

        int position = GroupPosition.SINGLE;
        CharSequence k = null;
        CharSequence v = null;
        CharSequence n = null;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyValueRowView);
            position = a.getInt(R.styleable.KeyValueRowView_groupPosition, GroupPosition.SINGLE);
            k = a.getText(R.styleable.KeyValueRowView_kvKey);
            v = a.getText(R.styleable.KeyValueRowView_kvValue);
            n = a.getText(R.styleable.KeyValueRowView_kvNote);
            int keyWidth = a.getDimensionPixelSize(R.styleable.KeyValueRowView_kvKeyWidth, -1);
            if (keyWidth >= 0) {
                setKeyWidth(keyWidth);
            }
            setValueAlign(a.getInt(R.styleable.KeyValueRowView_kvValueAlign, ALIGN_START));
            hideWhenEmpty = a.getBoolean(R.styleable.KeyValueRowView_kvHideWhenEmpty, true);
            setShowChevron(a.getBoolean(R.styleable.KeyValueRowView_kvShowChevron, false));
            a.recycle();
        }
        key.setText(k);
        setNote(n);
        // XML 中未写值时视为静态布局，由代码稍后填值，此时先不隐藏
        if (v != null) {
            setValue(v);
        }
        setGroupPosition(position);
    }

    public void setGroupPosition(@GroupPosition.Value int position) {
        if (groupPosition != position) {
            groupPosition = position;
            GroupPosition.apply(this, position);
        }
    }

    public void setKey(@Nullable CharSequence text) {
        key.setText(text);
    }

    /** 设置值；值为空且 hideWhenEmpty 为 true 时隐藏整行 */
    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
        if (hideWhenEmpty) {
            setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        }
    }

    @NonNull
    public CharSequence getValue() {
        return value.getText();
    }

    /** 值下方的说明（body-s），为空时隐藏 */
    public void setNote(@Nullable CharSequence text) {
        note.setText(text);
        note.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    public void setHideWhenEmpty(boolean hide) {
        hideWhenEmpty = hide;
        if (hide && TextUtils.isEmpty(value.getText())) {
            setVisibility(GONE);
        }
    }

    /** 键的宽度（像素），默认 96 dp，使值从 x 128 起 */
    public void setKeyWidth(int widthPx) {
        key.getLayoutParams().width = widthPx;
        key.requestLayout();
    }

    /** 值的对齐：{@link #ALIGN_START} 或 {@link #ALIGN_END} */
    public void setValueAlign(int align) {
        int gravity = align == ALIGN_END ? Gravity.END : Gravity.START;
        value.setGravity(gravity);
        note.setGravity(gravity);
    }

    public void setShowChevron(boolean show) {
        chevron.setVisibility(show ? VISIBLE : GONE);
    }

    @NonNull
    public TextView getValueView() {
        return value;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        setClickable(l != null);
        setFocusable(l != null);
    }
}
