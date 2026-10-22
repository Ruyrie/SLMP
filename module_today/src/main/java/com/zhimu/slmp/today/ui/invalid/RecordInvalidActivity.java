package com.zhimu.slmp.today.ui.invalid;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityRecordInvalidBinding;
import com.zhimu.slmp.today.di.TodayInjector;

/**
 * T08 关联记录已失效：消息关联的批次或记录已被删除时进入的提示页。“返回消息中心”回到 T06 并清除中间页。
 */
public class RecordInvalidActivity extends BaseActivity {

    private RecordInvalidViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), RecordInvalidViewModel.class);
        TodayActivityRecordInvalidBinding binding = DataBindingUtil.setContentView(this, R.layout.today_activity_record_invalid);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, null);
        binding.state.icon.setImageResource(com.zhimu.slmp.base.R.drawable.ic_link_off);
        vm.getPageTitle().observe(this, binding.topBar.baseToolbar::setTitle);
        vm.getTitle().observe(this, binding.state.title::setText);
        vm.getDesc().observe(this, binding.state.desc::setText);
        binding.state.action.setOnClickListener(v -> vm.onBackClick());
        vm.getNavEvent().observe(this, target -> backToMessages());
    }

    private void backToMessages() {
        if (Router.canOpen(this, RouterPath.T06)) {
            Intent intent = Router.intent(this, RouterPath.T06, null)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }
}
