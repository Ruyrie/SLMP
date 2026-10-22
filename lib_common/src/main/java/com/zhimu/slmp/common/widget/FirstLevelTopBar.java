package com.zhimu.slmp.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.AvatarView;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TabRegistry;

/**
 * 一级页顶部栏（02 号《UI设计规范》2.2、7.1、7.2 节）：T01 今日首页、L01 生产页 / L02 我的栏舍、K01 助手、E01 经营、A07 我的。
 * <p>
 * 高 64 dp，surface 底（滚动后 surface-container，不加阴影）。左侧养殖场切换器：养殖场名 title-l 22 sp（单行省略）+
 * {@code arrow_drop_down}，点击打开 F01 切换养殖场面板（{@link IFarmContextService#showSwitcher}，服务未注册时不响应）；
 * “全部养殖场”视角下显示“全部养殖场”。右侧依次为扫码 {@code qr_code_scanner}（进入 L07）、消息 {@code notifications}
 * （未读角标：16 dp error 底，超过 99 显示 99+，进入 T06）、32 dp 头像（姓名首字，宿主实现 {@link MainTabHost} 时切到“我的”）。
 * 三者中心间距 48 dp，头像右缘距屏幕 16 dp。
 * <pre>
 * &lt;com.zhimu.slmp.common.widget.FirstLevelTopBar
 *     android:id="@+id/top_bar"
 *     android:layout_width="match_parent"
 *     android:layout_height="@dimen/base_top_bar_height" /&gt;
 *
 * binding.topBar.bind(requireActivity(), getViewLifecycleOwner());
 * binding.topBar.bindLiftOnScroll(binding.recyclerView);
 * </pre>
 * XML：{@code app:topBarShowScan}（默认 true）、{@code app:topBarAllowAllFarms}（默认 false）。
 */
public class FirstLevelTopBar extends LinearLayout {

    private final View switcher;
    private final TextView farmName;
    private final ImageView farmArrow;
    private final View scan;
    private final View messages;
    private final ImageView messagesIcon;
    private final View avatarCell;
    private final AvatarView avatar;

    @Nullable
    private BadgeDrawable badge;
    @Nullable
    private FragmentActivity host;
    private boolean allowAllFarms;
    private boolean inAllFarms;
    @Nullable
    private CharSequence currentFarmName;
    private int unread;
    private boolean scrolled;

    public FirstLevelTopBar(@NonNull Context context) {
        this(context, null);
    }

    public FirstLevelTopBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setMinimumHeight(getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_top_bar_height));
        setPaddingRelative(0, 0, getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_2), 0);
        LayoutInflater.from(context).inflate(R.layout.common_ui_view_first_level_top_bar, this, true);
        switcher = findViewById(R.id.common_ui_farm_switcher);
        farmName = findViewById(R.id.common_ui_farm_name);
        farmArrow = findViewById(R.id.common_ui_farm_arrow);
        scan = findViewById(R.id.common_ui_scan);
        messages = findViewById(R.id.common_ui_messages);
        messagesIcon = findViewById(R.id.common_ui_messages_icon);
        avatarCell = findViewById(R.id.common_ui_avatar_cell);
        avatar = findViewById(R.id.common_ui_avatar);

        boolean showScan = true;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FirstLevelTopBar);
            showScan = a.getBoolean(R.styleable.FirstLevelTopBar_topBarShowScan, true);
            allowAllFarms = a.getBoolean(R.styleable.FirstLevelTopBar_topBarAllowAllFarms, false);
            a.recycle();
        }
        setScanVisible(showScan);
        applyBackground();

        ClickThrottle.setOnClickListener(switcher, v -> openSwitcher());
        ClickThrottle.setOnClickListener(scan, v -> Router.open(getContext(), RouterPath.L07));
        ClickThrottle.setOnClickListener(messages, v -> Router.open(getContext(), RouterPath.T06));
        ClickThrottle.setOnClickListener(avatarCell, v -> {
            if (host instanceof MainTabHost) {
                ((MainTabHost) host).selectTab(TabRegistry.MINE);
            }
        });
    }

    /**
     * 订阅当前养殖场、消息未读数与当前用户。
     *
     * @param host  页面所在的 Activity（打开切换面板、判断是否为 MainTabHost）
     * @param owner 生命周期（Fragment 中传 getViewLifecycleOwner()）
     */
    public void bind(@NonNull FragmentActivity host, @NonNull LifecycleOwner owner) {
        this.host = host;
        CoreComponent core = CoreInjector.get(host);
        core.farmContext().getContext().observe(owner, this::setFarm);
        core.sessionManager().getUser().observe(owner, this::setUser);
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.unreadCount().observe(owner, count -> setUnreadCount(count == null ? 0 : count));
        } else {
            setUnreadCount(0);
        }
    }

    // ---------------- 养殖场切换器 ----------------

    /** 显示当前养殖场名（“全部养殖场”视角下显示“全部养殖场”） */
    public void setFarm(@Nullable FarmContextVO farm) {
        currentFarmName = farm == null ? null : farm.getName();
        renderFarmName();
    }

    /**
     * 设置“全部养殖场”相关状态（只有 T03、T06、E02、E13 提供“全部养殖场”视角）。
     *
     * @param allowAllFarms 打开面板时“全部养殖场”是否可选
     * @param inAllFarms    当前是否处于“全部养殖场”视角（切换器显示“全部养殖场”）
     */
    public void setAllFarmsMode(boolean allowAllFarms, boolean inAllFarms) {
        this.allowAllFarms = allowAllFarms;
        this.inAllFarms = inAllFarms;
        renderFarmName();
    }

    private void renderFarmName() {
        CharSequence name = inAllFarms ? getContext().getString(R.string.common_ui_all_farms) : currentFarmName;
        farmName.setText(name);
        switcher.setContentDescription(getContext().getString(R.string.common_ui_cd_switch_farm,
                name == null ? "" : name));
        boolean hasName = !TextUtils.isEmpty(name);
        switcher.setVisibility(hasName ? VISIBLE : INVISIBLE);
    }

    private void openSwitcher() {
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (service == null || host == null) {
            return;
        }
        service.showSwitcher(host, allowAllFarms, inAllFarms);
    }

    // ---------------- 右侧入口 ----------------

    /** 是否显示扫码按钮（默认显示） */
    public void setScanVisible(boolean visible) {
        scan.setVisibility(visible ? VISIBLE : GONE);
    }

    /** 未读消息数；0 时不显示角标，超过 99 显示“99+” */
    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    public void setUnreadCount(int count) {
        unread = Math.max(0, count);
        if (badge == null) {
            badge = BadgeDrawable.create(getContext());
            badge.setMaxNumber(99);
            if (messagesIcon.isLaidOut()) {
                BadgeUtils.attachBadgeDrawable(badge, messagesIcon);
            } else {
                messagesIcon.post(() -> {
                    if (badge != null) {
                        BadgeUtils.attachBadgeDrawable(badge, messagesIcon);
                    }
                });
            }
        }
        if (unread > 0) {
            badge.setNumber(unread);
            badge.setVisible(true);
            messages.setContentDescription(getContext().getString(R.string.common_ui_cd_messages_unread, unread));
        } else {
            badge.clearNumber();
            badge.setVisible(false);
            messages.setContentDescription(getContext().getString(R.string.common_ui_cd_messages));
        }
    }

    public int getUnreadCount() {
        return unread;
    }

    /** 头像：显示 name_initial，没有时取姓名首字 */
    public void setUser(@Nullable AccountUserVO user) {
        if (user == null) {
            avatar.setInitial(null);
            return;
        }
        String initial = user.getNameInitial();
        if (TextUtils.isEmpty(initial)) {
            initial = AvatarView.initialOf(user.getName());
        }
        avatar.setInitial(initial);
    }

    // ---------------- 滚动变色 ----------------

    /** 内容向上滚动后底色由 surface 变为 surface-container（不加阴影） */
    public void setScrolled(boolean scrolled) {
        if (this.scrolled != scrolled) {
            this.scrolled = scrolled;
            applyBackground();
        }
    }

    /** 按滚动视图（RecyclerView、NestedScrollView 等）能否继续向上滚动自动切换底色 */
    public void bindLiftOnScroll(@NonNull View scrollingView) {
        setScrolled(scrollingView.canScrollVertically(-1));
        scrollingView.setOnScrollChangeListener((v, x, y, oldX, oldY) -> setScrolled(v.canScrollVertically(-1)));
    }

    private void applyBackground() {
        setBackgroundColor(MaterialColors.getColor(this, scrolled
                ? com.google.android.material.R.attr.colorSurfaceContainer
                : com.google.android.material.R.attr.colorSurface));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 养殖场名过长时省略，保证下拉箭头与右侧入口可见
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0) {
            int used = getPaddingStart() + getPaddingEnd();
            used += widthIfShown(scan) + widthIfShown(messages) + widthIfShown(avatarCell);
            android.view.ViewGroup.MarginLayoutParams slp =
                    (android.view.ViewGroup.MarginLayoutParams) switcher.getLayoutParams();
            used += slp.getMarginStart() + switcher.getPaddingStart() + switcher.getPaddingEnd()
                    + farmArrow.getLayoutParams().width;
            farmName.setMaxWidth(Math.max(0, width - used));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static int widthIfShown(@NonNull View v) {
        return v.getVisibility() == GONE ? 0 : v.getLayoutParams().width;
    }
}
