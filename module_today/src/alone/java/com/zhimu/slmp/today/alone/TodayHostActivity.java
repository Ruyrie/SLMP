package com.zhimu.slmp.today.alone;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.today.ui.home.TodayFragment;

/**
 * 单独运行时承载 T01 今日首页的调试页（壳工程中 T01 是 MainActivity 的“今日”标签，不注册本页）。
 * 切换标签的请求只提示。
 */
public class TodayHostActivity extends BaseActivity implements MainTabHost {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout container = new FrameLayout(this);
        container.setId(com.zhimu.slmp.today.R.id.today_alone_container);
        setContentView(container);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.zhimu.slmp.today.R.id.today_alone_container, new TodayFragment()).commit();
        }
    }

    @Override
    public void selectTab(@NonNull String tabKey) {
        showMessage("单独运行时没有底部导航：" + tabKey);
    }
}
