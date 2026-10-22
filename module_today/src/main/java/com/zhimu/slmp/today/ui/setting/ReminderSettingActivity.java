package com.zhimu.slmp.today.ui.setting;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityReminderSettingBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.request.ReminderSettingPatchRequest;
import com.zhimu.slmp.today.push.NotificationChannels;
import com.zhimu.slmp.today.util.Texts;

/**
 * T05 提醒设置：开关与时间修改即时保存；每次 onResume 检测系统通知总开关与 zm_todo 渠道，关闭时显示横幅与“去开启”。
 * 本页设置对账号生效，覆盖加入的全部养殖场。
 */
public class ReminderSettingActivity extends BaseActivity {

    private ReminderSettingViewModel vm;
    private TodayActivityReminderSettingBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), ReminderSettingViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.today_activity_reminder_setting);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.today_setting_title));
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.scroll);
        binding.stateLayout.setOnRetryListener(v -> vm.retry());
        binding.blocked.setAction(getString(R.string.today_setting_open), v -> vm.onOpenSettingsClick());

        vm.getOpenTimePicker().observe(this, field -> {
            if (field != null) {
                TimePickerSheet.show(getSupportFragmentManager(), field, titleOf(field));
            }
        });
        vm.getOpenNotificationSettings().observe(this, this::openNotificationSettings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.onPermissionChecked(NotificationManagerCompat.from(this).areNotificationsEnabled(),
                NotificationChannels.isTodoChannelOn(this));
    }

    private CharSequence titleOf(String field) {
        switch (field) {
            case ReminderSettingPatchRequest.FIELD_QUIET_START:
                return getString(R.string.today_setting_quiet_start);
            case ReminderSettingPatchRequest.FIELD_QUIET_END:
                return getString(R.string.today_setting_quiet_end);
            case ReminderSettingPatchRequest.FIELD_DIGEST_TIME:
            default:
                return getString(R.string.today_setting_digest_time);
        }
    }

    /** 为空打开应用通知设置，为 zm_todo 打开该渠道设置 */
    private void openNotificationSettings(@Nullable String channel) {
        Intent intent;
        if (Texts.isEmpty(channel)) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channel);
        }
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null)));
        }
    }
}
