package com.zhimu.slmp.herd.debug;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.herd.HerdModuleEntry;

/**
 * 调试页（只在 debug 包中）：单独托管 L01 生产页、L02 我的栏舍两个一级页 Fragment，便于组件单独运行时查看。
 * 正式包中两个 Fragment 由壳工程 MainActivity 经 TabRegistry 装配到底部导航。
 */
public class HerdTabHostActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout container = new FrameLayout(this);
        container.setId(android.R.id.custom);
        container.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, 0));
        setContentView(container);
        if (savedInstanceState == null) {
            String tab = getIntent().getStringExtra(HerdModuleEntry.DEBUG_EXTRA_TAB);
            Fragment f = TabRegistry.create(tab == null ? TabRegistry.PRODUCTION : tab);
            if (f != null) {
                getSupportFragmentManager().beginTransaction().replace(android.R.id.custom, f).commit();
            }
        }
    }
}
