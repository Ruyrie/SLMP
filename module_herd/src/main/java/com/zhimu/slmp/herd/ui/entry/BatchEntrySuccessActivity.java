package com.zhimu.slmp.herd.ui.entry;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityEntrySuccessBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.util.LabelPrintAdapter;

/**
 * L09 入栏成功：替换 L08 进入返回栈；close 与系统返回键回到进入 L08 之前的页面（RESULT_OK 让 L03 刷新列表）。
 */
public class BatchEntrySuccessActivity extends BaseActivity {

    private HerdActivityEntrySuccessBinding binding;
    private BatchEntrySuccessViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_entry_success);
        vm = obtainViewModel(HerdInjector.factory(), BatchEntrySuccessViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        setResult(RESULT_OK);

        binding.herdToolbar.setNavigationOnClickListener(v -> finish());
        MenuItem view = binding.herdToolbar.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.herd_success_view);
        view.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        binding.herdToolbar.setOnMenuItemClickListener(item -> {
            vm.openArchive();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        vm.getQrBitmap().observe(this, binding.herdQrImage::setImageBitmap);
        vm.getEntryText().observe(this, binding.herdKvEntry::setValue);
        vm.getResult().observe(this, r -> {
            if (r != null) {
                binding.herdRowPen.setTitle(getString(R.string.herd_success_pen_now, r.getPenName()));
            }
        });
        vm.getPenStockText().observe(this, binding.herdRowPen::setTrailingText);
        vm.getOverTag().observe(this, tag -> binding.herdRowPen.setStatusTag(tag, StatusTagView.WARNING, 0));
        vm.getSaving().observe(this, s -> {
            if (binding.herdActionBar.getSecondaryButton() != null) {
                com.zhimu.slmp.base.widget.ButtonLoading.setLoading(binding.herdActionBar.getSecondaryButton(),
                        Boolean.TRUE.equals(s));
            }
        });
        binding.herdActionBar.setOnSecondaryClickListener(v -> vm.saveLabel());
        binding.herdActionBar.setOnPrimaryClickListener(v -> vm.printLabel());
        vm.getPrintEvent().observe(this, bmp -> {
            String no = vm.getResult().getValue() == null ? "" : vm.getResult().getValue().getBatchNo();
            LabelPrintAdapter.print(this, bmp, no);
        });
        vm.getNavEvent().observe(this, t -> HerdNav.open(this, null, t));
    }
}
