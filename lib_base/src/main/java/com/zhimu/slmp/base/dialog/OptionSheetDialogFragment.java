package com.zhimu.slmp.base.dialog;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textview.MaterialTextView;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;

import java.util.ArrayList;
import java.util.List;

/**
 * 单选列表面板（02 号《UI设计规范》7.12 节）：点选即生效并关闭，不放“确定”。
 * <p>
 * 选项行高 64 dp：单选按钮 20 dp（x 24）、文字从 x 60 起（主文字 body-l + 说明 body-m），右侧可带语义标签（如“推荐”“当前”）。
 * 因业务原因不能选的项不隐藏，放进“不可选择”分组并写明原因（如“只能报废”）；角色无权使用的项不要加入。
 * 结果经 FragmentResult 回传：{@link #RESULT_VALUE}、{@link #RESULT_LABEL}、{@link #RESULT_EXTRAS}（透传）。
 * <pre>
 * OptionSheetDialogFragment.builder(REQ_PICK_LOT)
 *         .title("选择疫苗批号")
 *         .option(new Option("2025081", "2025081", "有效期至 2026年3月 · 余 120 头份").tag("推荐", StatusTagView.PRIMARY))
 *         .option(new Option("2024112", "2024112", "已过期，只能报废").disabled(true))
 *         .selected(currentLot)
 *         .build()
 *         .showSafely(getSupportFragmentManager());
 *
 * getSupportFragmentManager().setFragmentResultListener(REQ_PICK_LOT, this, (key, result) -&gt;
 *         viewModel.onLotPicked(OptionSheetDialogFragment.getValue(result)));
 * </pre>
 */
public class OptionSheetDialogFragment extends BaseBottomSheetDialogFragment {

    public static final String RESULT_VALUE = "value";
    public static final String RESULT_LABEL = "label";

    private static final String ARG_OPTIONS = "base_option_options";
    private static final String ARG_SELECTED = "base_option_selected";
    private static final String ARG_FOOTNOTE = "base_option_footnote";
    private static final String ARG_GROUP_DISABLED = "base_option_group_disabled";

    /** 选项 */
    public static final class Option implements Parcelable {
        @NonNull
        final String value;
        @NonNull
        final CharSequence label;
        @Nullable
        final CharSequence hint;
        boolean disabled;
        @Nullable
        CharSequence tagText;
        int tagType = StatusTagView.PRIMARY;

        public Option(@NonNull String value, @NonNull CharSequence label) {
            this(value, label, null);
        }

        /**
         * @param hint 第二行说明；不可选的项在这里写原因
         */
        public Option(@NonNull String value, @NonNull CharSequence label, @Nullable CharSequence hint) {
            this.value = value;
            this.label = label;
            this.hint = hint;
        }

        /** 不可选（放进“不可选择”分组，文字为 on-surface-variant） */
        @NonNull
        public Option disabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        /** 右侧语义标签，如“推荐”（{@link StatusTagView#PRIMARY}）、“待审核”（{@link StatusTagView#WARNING}） */
        @NonNull
        public Option tag(@Nullable CharSequence text, @StatusTagView.TagType int type) {
            this.tagText = text;
            this.tagType = type;
            return this;
        }

        @NonNull
        public String getValue() {
            return value;
        }

        @NonNull
        public CharSequence getLabel() {
            return label;
        }

        public boolean isDisabled() {
            return disabled;
        }

        Option(@NonNull Parcel in) {
            value = in.readString();
            label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            hint = in.readInt() == 1 ? TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in) : null;
            disabled = in.readInt() == 1;
            tagText = in.readInt() == 1 ? TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in) : null;
            tagType = in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(value);
            TextUtils.writeToParcel(label, dest, flags);
            dest.writeInt(hint == null ? 0 : 1);
            if (hint != null) {
                TextUtils.writeToParcel(hint, dest, flags);
            }
            dest.writeInt(disabled ? 1 : 0);
            dest.writeInt(tagText == null ? 0 : 1);
            if (tagText != null) {
                TextUtils.writeToParcel(tagText, dest, flags);
            }
            dest.writeInt(tagType);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Option> CREATOR = new Creator<Option>() {
            @Override
            public Option createFromParcel(Parcel in) {
                return new Option(in);
            }

            @Override
            public Option[] newArray(int size) {
                return new Option[size];
            }
        };
    }

    // ---------------- 构建 ----------------

    @NonNull
    public static Builder builder(@NonNull String requestKey) {
        return new Builder(requestKey);
    }

    /** 面板构建器 */
    public static final class Builder {
        private final String requestKey;
        @Nullable
        private CharSequence title;
        @Nullable
        private CharSequence description;
        @Nullable
        private CharSequence footnote;
        @Nullable
        private String selected;
        @Nullable
        private Bundle extras;
        private boolean groupDisabled = true;
        private final ArrayList<Option> options = new ArrayList<>();

        Builder(@NonNull String requestKey) {
            this.requestKey = requestKey;
        }

        @NonNull
        public Builder title(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder description(@Nullable CharSequence description) {
            this.description = description;
            return this;
        }

        @NonNull
        public Builder option(@NonNull Option option) {
            options.add(option);
            return this;
        }

        @NonNull
        public Builder option(@NonNull String value, @NonNull CharSequence label, @Nullable CharSequence hint) {
            options.add(new Option(value, label, hint));
            return this;
        }

        @NonNull
        public Builder options(@NonNull List<Option> list) {
            options.addAll(list);
            return this;
        }

        /** 当前选中项的 value */
        @NonNull
        public Builder selected(@Nullable String value) {
            this.selected = value;
            return this;
        }

        /** 面板底部的 body-s 注释（带 16 dp info 图标） */
        @NonNull
        public Builder footnote(@Nullable CharSequence footnote) {
            this.footnote = footnote;
            return this;
        }

        /** 不可选的项是否归入“不可选择”分组（默认是；false 时留在原位置只改文字颜色） */
        @NonNull
        public Builder groupDisabled(boolean group) {
            this.groupDisabled = group;
            return this;
        }

        /** 原样透传到结果中的数据（如列表项 ID） */
        @NonNull
        public Builder extras(@Nullable Bundle extras) {
            this.extras = extras;
            return this;
        }

        @NonNull
        public OptionSheetDialogFragment build() {
            Bundle args = baseArgs(requestKey, title, description, extras);
            args.putParcelableArrayList(ARG_OPTIONS, options);
            args.putString(ARG_SELECTED, selected);
            args.putCharSequence(ARG_FOOTNOTE, footnote);
            args.putBoolean(ARG_GROUP_DISABLED, groupDisabled);
            OptionSheetDialogFragment f = new OptionSheetDialogFragment();
            f.setArguments(args);
            return f;
        }
    }

    /** 从结果中取出选中项的 value */
    @Nullable
    public static String getValue(@NonNull Bundle result) {
        return result.getString(RESULT_VALUE);
    }

    @Nullable
    public static Bundle getExtras(@NonNull Bundle result) {
        return result.getBundle(RESULT_EXTRAS);
    }

    // ---------------- 视图 ----------------

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        ArrayList<Option> options = BundleCompat.getParcelableArrayList(args, ARG_OPTIONS, Option.class);
        String selected = args.getString(ARG_SELECTED);
        CharSequence footnote = args.getCharSequence(ARG_FOOTNOTE);
        boolean groupDisabled = args.getBoolean(ARG_GROUP_DISABLED, true);

        NestedScrollView scroll = new NestedScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.base_space_2));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<Option> enabled = new ArrayList<>();
        List<Option> disabled = new ArrayList<>();
        if (options != null) {
            for (Option o : options) {
                if (groupDisabled && o.disabled) {
                    disabled.add(o);
                } else {
                    enabled.add(o);
                }
            }
        }
        for (Option o : enabled) {
            list.addView(createRow(inflater, list, o, o.value.equals(selected)));
        }
        if (!disabled.isEmpty()) {
            int inset = getResources().getDimensionPixelSize(R.dimen.base_sheet_padding_horizontal);
            MaterialDivider divider = new MaterialDivider(requireContext());
            divider.setDividerInsetStart(inset);
            divider.setDividerInsetEnd(inset);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = getResources().getDimensionPixelSize(R.dimen.base_space_2);
            list.addView(divider, dlp);
            TextView header = new MaterialTextView(requireContext());
            header.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
            header.setTextColor(com.google.android.material.color.MaterialColors.getColor(header,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            header.setText(R.string.base_sheet_disabled_group);
            header.setPadding(inset, getResources().getDimensionPixelSize(R.dimen.base_space_3), inset,
                    getResources().getDimensionPixelSize(R.dimen.base_space_1));
            list.addView(header);
            for (Option o : disabled) {
                list.addView(createRow(inflater, list, o, false));
            }
        }
        if (!TextUtils.isEmpty(footnote)) {
            list.addView(createFootnote(footnote));
        }
        return scroll;
    }

    @NonNull
    private View createRow(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @NonNull Option option,
                           boolean checked) {
        View row = inflater.inflate(R.layout.base_item_sheet_option, parent, false);
        MaterialRadioButton radio = row.findViewById(R.id.base_option_radio);
        TextView label = row.findViewById(R.id.base_option_label);
        TextView hint = row.findViewById(R.id.base_option_hint);
        StatusTagView tag = row.findViewById(R.id.base_option_tag);
        radio.setChecked(checked);
        label.setText(option.label);
        hint.setText(option.hint);
        hint.setVisibility(TextUtils.isEmpty(option.hint) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(option.tagText)) {
            tag.setStatus(option.tagText, option.tagType);
            tag.setVisibility(View.VISIBLE);
        }
        boolean enabled = !option.disabled;
        radio.setEnabled(enabled);
        label.setEnabled(enabled);
        row.setEnabled(enabled);
        row.setSelected(checked);
        if (enabled) {
            ClickThrottle.setOnClickListener(row, v -> onOptionSelected(option));
        } else {
            row.setClickable(false);
            row.setBackground(null);
        }
        return row;
    }

    @NonNull
    private View createFootnote(@NonNull CharSequence text) {
        TextView note = new MaterialTextView(requireContext());
        note.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall);
        int color = com.google.android.material.color.MaterialColors.getColor(note,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        note.setTextColor(color);
        note.setText(text);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.base_icon_size_tag);
        android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(requireContext(), R.drawable.ic_info);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
            icon.setBounds(0, 0, iconSize, iconSize);
            note.setCompoundDrawablesRelative(icon, null, null, null);
            note.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.base_space_1));
        }
        int inset = getResources().getDimensionPixelSize(R.dimen.base_sheet_padding_horizontal);
        note.setPadding(inset, getResources().getDimensionPixelSize(R.dimen.base_space_3), inset, 0);
        return note;
    }

    /** 选中一项：回传结果并关闭 */
    protected void onOptionSelected(@NonNull Option option) {
        Bundle result = new Bundle();
        result.putString(RESULT_VALUE, option.value);
        result.putCharSequence(RESULT_LABEL, option.label);
        setResultAndDismiss(result);
    }
}
