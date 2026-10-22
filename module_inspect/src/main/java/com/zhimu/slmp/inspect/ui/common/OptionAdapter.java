package com.zhimu.slmp.inspect.ui.common;

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
import com.zhimu.slmp.inspect.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * 下拉框（Exposed Dropdown Menu）的选项：主文字 + 可选第二行；不做输入过滤，始终显示全部选项。
 */
public class OptionAdapter extends ArrayAdapter<OptionAdapter.Item> {

    public static final class Item {
        public final long id;
        @NonNull
        public final String title;
        @Nullable
        public final String subtitle;

        public Item(long id, @NonNull String title, @Nullable String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
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
        super(context, R.layout.inspect_item_dropdown);
        inflater = LayoutInflater.from(context);
    }

    /** 绑定到下拉框：选择后回调 id */
    @NonNull
    public static OptionAdapter bind(@NonNull MaterialAutoCompleteTextView view, @NonNull LongConsumer onSelected) {
        OptionAdapter adapter = new OptionAdapter(view.getContext());
        view.setAdapter(adapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            Item item = adapter.getItem(position);
            if (item != null) {
                onSelected.accept(item.id);
            }
        });
        return adapter;
    }

    /** 按选中 id 显示文字 */
    public void showSelected(@NonNull MaterialAutoCompleteTextView view, @Nullable Long id) {
        Item item = findById(id);
        view.setText(item == null ? "" : item.title, false);
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

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.inspect_item_dropdown, parent, false);
        Item item = getItem(position);
        TextView title = v.findViewById(R.id.inspect_dropdown_title);
        TextView subtitle = v.findViewById(R.id.inspect_dropdown_subtitle);
        if (item != null) {
            title.setText(item.title);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(TextUtils.isEmpty(item.subtitle) ? View.GONE : View.VISIBLE);
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
