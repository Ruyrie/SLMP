package com.zhimu.slmp.stock.ui.common;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.databinding.StockItemSectionBinding;
import com.zhimu.slmp.stock.databinding.StockSheetPickerBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用选择面板（技术准备文档 2.3.1 的 FeedPickerSheet、DrugPickerSheet、DrugLotPickerSheet、CatalogPickerSheet、
 * DrugArchivePickerSheet 合并为一个可配置面板）：标题、副标题、可选搜索框、分组单选列表、行内“去报废”、末尾文字按钮、底部说明。
 * 数据取自宿主 Activity（{@link PickerHost}）；结果经 FragmentResult 回传：{@link #EVENT} 为 select / action / footer，{@link #VALUE} 为选项值。
 */
public class PickerSheet extends BaseBottomSheetDialogFragment {

    public static final String EVENT = "picker_event";
    public static final String VALUE = "picker_value";
    public static final String EVENT_SELECT = "select";
    public static final String EVENT_ACTION = "action";
    public static final String EVENT_FOOTER = "footer";

    private static final String ARG_KEY = "picker_key";
    private static final String ARG_SEARCH_HINT = "picker_search_hint";
    private static final String ARG_KEYWORD = "picker_keyword";
    private static final String ARG_FOOTER = "picker_footer";
    private static final String ARG_FOOTER_ICON = "picker_footer_icon";
    private static final long DEBOUNCE_MS = 300;

    private StockSheetPickerBinding binding;
    private final Adapter adapter = new Adapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingSearch;

    /** 构建面板；requestKey 也作为宿主中的 pickerKey */
    @NonNull
    public static Builder builder(@NonNull String requestKey) {
        return new Builder(requestKey);
    }

    public static final class Builder {
        private final String key;
        private CharSequence title;
        private CharSequence description;
        private String searchHint;
        private String keyword;
        private String footer;
        private int footerIcon = com.zhimu.slmp.base.R.drawable.ic_add;

        Builder(@NonNull String key) {
            this.key = key;
        }

        public Builder title(@Nullable CharSequence t) {
            title = t;
            return this;
        }

        public Builder description(@Nullable CharSequence d) {
            description = d;
            return this;
        }

        /** 显示搜索框 */
        public Builder search(@NonNull String hint, @Nullable String initialKeyword) {
            searchHint = hint;
            keyword = initialKeyword;
            return this;
        }

        /** 末尾文字按钮 */
        public Builder footer(@Nullable String text) {
            footer = text;
            return this;
        }

        @NonNull
        public PickerSheet build() {
            PickerSheet s = new PickerSheet();
            Bundle args = baseArgs(key, title, description, null);
            args.putString(ARG_KEY, key);
            args.putString(ARG_SEARCH_HINT, searchHint);
            args.putString(ARG_KEYWORD, keyword);
            args.putString(ARG_FOOTER, footer);
            args.putInt(ARG_FOOTER_ICON, footerIcon);
            s.setArguments(args);
            return s;
        }
    }

    /** 结果中的事件 */
    @Nullable
    public static String eventOf(@NonNull Bundle result) {
        return result.getString(EVENT);
    }

    @Nullable
    public static String valueOf(@NonNull Bundle result) {
        return result.getString(VALUE);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = StockSheetPickerBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        String key = args.getString(ARG_KEY, "");
        binding.stockPickerList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.stockPickerList.addItemDecoration(new GroupedListDecoration(requireContext()) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int position) {
                return adapter.typeAt(position) == PickerItem.TYPE_ITEM;
            }
        });
        binding.stockPickerList.setAdapter(adapter);

        String hint = args.getString(ARG_SEARCH_HINT);
        if (hint != null) {
            binding.stockPickerSearchLayout.setVisibility(View.VISIBLE);
            binding.stockPickerSearchLayout.setHint(hint);
            String kw = args.getString(ARG_KEYWORD);
            if (savedInstanceState == null && !TextUtils.isEmpty(kw)) {
                binding.stockPickerSearch.setText(kw);
            }
            binding.stockPickerSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    scheduleSearch(key, s.toString().trim());
                }
            });
        }
        String footer = args.getString(ARG_FOOTER);
        if (!TextUtils.isEmpty(footer)) {
            binding.stockPickerFooter.setVisibility(View.VISIBLE);
            binding.stockPickerFooter.setText(footer);
            binding.stockPickerFooter.setIconResource(args.getInt(ARG_FOOTER_ICON));
            binding.stockPickerFooter.setOnClickListener(v -> deliver(EVENT_FOOTER, null));
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String key = requireArguments().getString(ARG_KEY, "");
        if (requireActivity() instanceof PickerHost) {
            LiveData<PickerData> data = ((PickerHost) requireActivity()).pickerData(key);
            if (data != null) {
                data.observe(getViewLifecycleOwner(), this::render);
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        super.onDestroyView();
    }

    private void scheduleSearch(@NonNull String key, @NonNull String keyword) {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> {
            if (requireActivity() instanceof PickerHost) {
                ((PickerHost) requireActivity()).onPickerSearch(key, keyword);
            }
        };
        handler.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    private void render(@Nullable PickerData data) {
        if (binding == null) {
            return;
        }
        List<PickerItem> items = data == null ? new ArrayList<>() : data.items;
        adapter.submit(items);
        String error = data == null ? null : data.errorLine;
        binding.stockPickerError.setText(error);
        binding.stockPickerError.setVisibility(TextUtils.isEmpty(error) ? View.GONE : View.VISIBLE);
        boolean empty = data == null || data.itemCount() == 0;
        String emptyText = data == null ? null : data.emptyText;
        binding.stockPickerEmpty.setText(emptyText);
        binding.stockPickerEmpty.setVisibility(empty && !TextUtils.isEmpty(emptyText) ? View.VISIBLE : View.GONE);
        String note = data == null ? null : data.footnote;
        binding.stockPickerFootnote.setText(note);
        binding.stockPickerFootnote.setVisibility(TextUtils.isEmpty(note) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(note)) {
            binding.stockPickerFootnote.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    com.zhimu.slmp.base.R.drawable.ic_info, 0, 0, 0);
        }
    }

    private void deliver(@NonNull String event, @Nullable String value) {
        Bundle b = new Bundle();
        b.putString(EVENT, event);
        b.putString(VALUE, value);
        setResultAndDismiss(b);
    }

    /** 列表：分组标题 + 分组列表项（组内首末大圆角） */
    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<PickerItem> items = new ArrayList<>();

        void submit(@NonNull List<PickerItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        int typeAt(int position) {
            return position >= 0 && position < items.size() ? items.get(position).type : PickerItem.TYPE_HEADER;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == PickerItem.TYPE_HEADER) {
                StockItemSectionBinding b = StockItemSectionBinding.inflate(inf, parent, false);
                return new Holder<>(b.getRoot(), b);
            }
            StockItemRowBinding b = StockItemRowBinding.inflate(inf, parent, false);
            return new Holder<>(b.getRoot(), b);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            PickerItem item = items.get(position);
            Object b = ((Holder<?>) holder).binding;
            if (b instanceof StockItemSectionBinding) {
                StockUi.bindSection((StockItemSectionBinding) b, item.title, null, null, null);
                return;
            }
            StockItemRowBinding row = (StockItemRowBinding) b;
            StockUi.bindRow(row, item.icon, item.tone, item.title, item.subtitle, item.trailing, item.tag,
                    item.tagType, item.tagIcon);
            int start = position;
            while (start > 0 && items.get(start - 1).type == PickerItem.TYPE_ITEM) {
                start--;
            }
            int end = position;
            while (end < items.size() - 1 && items.get(end + 1).type == PickerItem.TYPE_ITEM) {
                end++;
            }
            StockUi.position(row.stockRowCard, position - start, end - start + 1);
            row.stockRowCheck.setVisibility(item.selected ? View.VISIBLE : View.GONE);
            row.stockRowCard.setAlpha(item.disabled ? 0.6f : 1f);
            if (!TextUtils.isEmpty(item.actionText)) {
                row.stockRowAction.setVisibility(View.VISIBLE);
                row.stockRowAction.setText(item.actionText);
                row.stockRowAction.setOnClickListener(v -> deliver(EVENT_ACTION, item.value));
            }
            row.stockRowCard.setOnClickListener(item.disabled ? null : v -> deliver(EVENT_SELECT, item.value));
            row.stockRowCard.setClickable(!item.disabled);
        }
    }

    private static final class Holder<B> extends RecyclerView.ViewHolder {
        final B binding;

        Holder(@NonNull View v, @NonNull B binding) {
            super(v);
            this.binding = binding;
        }
    }
}
