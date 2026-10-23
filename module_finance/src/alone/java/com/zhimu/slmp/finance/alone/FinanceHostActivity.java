package com.zhimu.slmp.finance.alone;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.finance.ui.home.FinanceHomeFragment;

/**
 * 单独运行时承载 E01 经营页的调试页（壳工程中 E01 是 MainActivity 的“经营”标签，不注册本页）。
 */
public class FinanceHostActivity extends BaseActivity implements MainTabHost {

    private static final String TAG = "finance_home";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());
        setContentView(container);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(container.getId(), new FinanceHomeFragment(), TAG).commit();
        }
    }

    @Override
    public void selectTab(@NonNull String tabKey) {
        showMessage("单独运行时没有底部导航：" + tabKey);
    }
}
