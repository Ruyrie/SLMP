package com.zhimu.slmp.feed.ui.weigh;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.feed.databinding.FeedItemWeighRowBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * G05 逐头录入行：行号即 seq_no（“第 1 头”），行尾 close 删除；在最后一行按键盘“下一项”时新增一行并聚焦。
 * 行数很少变化、行内有输入框，直接在 LinearLayout 中复用行视图，内容相同时不回写（避免光标跳动）。
 */
public class WeighItemRowAdapter {

    public interface Listener {
        void onWeightChanged(int index, @NonNull String text);

        void onRemove(int index);

        /** 最后一行按“下一项”：新增一行 */
        void onAddFromKeyboard();
    }

    private final LinearLayout container;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<Holder> holders = new ArrayList<>();
    private boolean focusLastOnNextSubmit;

    private static final class Holder {
        final FeedItemWeighRowBinding b;
        int index;
        boolean binding;

        Holder(@NonNull FeedItemWeighRowBinding b) {
            this.b = b;
        }
    }

    public WeighItemRowAdapter(@NonNull LinearLayout container, @NonNull Listener listener) {
        this.container = container;
        this.inflater = LayoutInflater.from(container.getContext());
        this.listener = listener;
    }

    /** 下一次 submit 后聚焦最后一行（新增行时） */
    public void focusLastOnNextSubmit() {
        focusLastOnNextSubmit = true;
    }

    public void submit(@Nullable List<String> rows, @Nullable Map<String, String> errors, boolean editable) {
        List<String> list = rows == null ? Collections.emptyList() : rows;
        while (holders.size() < list.size()) {
            holders.add(create());
        }
        while (holders.size() > list.size()) {
            Holder h = holders.remove(holders.size() - 1);
            container.removeView(h.b.getRoot());
        }
        for (int i = 0; i < list.size(); i++) {
            Holder h = holders.get(i);
            h.index = i;
            h.binding = true;
            h.b.feedWeighRowLabel.setText("第 " + (i + 1) + " 头");
            String current = h.b.feedWeighRowInput.getText() == null ? "" : h.b.feedWeighRowInput.getText().toString();
            String value = list.get(i) == null ? "" : list.get(i);
            if (!current.equals(value)) {
                h.b.feedWeighRowInput.setText(value);
            }
            String e = errors == null ? null : errors.get(WeighRegisterViewModel.itemKey(i));
            if (!TextUtils.equals(h.b.feedWeighRowLayout.getError(), e)) {
                h.b.feedWeighRowLayout.setError(TextUtils.isEmpty(e) ? null : e);
                if (TextUtils.isEmpty(e)) {
                    h.b.feedWeighRowLayout.setErrorEnabled(false);
                }
            }
            h.b.feedWeighRowInput.setEnabled(editable);
            h.b.feedWeighRowDelete.setEnabled(editable);
            h.b.feedWeighRowDelete.setContentDescription("删除第 " + (i + 1) + " 头");
            h.binding = false;
        }
        if (focusLastOnNextSubmit && !holders.isEmpty()) {
            focusLastOnNextSubmit = false;
            holders.get(holders.size() - 1).b.feedWeighRowInput.requestFocus();
        }
    }

    @NonNull
    private Holder create() {
        FeedItemWeighRowBinding b = FeedItemWeighRowBinding.inflate(inflater, container, false);
        Holder h = new Holder(b);
        b.feedWeighRowInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!h.binding) {
                    listener.onWeightChanged(h.index, s.toString());
                }
            }
        });
        b.feedWeighRowInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && h.index == holders.size() - 1) {
                focusLastOnNextSubmit = true;
                listener.onAddFromKeyboard();
                return true;
            }
            return false;
        });
        ClickThrottle.setOnClickListener(b.feedWeighRowDelete, v -> listener.onRemove(h.index));
        container.addView(b.getRoot());
        return h;
    }
}
