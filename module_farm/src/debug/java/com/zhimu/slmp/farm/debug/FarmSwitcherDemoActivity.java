package com.zhimu.slmp.farm.debug;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.farm.ui.switcher.FarmSwitchSheet;

/**
 * F01 切换养殖场演示（只在 debug 包中）：在本页上打开底部面板，显示当前养殖场与“全部养殖场”回传结果。
 */
public class FarmSwitcherDemoActivity extends BaseActivity {

    private TextView status;
    private boolean inAllFarms;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 3, pad, pad);
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(status);
        root.addView(button("打开 F01（当前页不提供全部养殖场）", false));
        root.addView(button("打开 F01（日历等页面，可选全部养殖场）", true));
        setContentView(root);

        CoreInjector.get(this).farmContext().getContext().observe(this, c -> render());
        getSupportFragmentManager().setFragmentResultListener(FarmSwitchSheet.REQUEST_KEY, this, (key, result) -> {
            inAllFarms = result.getBoolean(FarmSwitchSheet.RESULT_ALL_FARMS);
            render();
        });
    }

    private MaterialButton button(String text, boolean allowAll) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setOnClickListener(v -> {
            IFarmContextService service = ServiceHub.get(IFarmContextService.class);
            if (service != null) {
                service.showSwitcher(this, allowAll, allowAll && inAllFarms);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(lp);
        return b;
    }

    private void render() {
        FarmContextVO c = CoreInjector.get(this).farmContext().current();
        String farm = c == null ? "没有养殖场" : c.getName() + " · " + c.getRoleText() + " · " + c.getStatusText();
        status.setText("当前养殖场：" + farm + (inAllFarms ? "\n当前为“全部养殖场”视角" : ""));
    }
}
