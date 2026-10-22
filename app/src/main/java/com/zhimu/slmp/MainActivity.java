package com.zhimu.slmp;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.ui.InsetsHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 主页：按 FarmContext 角色装配底部导航（01 号文档 2.2 节）。
 * 所有者与场长“今日、生产、助手、经营、我的”；员工“今日、我的栏舍、助手、我的”；没有养殖场或养殖场待审核时按所有者。
 * 标签 Fragment 由 TabRegistry 创建，没有注册的标签显示占位页；角色变化时重建导航。
 * 返回：不在“今日”标签时先回到“今日”，在“今日”时交给系统（预测性返回到桌面）。
 */
public class MainActivity extends BaseActivity implements MainTabHost {

    private static final String STATE_TAB = "main_tab";
    private static final String STATE_LAYOUT = "main_layout";
    private static final String TAG_PREFIX = "main_tab_";
    private static final String LAYOUT_OWNER = "OWNER";
    private static final String LAYOUT_STAFF = "STAFF";

    /** 标签定义 */
    private static final class Tab {
        final String key;
        @StringRes
        final int title;
        @DrawableRes
        final int icon;

        Tab(String key, int title, int icon) {
            this.key = key;
            this.title = title;
            this.icon = icon;
        }
    }

    private static final Tab TODAY = new Tab(TabRegistry.TODAY, R.string.app_tab_today,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_today);
    private static final Tab PRODUCTION = new Tab(TabRegistry.PRODUCTION, R.string.app_tab_production,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_fence);
    private static final Tab MY_PENS = new Tab(TabRegistry.MY_PENS, R.string.app_tab_my_pens,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_fence);
    private static final Tab ASSISTANT = new Tab(TabRegistry.ASSISTANT, R.string.app_tab_assistant,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_auto_awesome);
    private static final Tab BUSINESS = new Tab(TabRegistry.BUSINESS, R.string.app_tab_business,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_monitoring);
    private static final Tab MINE = new Tab(TabRegistry.MINE, R.string.app_tab_mine,
            com.zhimu.slmp.base.R.drawable.base_selector_nav_person);

    private BottomNavigationView nav;
    private final List<Tab> tabs = new ArrayList<>();
    @Nullable
    private String layoutKey;
    @Nullable
    private String restoredLayout;
    @NonNull
    private String currentKey = TabRegistry.TODAY;
    private OnBackPressedCallback backToToday;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_activity_main);
        nav = findViewById(R.id.main_bottom_nav);
        InsetsHelper.applyTopPadding(findViewById(R.id.main_container));
        if (savedInstanceState != null) {
            currentKey = savedInstanceState.getString(STATE_TAB, TabRegistry.TODAY);
            restoredLayout = savedInstanceState.getString(STATE_LAYOUT);
        }
        backToToday = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                selectTab(TabRegistry.TODAY);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToToday);
        nav.setOnItemSelectedListener(item -> {
            int index = item.getItemId() - 1;
            if (index >= 0 && index < tabs.size()) {
                showTab(tabs.get(index).key);
            }
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
        });
        CoreInjector.get(this).farmContext().getContext().observe(this, this::onFarmContext);
    }

    /** 由系统栏 insets 交给顶部容器与底部导航各自处理 */
    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }

    @Nullable
    @Override
    public View getSnackbarAnchor() {
        return nav;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TAB, currentKey);
        outState.putString(STATE_LAYOUT, layoutKey);
    }

    private void onFarmContext(@Nullable FarmContextVO ctx) {
        String layout = layoutOf(ctx);
        if (layout.equals(layoutKey)) {
            return;
        }
        boolean keepFragments = layoutKey == null && layout.equals(restoredLayout);
        layoutKey = layout;
        rebuild(layout, keepFragments);
    }

    /** 员工（且养殖场不是待审核）用员工导航，其余按所有者 */
    @NonNull
    private static String layoutOf(@Nullable FarmContextVO ctx) {
        if (ctx != null && FarmContextVO.ROLE_STAFF.equals(ctx.getRole())
                && !FarmContextVO.STATUS_PENDING.equals(ctx.getStatus())) {
            return LAYOUT_STAFF;
        }
        return LAYOUT_OWNER;
    }

    private void rebuild(@NonNull String layout, boolean keepFragments) {
        FragmentManager fm = getSupportFragmentManager();
        if (!keepFragments) {
            FragmentTransaction tx = fm.beginTransaction();
            for (Fragment f : fm.getFragments()) {
                if (f.getTag() != null && f.getTag().startsWith(TAG_PREFIX)) {
                    tx.remove(f);
                }
            }
            tx.commitNowAllowingStateLoss();
        }
        tabs.clear();
        tabs.addAll(LAYOUT_STAFF.equals(layout)
                ? Arrays.asList(TODAY, MY_PENS, ASSISTANT, MINE)
                : Arrays.asList(TODAY, PRODUCTION, ASSISTANT, BUSINESS, MINE));
        Menu menu = nav.getMenu();
        menu.clear();
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            menu.add(Menu.NONE, i + 1, i, t.title).setIcon(t.icon);
        }
        int index = indexOf(currentKey);
        if (index < 0) {
            index = 0;
        }
        // 只更新选中状态，不触发监听，再显示对应标签页
        menu.findItem(index + 1).setChecked(true);
        showTab(tabs.get(index).key);
    }

    private void showTab(@NonNull String key) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            return;
        }
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        Fragment target = fm.findFragmentByTag(TAG_PREFIX + key);
        for (Fragment f : fm.getFragments()) {
            if (f != target && f.getTag() != null && f.getTag().startsWith(TAG_PREFIX) && !f.isHidden()) {
                tx.hide(f);
                tx.setMaxLifecycle(f, Lifecycle.State.STARTED);
            }
        }
        if (target == null) {
            target = createTab(key);
            tx.add(R.id.main_container, target, TAG_PREFIX + key);
        } else {
            tx.show(target);
        }
        tx.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        tx.commitNow();
        currentKey = key;
        backToToday.setEnabled(!TabRegistry.TODAY.equals(key));
    }

    @NonNull
    private Fragment createTab(@NonNull String key) {
        Fragment f = TabRegistry.create(key);
        if (f != null) {
            return f;
        }
        int index = indexOf(key);
        String title = index >= 0 ? getString(tabs.get(index).title) : "";
        return PlaceholderTabFragment.newInstance(title);
    }

    private int indexOf(@NonNull String key) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void selectTab(@NonNull String tabKey) {
        int index = indexOf(tabKey);
        if (index >= 0) {
            nav.setSelectedItemId(index + 1);
        }
    }
}
