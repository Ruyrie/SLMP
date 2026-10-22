package com.zhimu.slmp.feed.ui.common;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.widget.BottomActionBar;
import com.zhimu.slmp.feed.R;

import java.util.List;
import java.util.function.LongConsumer;

/** 表单页的共用视图逻辑：下拉框、放弃填写对话框、只读键值行、“提交修改申请 需审核”按钮。 */
public final class FeedViews {

    private FeedViews() {
    }

    /** 下拉框：设置适配器与选择回调（不可选的项忽略） */
    @NonNull
    public static OptionAdapter bindDropdown(@NonNull MaterialAutoCompleteTextView view,
                                             @NonNull LongConsumer onSelected) {
        OptionAdapter adapter = new OptionAdapter(view.getContext());
        view.setAdapter(adapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = adapter.getItem(position);
            if (item != null && !item.disabled) {
                onSelected.accept(item.id);
            }
        });
        return adapter;
    }

    /** 按选中 ID 显示下拉框文字 */
    public static void showSelected(@NonNull MaterialAutoCompleteTextView view, @NonNull OptionAdapter adapter,
                                    @Nullable Long id) {
        OptionAdapter.Item item = adapter.findById(id);
        view.setText(item == null ? "" : item.title, false);
    }

    /** “放弃这次填写？”对话框 */
    public static void showDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        ConfirmDialogFragment.builder(requestKey)
                .title(activity.getString(R.string.feed_discard_title))
                .message(activity.getString(R.string.feed_discard_message))
                .negative(activity.getString(R.string.feed_action_keep_editing))
                .positive(activity.getString(R.string.feed_action_discard))
                .build()
                .showSafely(activity.getSupportFragmentManager());
    }

    /** 只读卡片的“标签—值”行 */
    public static void renderRows(@NonNull ViewGroup box, @Nullable List<String[]> rows) {
        box.removeAllViews();
        if (rows == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(box.getContext());
        for (String[] row : rows) {
            ViewGroup v = (ViewGroup) inflater.inflate(R.layout.feed_item_kv, box, false);
            ((TextView) v.findViewById(R.id.feed_kv_key)).setText(row[0]);
            ((TextView) v.findViewById(R.id.feed_kv_value)).setText(row.length > 1 ? row[1] : "");
            box.addView(v);
        }
    }

    /** 底部描边按钮“提交修改申请”，文字右侧 12 sp on-surface-variant 注明“需审核” */
    public static void showRequestChange(@NonNull BottomActionBar bar, @NonNull CharSequence text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int start = sb.length();
        sb.append("  ").append(bar.getContext().getString(R.string.feed_action_need_review));
        int color = MaterialColors.getColor(bar, com.google.android.material.R.attr.colorOnSurfaceVariant);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new AbsoluteSizeSpan(12, true), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        bar.setPrimary(null, 0);
        bar.setSecondary(sb, 0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bar.getSecondaryButton().getLayoutParams();
        lp.setMarginEnd(0);
        bar.getSecondaryButton().setLayoutParams(lp);
    }
}
