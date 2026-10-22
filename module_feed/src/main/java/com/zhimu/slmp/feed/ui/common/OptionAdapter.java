package com.zhimu.slmp.feed.ui.common;

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

import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 下拉框（Exposed Dropdown Menu）的选项：主文字 16 sp + 次文字 14 sp（“结存 500 千克”）+ 行尾标签（“库存不足”），
 * 不可选的项置灰（次文字写原因）。不做输入过滤，始终显示全部选项。
 */
public class OptionAdapter extends ArrayAdapter<OptionAdapter.Item> {

    /** 选项 */
    public static final class Item {
        public final long id;
        @NonNull
        public final String title;
        @Nullable
        public final String subtitle;
        @Nullable
        public final String tag;
        public final boolean disabled;

        public Item(long id, @NonNull String title, @Nullable String subtitle, @Nullable String tag, boolean disabled) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.tag = tag;
            this.disabled = disabled;
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    private final LayoutInflater inflater;
    private final List<Item> items = new ArrayList<>();

    public OptionAdapter(@NonNull Context context) {
        super(context, R.layout.feed_item_dropdown);
        inflater = LayoutInflater.from(context);
    }

    public void setItems(@NonNull List<Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Nullable
    @Override
    public Item getItem(int position) {
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
        View v = convertView != null ? convertView : inflater.inflate(R.layout.feed_item_dropdown, parent, false);
        Item item = getItem(position);
        TextView title = v.findViewById(R.id.feed_dropdown_title);
        TextView subtitle = v.findViewById(R.id.feed_dropdown_subtitle);
        StatusTagView tag = v.findViewById(R.id.feed_dropdown_tag);
        if (item != null) {
            title.setText(item.title);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(TextUtils.isEmpty(item.subtitle) ? View.GONE : View.VISIBLE);
            if (TextUtils.isEmpty(item.tag)) {
                tag.setVisibility(View.GONE);
            } else {
                tag.setStatus(item.tag, StatusTagView.WARNING);
                tag.setVisibility(View.VISIBLE);
            }
            v.setAlpha(item.disabled ? 0.5f : 1f);
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
                r.count = items.size();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue instanceof Item ? ((Item) resultValue).title : "";
            }
        };
    }
}
