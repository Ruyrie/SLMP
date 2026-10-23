package com.zhimu.slmp.stock.ui.feedarchive;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityFeedArchiveBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;

import java.util.ArrayList;
import java.util.List;

/**
 * M02 饲料档案（新增 / 编辑）。保存成功带文案 RESULT_OK 返回 M01。
 */
public class FeedArchiveActivity extends BaseActivity {

    private static final String REQ_TYPE = "stock_feed_type";
    private static final String REQ_CATEGORY = "stock_feed_category";
    private static final String REQ_DISCARD = "stock_feed_archive_discard";
    private static final String REQ_CONFLICT = "stock_feed_archive_conflict";

    private StockActivityFeedArchiveBinding binding;
    private FeedArchiveViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_feed_archive);
        vm = obtainViewModel(StockInjector.factory(), FeedArchiveViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        boolean edit = vm.isEdit();
        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar,
                getString(edit ? R.string.stock_feed_archive_title : R.string.stock_feed_add));
        binding.stockTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        if (edit) {
            binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_history);
            TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
            binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
                vm.onChangeLog();
                return true;
            });
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });

        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> {
            binding.stockState.render(s);
            binding.stockActionBar.setVisibility(s != null && s.is(com.zhimu.slmp.base.mvvm.UiState.Status.CONTENT)
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        ClickThrottle.setOnClickListener(binding.stockTypeInput, v -> showTypes());
        binding.stockTypeLayout.setEndIconOnClickListener(v -> showTypes());
        ClickThrottle.setOnClickListener(binding.stockCategoryInput, v -> showCategories());
        binding.stockCategoryLayout.setEndIconOnClickListener(v -> showCategories());
        binding.stockChangeLog.setOnClickListener(v -> vm.onChangeLog());
        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));

        getSupportFragmentManager().setFragmentResultListener(REQ_TYPE, this,
                (k, r) -> vm.onFeedTypePicked(OptionSheetDialogFragment.getValue(r)));
        getSupportFragmentManager().setFragmentResultListener(REQ_CATEGORY, this, (k, r) -> {
            String v = OptionSheetDialogFragment.getValue(r);
            vm.onCategoryPicked(v == null ? null : Long.parseLong(v));
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFLICT, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onConflictRefresh();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
        vm.getSaved().observe(this, msg -> StockNav.finishWithMessage(this, UiMessage.of(msg)));
        vm.getConflict().observe(this, msg -> ConfirmDialogFragment.builder(REQ_CONFLICT)
                .title(getString(R.string.stock_conflict_title))
                .positive(getString(R.string.stock_action_refresh))
                .negative(getString(R.string.stock_action_cancel))
                .build().showSafely(getSupportFragmentManager()));
        vm.getOpenChangeLog().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                StockEnums.BIZ_FEED, id, null));
    }

    private void showTypes() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<DictItemVO> list = vm.getFeedTypes().getValue();
        if (list != null) {
            for (DictItemVO t : list) {
                options.add(new OptionSheetDialogFragment.Option(t.getCode(), t.getName(), t.getDescription()));
            }
        }
        OptionSheetDialogFragment.builder(REQ_TYPE)
                .title(getString(R.string.stock_field_feed_type))
                .options(options)
                .selected(vm.getFeedTypeCode().getValue())
                .build().showSafely(getSupportFragmentManager());
    }

    private void showCategories() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<CategoryVO> list = vm.getCategories().getValue();
        if (list != null) {
            for (CategoryVO c : list) {
                options.add(new OptionSheetDialogFragment.Option(String.valueOf(c.getId()), c.getName(),
                        c.getSpeciesText()));
            }
        }
        Long current = vm.getCategoryId().getValue();
        OptionSheetDialogFragment.builder(REQ_CATEGORY)
                .title(getString(R.string.stock_field_category))
                .options(options)
                .selected(current == null ? null : String.valueOf(current))
                .build().showSafely(getSupportFragmentManager());
    }
}
