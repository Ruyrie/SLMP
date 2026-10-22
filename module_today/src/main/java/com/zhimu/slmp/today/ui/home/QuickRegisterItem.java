package com.zhimu.slmp.today.ui.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.zhimu.slmp.base.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 快捷登记面板的静态配置（T02）：名称、图标、去向页面编号。“物资入库”没有去向，点开后在面板内选择饲料入库 M03 或兽药与疫苗入库 M08。
 */
public final class QuickRegisterItem {

    public static final String GROUP_WORK = "WORK";
    public static final String GROUP_OWNER = "OWNER";
    /** 物资入库（二级选择） */
    public static final String ROUTE_STOCK_IN = "STOCK_IN";

    @StringRes
    private final int name;
    @DrawableRes
    private final int icon;
    @NonNull
    private final String route;
    @NonNull
    private final String group;

    public QuickRegisterItem(@StringRes int name, @DrawableRes int icon, @NonNull String route, @NonNull String group) {
        this.name = name;
        this.icon = icon;
        this.route = route;
        this.group = group;
    }

    /** “作业”组 8 项 */
    public static final List<QuickRegisterItem> WORK = Collections.unmodifiableList(Arrays.asList(
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_inspection, R.drawable.ic_fact_check, "I02", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_feeding, R.drawable.ic_rice_bowl, "G02", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_weighing, R.drawable.ic_scale, "G05", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_death, R.drawable.ic_heart_minus, "L11", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_vaccine, R.drawable.ic_vaccines, "D03", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_medication, R.drawable.ic_medication, "D04", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_disinfection, R.drawable.ic_sanitizer, "D08", GROUP_WORK),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_abnormal, R.drawable.ic_report, "I05", GROUP_WORK)));

    /** “养殖户”组 4 项（员工不显示） */
    public static final List<QuickRegisterItem> OWNER = Collections.unmodifiableList(Arrays.asList(
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_batch_in, R.drawable.ic_add_box, "L08", GROUP_OWNER),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_stock_in, R.drawable.ic_inventory_2, ROUTE_STOCK_IN, GROUP_OWNER),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_bookkeeping, R.drawable.ic_payments, "E03", GROUP_OWNER),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_sale, R.drawable.ic_local_shipping, "E06", GROUP_OWNER)));

    /** 物资入库的二级选择 */
    public static final List<QuickRegisterItem> STOCK_IN = Collections.unmodifiableList(Arrays.asList(
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_feed_in, R.drawable.ic_inventory_2, "M03", GROUP_OWNER),
            new QuickRegisterItem(com.zhimu.slmp.today.R.string.today_quick_drug_in, R.drawable.ic_vaccines, "M08", GROUP_OWNER)));

    /** 按角色组合：员工只有“作业”组 */
    @NonNull
    public static List<QuickRegisterItem> forRole(boolean managerView) {
        List<QuickRegisterItem> list = new ArrayList<>(WORK);
        if (managerView) {
            list.addAll(OWNER);
        }
        return Collections.unmodifiableList(list);
    }

    @StringRes
    public int getName() {
        return name;
    }

    @DrawableRes
    public int getIcon() {
        return icon;
    }

    @NonNull
    public String getRoute() {
        return route;
    }

    @NonNull
    public String getGroup() {
        return group;
    }

    public boolean isOwnerGroup() {
        return GROUP_OWNER.equals(group);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof QuickRegisterItem)) {
            return false;
        }
        QuickRegisterItem q = (QuickRegisterItem) o;
        return name == q.name && route.equals(q.route);
    }

    @Override
    public int hashCode() {
        return 31 * name + route.hashCode();
    }
}
