package com.zhimu.slmp.breed.ui.common;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * 下拉框（Exposed Dropdown Menu）的选项适配器：主文字 + 可选第二行 + 右侧状态标签；不可选的项置灰。
 * 不做输入过滤，始终显示全部选项；列表为空时显示一行空文案（不可选）。
 */
public class OptionAdapter extends ArrayAdapter<OptionAdapter.Item> {

    /** 空列表占位项的 ID */
    public static final long EMPTY_ID = Long.MIN_VALUE;

    /** 选项 */
    public static final class Item {
        public final long id;
        @NonNull
        public final String title;
        @Nullable
        public final String subtitle;
        @Nullable
        public final String tagText;
        @StatusTagView.TagType
        public final int tagType;
        public final boolean disabled;

        public Item(long id, @NonNull String title, @Nullable String subtitle, @Nullable String tagText,
                    @StatusTagView.TagType int tagType, boolean disabled) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.tagText = tagText;
            this.tagType = tagType;
            this.disabled = disabled;
        }

        public Item(long id, @NonNull String title, @Nullable String subtitle) {
            this(id, title, subtitle, null, StatusTagView.NEUTRAL, false);
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    private final LayoutInflater inflater;
    private final List<Item> items = new ArrayList<>();
    @Nullable
    private String emptyText;

    public OptionAdapter(@NonNull Context context) {
        super(context, R.layout.breed_item_dropdown);
        inflater = LayoutInflater.from(context);
    }

    /** 下拉为空时显示的文案（例如“没有可以配种的母猪……”） */
    public void setEmptyText(@Nullable String text) {
        emptyText = text;
        notifyDataSetChanged();
    }

    public void setItems(@NonNull List<Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    private boolean showEmpty() {
        return items.isEmpty() && !TextUtils.isEmpty(emptyText);
    }

    @Override
    public int getCount() {
        return showEmpty() ? 1 : items.size();
    }

    @Nullable
    @Override
    public Item getItem(int position) {
        if (showEmpty()) {
            return new Item(EMPTY_ID, emptyText, null, null, StatusTagView.NEUTRAL, true);
        }
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @Nullable
    public Item findById(@Nullable Long id) {
        if (id == null) {
            return null;
        }
        for (Item i : items) {
            if (i.id == id) {
                return i;
            }
        }
        return null;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        Item i = getItem(position);
        return i != null && !i.disabled;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.breed_item_dropdown, parent, false);
        Item item = getItem(position);
        TextView title = v.findViewById(R.id.breed_dropdown_title);
        TextView subtitle = v.findViewById(R.id.breed_dropdown_subtitle);
        StatusTagView tag = v.findViewById(R.id.breed_dropdown_tag);
        if (item != null) {
            boolean empty = item.id == EMPTY_ID;
            title.setText(item.title);
            title.setMaxLines(empty ? 3 : 1);
            title.setTextAppearance(empty ? com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium
                    : com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyLarge_Number);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(TextUtils.isEmpty(item.subtitle) ? View.GONE : View.VISIBLE);
            tag.setVisibility(TextUtils.isEmpty(item.tagText) ? View.GONE : View.VISIBLE);
            tag.setStatus(item.tagText, item.tagType);
            v.setAlpha(item.disabled && !empty ? 0.5f : 1f);
        }
        return v;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                r.values = items;
                r.count = getCount();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue instanceof Item && ((Item) resultValue).id != EMPTY_ID
                        ? ((Item) resultValue).title : "";
            }
        };
    }

    /** 下拉框：设置适配器与选择回调（不可选的项忽略） */
    @NonNull
    public static OptionAdapter bind(@NonNull MaterialAutoCompleteTextView view, @NonNull LongConsumer onSelected) {
        OptionAdapter adapter = new OptionAdapter(view.getContext());
        view.setAdapter(adapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            Item item = adapter.getItem(position);
            if (item != null && !item.disabled) {
                onSelected.accept(item.id);
            }
        });
        return adapter;
    }

    /** 按选中 ID 显示下拉框文字 */
    public static void showSelected(@NonNull MaterialAutoCompleteTextView view, @NonNull OptionAdapter adapter,
                                    @Nullable Long id) {
        Item item = adapter.findById(id);
        view.setText(item == null ? "" : item.title, false);
    }
}
