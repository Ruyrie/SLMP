package com.zhimu.slmp.breed.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.zhimu.slmp.breed.databinding.BreedIncludeSearchBarBinding;

import java.util.function.Consumer;

/**
 * 顶部栏搜索态（B01、B02）：点 search 后顶部栏换为搜索栏并弹出键盘；输入交给 ViewModel 防抖查询；
 * 点返回或清空退出搜索（清空关键词）。
 */
public final class SearchBarController {

    private final View topBar;
    private final BreedIncludeSearchBarBinding bar;
    private final Consumer<String> onQuery;
    private boolean active;

    public SearchBarController(@NonNull View topBar, @NonNull BreedIncludeSearchBarBinding bar,
                               @NonNull CharSequence hint, @NonNull Consumer<String> onQuery) {
        this.topBar = topBar;
        this.bar = bar;
        this.onQuery = onQuery;
        bar.breedSearchInput.setHint(hint);
        bar.breedSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (active) {
                    onQuery.accept(s.toString());
                }
            }
        });
        bar.breedSearchBack.setOnClickListener(v -> close());
        bar.breedSearchClear.setOnClickListener(v -> {
            if (bar.breedSearchInput.length() > 0) {
                bar.breedSearchInput.setText("");
            } else {
                close();
            }
        });
    }

    public boolean isActive() {
        return active;
    }

    public void open() {
        active = true;
        topBar.setVisibility(View.GONE);
        bar.getRoot().setVisibility(View.VISIBLE);
        bar.breedSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) bar.breedSearchInput.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            bar.breedSearchInput.post(() -> imm.showSoftInput(bar.breedSearchInput, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    /** 退出搜索：恢复顶部栏并清空关键词 */
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        bar.breedSearchInput.setText("");
        onQuery.accept("");
        InputMethodManager imm = (InputMethodManager) bar.breedSearchInput.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(bar.breedSearchInput.getWindowToken(), 0);
        }
        bar.getRoot().setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
    }
}
