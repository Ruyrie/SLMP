package com.zhimu.slmp.finance.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.databinding.BaseIncludeTopAppBarBinding;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.finance.util.NavTarget;

/**
 * 模块10 二级页基类：顶部栏返回与滚动变色、整页状态（重试、无权查看“返回”）、跳转事件，
 * 以及子页面（Activity Result）返回后的回调 {@link #onChildResult}。
 */
public abstract class FinancePageActivity extends BaseActivity {

    protected ActivityResultLauncher<Intent> childLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        childLauncher = Router.registerForResult(this, this::onChildResult);
        super.onCreate(savedInstanceState);
    }

    /** 子页面返回；默认不处理 */
    protected void onChildResult(@NonNull ActivityResult result) {
    }

    protected void setupTopBar(@NonNull BaseIncludeTopAppBarBinding bar, @NonNull CharSequence title,
                               @Nullable View scrolling) {
        TopAppBarHelper.setupBack(bar.baseToolbar, title);
        if (scrolling != null) {
            TopAppBarHelper.bindLiftOnScroll(bar.baseAppBar, scrolling);
        }
    }

    protected void bindState(@NonNull StateLayout state, @NonNull BaseViewModel vm) {
        state.setOnRetryListener(v -> vm.retry());
        state.setOnNoPermissionActionListener(v -> finish());
        vm.getUiState().observe(this, state::render);
    }

    protected void observeNav(@NonNull LiveData<NavTarget> event) {
        event.observe(this, t -> FinanceNav.open(this, t, childLauncher));
    }
}
