package com.zhimu.slmp.stock.ui.common;

import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.databinding.StockItemSectionBinding;

import java.util.List;

/** 本组件共用的视图逻辑：物资图标底色、分组列表项、键值只读行、“放弃这次填写？”、菜单“需审核”注记。 */
public final class StockUi {

    private StockUi() {
    }

    /** 图标底块按语气着色：secondary、warning、error、neutral（流水） */
    public static void tone(@NonNull View leading, @NonNull android.widget.ImageView icon, @ListRowView.Tone int tone) {
        int bgAttr;
        int fgAttr;
        switch (tone) {
            case ListRowView.TONE_WARNING:
                bgAttr = com.zhimu.slmp.base.R.attr.colorWarningContainer;
                fgAttr = com.zhimu.slmp.base.R.attr.colorOnWarningContainer;
                break;
            case ListRowView.TONE_ERROR:
                bgAttr = com.google.android.material.R.attr.colorErrorContainer;
                fgAttr = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case ListRowView.TONE_NEUTRAL:
                bgAttr = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                fgAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
                break;
            default:
                bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
                break;
        }
        leading.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(leading, bgAttr)));
        icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(icon, fgAttr)));
    }

    /** 分组列表项：图标、文字、右侧数量与标签；空值隐藏对应视图 */
    public static void bindRow(@NonNull StockItemRowBinding b, @DrawableRes int icon, @ListRowView.Tone int tone,
                               @Nullable CharSequence title, @Nullable CharSequence subtitle,
                               @Nullable CharSequence value, @Nullable CharSequence tag,
                               @StatusTagView.TagType int tagType, @DrawableRes int tagIcon) {
        if (icon == 0) {
            b.stockRowLeading.setVisibility(View.GONE);
        } else {
            b.stockRowLeading.setVisibility(View.VISIBLE);
            b.stockRowIcon.setImageResource(icon);
            tone(b.stockRowLeading, b.stockRowIcon, tone);
        }
        b.stockRowTitle.setText(title);
        b.stockRowSubtitle.setText(subtitle);
        b.stockRowSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        b.stockRowValue.setText(value);
        b.stockRowValue.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(tag)) {
            b.stockRowTag.setVisibility(View.GONE);
        } else {
            b.stockRowTag.setVisibility(View.VISIBLE);
            b.stockRowTag.setStatus(tag, tagType);
            b.stockRowTag.setTagIcon(tagIcon);
        }
        b.stockRowAction.setVisibility(View.GONE);
        b.stockRowAction.setOnClickListener(null);
        b.stockRowCheck.setVisibility(View.GONE);
        b.stockRowCard.setAlpha(1f);
        b.stockRowCard.setEnabled(true);
        b.stockRowTitle.setPaintFlags(b.stockRowTitle.getPaintFlags()
                & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
    }

    /** 卡片形状按组内位置设置 */
    public static void position(@NonNull MaterialCardView card, int index, int size) {
        GroupPosition.apply(card, GroupPosition.of(index, size));
    }

    /** 分组标题 */
    public static void bindSection(@NonNull StockItemSectionBinding b, @Nullable CharSequence title,
                                   @Nullable CharSequence note, @Nullable CharSequence action,
                                   @Nullable View.OnClickListener onAction) {
        b.stockSectionTitle.setText(title);
        b.stockSectionNote.setText(note);
        b.stockSectionNote.setVisibility(TextUtils.isEmpty(note) ? View.GONE : View.VISIBLE);
        b.stockSectionAction.setText(action);
        b.stockSectionAction.setVisibility(TextUtils.isEmpty(action) ? View.GONE : View.VISIBLE);
        b.stockSectionAction.setOnClickListener(onAction);
    }

    /** 只读分组列表：每行“标签—值”，值为空的行不显示 */
    public static void renderKv(@NonNull ViewGroup box, @Nullable List<String[]> rows) {
        box.removeAllViews();
        if (rows == null) {
            return;
        }
        for (String[] row : rows) {
            if (row.length < 2 || TextUtils.isEmpty(row[1])) {
                continue;
            }
            KeyValueRowView v = new KeyValueRowView(box.getContext());
            v.setKey(row[0]);
            v.setValue(row[1]);
            v.setValueAlign(KeyValueRowView.ALIGN_END);
            box.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** “放弃这次填写？”对话框 */
    public static void showDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        ConfirmDialogFragment.builder(requestKey)
                .title(activity.getString(R.string.stock_discard_title))
                .message(activity.getString(R.string.stock_discard_message))
                .negative(activity.getString(R.string.stock_action_keep_editing))
                .positive(activity.getString(R.string.stock_action_discard))
                .build()
                .showSafely(activity.getSupportFragmentManager());
    }

    /** 菜单项“提交修改申请”，右侧 12 sp on-surface-variant 注明“需审核” */
    @NonNull
    public static CharSequence needReview(@NonNull View anchor, @NonNull CharSequence text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int start = sb.length();
        sb.append("  ").append(anchor.getContext().getString(R.string.stock_need_review));
        int color = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceVariant);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new AbsoluteSizeSpan(12, true), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 把字段滚动到可见并获得焦点（校验未通过的第一个字段） */
    public static void scrollTo(@NonNull androidx.core.widget.NestedScrollView scroll, @Nullable View target) {
        if (target == null) {
            return;
        }
        scroll.post(() -> {
            int top = 0;
            View v = target;
            while (v != null && v != scroll) {
                top += v.getTop();
                v = v.getParent() instanceof View ? (View) v.getParent() : null;
            }
            scroll.smoothScrollTo(0, Math.max(0, top - scroll.getResources().getDimensionPixelSize(
                    com.zhimu.slmp.base.R.dimen.base_space_8)));
            target.requestFocus();
        });
    }

    @NonNull
    public static StockItemRowBinding inflateRow(@NonNull ViewGroup parent) {
        return StockItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    }
}
