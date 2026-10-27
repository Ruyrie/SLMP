package com.zhimu.slmp.today.ui.home;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.util.TodayNavigator;

/** T02 is a panel; this transparent host makes its action usable by other modules. */
public class QuickRegisterActivity extends BaseActivity {

    private static final String STATE_FARM_ID = "quick_register_farm_id";
    private TodayViewModel vm;
    private long farmId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        farmId = savedInstanceState == null ? 0 : savedInstanceState.getLong(STATE_FARM_ID);
        setContentView(new FrameLayout(this));
        vm = obtainViewModel(TodayInjector.factory(), TodayViewModel.class);
        observeBase(vm);
        vm.getNavEvent().observe(this, target -> {
            if (target != null && !isFinishing() && allowRegistration(
                    CoreInjector.get(this).farmContext().current())) {
                TodayNavigator.open(this, target);
                finish();
            }
        });
        CoreInjector.get(this).farmContext().getContext().observe(this, context -> {
            if (isFinishing() || !allowRegistration(context)) return;
            if (farmId > 0 && farmId != context.getFarmId()) {
                finish();
                GlobalEvents.postMessage(getString(R.string.today_quick_farm_changed));
                return;
            }
            farmId = context.getFarmId();
            vm.bindQuickRegisterContext(context);
            if (getSupportFragmentManager().findFragmentByTag(QuickRegisterSheet.TAG) == null
                    && !getSupportFragmentManager().isStateSaved()) {
                QuickRegisterSheet.show(getSupportFragmentManager());
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putLong(STATE_FARM_ID, farmId);
        super.onSaveInstanceState(state);
    }

    private boolean allowRegistration(@Nullable FarmContextVO context) {
        int reason = QuickRegisterAccess.blockedReason(context);
        if (reason == 0) return true;
        String tip = context == null ? null : context.getRestrictTip();
        finish();
        GlobalEvents.postMessage(tip == null || tip.isEmpty() ? getString(reason) : tip);
        return false;
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }
}
