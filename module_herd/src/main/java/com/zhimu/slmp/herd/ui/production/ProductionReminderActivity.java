package com.zhimu.slmp.herd.ui.production;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityProductionReminderBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.ui.common.HerdNav;

/**
 * 生产提醒页：L01 生产页“生产提醒 N 项”卡进入（N = 0 时也可进入，显示空状态）。列出全部生产提醒，
 * 点击行为与原生产页列表相同；下拉刷新，从详情页返回时自动刷新。
 */
public class ProductionReminderActivity extends BaseActivity {

    private HerdActivityProductionReminderBinding binding;
    private ProductionReminderViewModel vm;
    private boolean resumedOnce;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HerdActivityProductionReminderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(HerdInjector.factory(), ProductionReminderViewModel.class);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.herdToolbar, getString(R.string.herd_production_reminder));
        TopAppBarHelper.bindLiftOnScroll(binding.herdAppBar, binding.herdReminders);

        ReminderAdapter adapter = new ReminderAdapter(vm::onReminderClick);
        binding.herdReminders.setLayoutManager(new LinearLayoutManager(this));
        binding.herdReminders.addItemDecoration(new GroupedListDecoration(this));
        binding.herdReminders.setAdapter(adapter);
        vm.getReminders().observe(this, adapter::submitList);

        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.herdState::render);
        vm.getRefreshing().observe(this, r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(vm::refresh);
        vm.getNavEvent().observe(this, t -> HerdNav.open(this, null, t));

        vm.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从诊断详情、用药档案等返回后，提醒可能已处理，重新取一次
        if (resumedOnce) {
            vm.refresh();
        }
        resumedOnce = true;
    }
}
