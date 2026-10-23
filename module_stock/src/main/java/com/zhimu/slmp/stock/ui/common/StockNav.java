package com.zhimu.slmp.stock.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.router.RouterTable;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.ui.record.StockRecordActivity;
import com.zhimu.slmp.stock.util.FlowRouter;

import java.util.Map;

/** 页面跳转与“返回来源页并提示”。 */
public final class StockNav {

    private StockNav() {
    }

    /** setResult(RESULT_OK) 并关闭；提示用全局提示，在下一个前台页面显示 */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable UiMessage message) {
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
        if (message != null) {
            GlobalEvents.postMessage(message);
        }
    }

    /** 打开页面；有 launcher 时以结果方式打开（返回 RESULT_OK 后刷新） */
    public static void open(@NonNull Activity activity, @Nullable ActivityResultLauncher<Intent> launcher,
                            @NonNull String action, @Nullable Bundle extras) {
        if (launcher != null) {
            Router.openForResult(activity, launcher, action, extras);
        } else {
            Router.open(activity, action, extras);
        }
    }

    /** 点流水：按 FlowRouter 的目标打开 */
    public static void openFlow(@NonNull Activity activity, @Nullable ActivityResultLauncher<Intent> launcher,
                                @Nullable StockFlowItemVO flow) {
        FlowRouter.Target t = FlowRouter.route(flow);
        if (t == null) {
            return;
        }
        Bundle b = toBundle(t.args);
        if (FlowRouter.PAGE_RECORD.equals(t.page)) {
            Intent i = new Intent(activity, StockRecordActivity.class).putExtras(b);
            if (launcher != null) {
                launcher.launch(i);
            } else {
                activity.startActivity(i);
            }
            return;
        }
        String action = RouterTable.actionOf(t.page);
        if (action != null) {
            open(activity, launcher, action, b);
        }
    }

    @NonNull
    public static Bundle toBundle(@NonNull Map<String, Object> args) {
        Bundle b = new Bundle();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Long) {
                b.putLong(e.getKey(), (Long) v);
            } else if (v instanceof Integer) {
                b.putInt(e.getKey(), (Integer) v);
            } else if (v instanceof Boolean) {
                b.putBoolean(e.getKey(), (Boolean) v);
            } else if (v != null) {
                b.putString(e.getKey(), String.valueOf(v));
            }
        }
        return b;
    }

    @NonNull
    public static Bundle idArgs(@NonNull String key, @Nullable Long id) {
        Bundle b = new Bundle();
        if (id != null && id > 0) {
            b.putLong(key, id);
        }
        return b;
    }

    /** U01 提交修改申请 */
    @NonNull
    public static Bundle changeArgs(@NonNull String bizType, long bizId) {
        Bundle b = new Bundle();
        b.putString(RouterPath.EXTRA_BIZ_TYPE, bizType);
        b.putLong(RouterPath.EXTRA_BIZ_ID, bizId);
        return b;
    }

    /** U02 我的修改申请（定位到某条） */
    @NonNull
    public static Bundle requestArgs(@Nullable Long requestId) {
        return idArgs(RouterPath.EXTRA_REQUEST_ID, requestId);
    }

    /** M11 预选药品与批号 */
    @NonNull
    public static Bundle outboundArgs(@Nullable Long drugId, @Nullable Long lotId) {
        Bundle b = idArgs(StockExtras.EXTRA_DRUG_ID, drugId);
        if (lotId != null && lotId > 0) {
            b.putLong(StockExtras.EXTRA_LOT_ID, lotId);
        }
        return b;
    }
}
