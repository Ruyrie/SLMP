package com.zhimu.slmp;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.snackbar.Snackbar;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.SnackbarHelper;
import com.zhimu.slmp.base.widget.BottomActionBar;
import com.zhimu.slmp.common.ui.status.StatusActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** 无网络/业务写入：重现弹层收起后底栏位置变化，提示条必须随底栏移动。 */
@RunWith(AndroidJUnit4.class)
public class SnackbarAnchorRegressionTest {
    @Test public void snackbarFollowsBottomBarAfterKeyboardSizedLayoutChange() {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        StatusActivity activity = (StatusActivity) inst.startActivitySync(new Intent(
                inst.getTargetContext(), StatusActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<BottomActionBar> anchor = new AtomicReference<>();
        AtomicReference<Snackbar> snackbar = new AtomicReference<>();
        try {
            inst.runOnMainSync(() -> {
                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);
                TextView records = new TextView(activity);
                records.setText("收款记录");
                content.addView(records, new LinearLayout.LayoutParams(-1, 0, 1));
                BottomActionBar bar = new BottomActionBar(activity);
                bar.setSecondary("查看批次核算", 0);
                content.addView(bar, new LinearLayout.LayoutParams(-1, dp(activity, 300)));
                activity.setContentView(content);
                anchor.set(bar);
            });
            inst.waitForIdleSync();
            inst.runOnMainSync(() -> {
                Snackbar bar = SnackbarHelper.show(activity, UiMessage.of("已登记收款，出栏单已收齐"));
                assertNotNull(bar);
                bar.setDuration(10000);
                snackbar.set(bar);
            });
            await(inst, () -> snackbar.get().isShown());
            inst.runOnMainSync(() -> {
                android.view.ViewGroup.LayoutParams params = anchor.get().getLayoutParams();
                params.height = dp(activity, 72);
                anchor.get().setLayoutParams(params);
            });
            await(inst, () -> {
                int[] a = new int[2], b = new int[2];
                anchor.get().getLocationOnScreen(a);
                View view = snackbar.get().getView();
                view.getLocationOnScreen(b);
                int gap = a[1] - b[1] - view.getHeight();
                return gap >= 0 && gap <= dp(activity, 32);
            });
        } finally {
            inst.runOnMainSync(() -> { SnackbarHelper.dismissCurrent(); activity.finish(); });
        }
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void await(Instrumentation inst, java.util.function.BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        java.util.concurrent.atomic.AtomicBoolean passed = new java.util.concurrent.atomic.AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            inst.runOnMainSync(() -> passed.set(condition.getAsBoolean()));
            if (passed.get()) return;
            SystemClock.sleep(50);
        }
        fail("底栏收起后，提示条没有移动到操作栏上方");
    }
}
