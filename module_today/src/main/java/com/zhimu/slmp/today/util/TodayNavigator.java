package com.zhimu.slmp.today.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.router.RouterTable;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 页面一侧打开 {@link NavTarget}：页面编号经 RouterTable 转为 action，参数转为 Bundle（_id 结尾的数字参数按 long 传入，
 * 与 RouterPath 参数键的类型一致）；跨场切换的提示放在 EXTRA_SWITCH_TIP 中，由目标页的 BaseActivity 显示。目标页面不存在时 Router 提示“该功能正在开发中”。
 */
public final class TodayNavigator {

    /** 跨场切换后的提示（目标页可读取；本组件同时用全局提示显示） */
    public static final String EXTRA_SWITCH_TIP = RouterPath.EXTRA_SWITCH_TIP;

    private static final Pattern LONG = Pattern.compile("-?\\d{1,18}");

    private TodayNavigator() {
    }

    public static boolean open(@NonNull Context context, @NonNull NavTarget target) {
        return open(context, target, null);
    }

    /**
     * @param launcher 不为空时以 Activity Result 打开（T04、T07 返回后刷新）
     */
    public static boolean open(@NonNull Context context, @NonNull NavTarget target,
                               @Nullable ActivityResultLauncher<Intent> launcher) {
        if (NavTarget.ROUTE_BACK.equals(target.getRoute())) {
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
            return true;
        }
        if ("F01".equals(target.getRoute())) {
            return openFarmSwitcher(context);
        }
        String action = actionOf(context, target.getRoute());
        if (action == null) {
            GlobalEvents.postMessage(Router.MSG_NOT_READY);
            return false;
        }
        Bundle extras = toBundle(target.getParams());
        if (target.getSwitchTip() != null) {
            extras.putString(EXTRA_SWITCH_TIP, target.getSwitchTip());
        }
        boolean opened = launcher != null
                ? Router.openForResult(context, launcher, action, extras)
                : Router.open(context, action, extras);
        return opened;
    }

    /**
     * F01 切换养殖场是底部面板，没有独立页面：壳工程中回到 MAIN（“今日”标签）并弹出面板；
     * 单独运行（没有 MAIN）时在当前页弹出。
     */
    static boolean openFarmSwitcher(@NonNull Context context) {
        if (Router.canOpen(context, RouterPath.MAIN)) {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_MAIN_TAB, TabRegistry.TODAY);
            extras.putBoolean(RouterPath.EXTRA_OPEN_FARM_SWITCHER, true);
            context.startActivity(Router.intent(context, RouterPath.MAIN, extras)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return true;
        }
        IFarmContextService farm = ServiceHub.get(IFarmContextService.class);
        if (farm != null && context instanceof FragmentActivity) {
            farm.showSwitcher((FragmentActivity) context, false, false);
            return true;
        }
        GlobalEvents.postMessage(Router.MSG_NOT_READY);
        return false;
    }

    /** T01 是主页的“今日”标签：壳工程中打开 MAIN，单独运行时打开调试宿主页 */
    @Nullable
    public static String actionOf(@NonNull Context context, @NonNull String route) {
        if ("T01".equals(route) && Router.canOpen(context, RouterPath.MAIN)) {
            return RouterPath.MAIN;
        }
        return RouterTable.actionOf(route);
    }

    @NonNull
    public static Bundle toBundle(@NonNull Map<String, String> params) {
        Bundle b = new Bundle();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (value == null) {
                continue;
            }
            if (key.endsWith("_id") && LONG.matcher(value).matches()) {
                b.putLong(key, Long.parseLong(value));
            } else if (RouterPath.EXTRA_ERROR_CODE.equals(key) && LONG.matcher(value).matches()) {
                b.putInt(key, Integer.parseInt(value));
            } else {
                b.putString(key, value);
            }
        }
        return b;
    }
}
