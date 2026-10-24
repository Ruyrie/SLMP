package com.zhimu.slmp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 主页：按 FarmContext 角色装配底部导航（01 号文档 2.2 节）。
 * 所有者与场长“今日、生产、助手、经营、我的”；员工“今日、我的栏舍、助手、我的”；没有养殖场或养殖场待审核时按所有者。
 * 标签 Fragment 由 TabRegistry 创建，没有注册的标签显示占位页；角色变化时重建导航。
 * 打开时可带 RouterPath.EXTRA_MAIN_TAB（及 EXTRA_MAIN_TAB_ARGS）切到指定标签，导航装配好后生效。
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
    /** 等待导航装配后切换的标签与参数 */
    @Nullable
    private String pendingTab;
    @Nullable
    private Bundle pendingTabArgs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_activity_main);
        nav = findViewById(R.id.main_bottom_nav);
        applyInsets();
        if (savedInstanceState != null) {
            currentKey = savedInstanceState.getString(STATE_TAB, TabRegistry.TODAY);
            restoredLayout = savedInstanceState.getString(STATE_LAYOUT);
        } else {
            takeTabRequest(getIntent());
            if (pendingTab != null) {
                currentKey = pendingTab;
            }
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

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        takeTabRequest(intent);
        if (!tabs.isEmpty()) {
            applyPendingTab();
        }
    }

    private void takeTabRequest(@Nullable Intent intent) {
        String tab = intent == null ? null : intent.getStringExtra(RouterPath.EXTRA_MAIN_TAB);
        if (tab != null) {
            pendingTab = tab;
            pendingTabArgs = intent.getBundleExtra(RouterPath.EXTRA_MAIN_TAB_ARGS);
        }
    }

    private void applyPendingTab() {
        String tab = pendingTab;
        Bundle args = pendingTabArgs;
        pendingTab = null;
        pendingTabArgs = null;
        if (tab != null) {
            selectTab(tab, args);
        }
    }

    /** 由系统栏 insets 交给顶部容器与底部导航各自处理 */
    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }

    /**
     * 系统栏与软键盘 insets 由主页统一处理：
     * <ul>
     *     <li>一级页容器：顶部避让状态栏；软键盘弹出时底部按键盘高度加内边距（一级页变矮，内容贴在键盘上方）。
     *     已处理的底部从下发给一级页的 insets 中扣除（收起时扣除底部导航已避让的导航栏），一级页不要再自己加；</li>
     *     <li>底部导航：只避让导航栏，替换 BottomNavigationView 自带的处理（它按 systemWindowInsets 加底部内边距，
     *     adjustResize 下包含软键盘高度，会被整体顶到键盘上方）；软键盘弹出时隐藏，收起后恢复
     *     （UI 文档 K01：软键盘弹出时底部导航被键盘遮住）。</li>
     * </ul>
     */
    private void applyInsets() {
        View container = findViewById(R.id.main_container);
        final int pt = container.getPaddingTop();
        final int pb = container.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
            Insets top = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            boolean imeShown = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottom = imeShown
                    ? Math.max(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom, navBottom) : 0;
            v.setPadding(v.getPaddingLeft(), pt + top.top, v.getPaddingRight(), pb + bottom);
            nav.setVisibility(imeShown ? View.GONE : View.VISIBLE);
            return insets.inset(0, 0, 0, imeShown ? bottom : navBottom);
        });
        final int nl = nav.getPaddingLeft();
        final int nr = nav.getPaddingRight();
        final int nb = nav.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(nl + bars.left, v.getPaddingTop(), nr + bars.right, nb + bars.bottom);
            return insets;
        });
    }

    /** 底部导航上方；软键盘弹出、底部导航隐藏时改用页面中可见的锚点，没有则显示在底部（键盘上方） */
    @Nullable
    @Override
    public View getSnackbarAnchor() {
        return nav != null && nav.getVisibility() == View.VISIBLE ? nav : super.getSnackbarAnchor();
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
        applyPendingTab();
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

    @Override
    public void selectTab(@NonNull String tabKey, @Nullable Bundle args) {
        selectTab(tabKey);
        if (args != null && indexOf(tabKey) >= 0) {
            getSupportFragmentManager().setFragmentResult(MainTabHost.argsResultKey(tabKey), args);
        }
    }
}
