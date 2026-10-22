package com.zhimu.slmp.account.alone;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.zhimu.slmp.account.ui.mine.MineFragment;
import com.zhimu.slmp.base.ui.BaseActivity;

/**
 * 单独运行时代替 MainActivity（action 为 RouterPath.MAIN）：只承载 A07 我的，便于调试登录后进入首页、退出登录等流程。
 */
public class MineHostActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());
        setContentView(container);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(container.getId(), new MineFragment())
                    .commit();
        }
    }
}
