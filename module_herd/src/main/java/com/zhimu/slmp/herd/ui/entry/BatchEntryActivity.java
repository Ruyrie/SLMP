package com.zhimu.slmp.herd.ui.entry;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.CategoryBreedVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchEntryBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.OptionAdapter;
import com.zhimu.slmp.network.GsonFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * L08 批次入栏（L03 FAB）/ 修改入栏记录（L06 长按入栏记录）。成功后打开 L09 并替换本页（返回结果转交给打开本页的页面）。
 */
public class BatchEntryActivity extends BaseActivity {

    private static final String REQ_DATE = "herd_entry_date";
    private static final String REQ_DISCARD = "herd_entry_discard";

    private HerdActivityBatchEntryBinding binding;
    private BatchEntryViewModel vm;
    private OptionAdapter penAdapter;
    private OptionAdapter breedAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_batch_entry);
        vm = obtainViewModel(HerdInjector.factory(), BatchEntryViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        binding.herdTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.herdState::render);
        vm.getEditMode().observe(this, edit -> {
            boolean e = Boolean.TRUE.equals(edit);
            binding.herdNotice.setText(getString(e ? R.string.herd_entry_notice_edit : R.string.herd_entry_notice));
            binding.herdActionBar.setPrimaryText(getString(e ? R.string.herd_action_save : R.string.herd_action_submit));
        });

        // 栏舍与品种下拉
        penAdapter = new OptionAdapter(this);
        binding.herdPenInput.setAdapter(penAdapter);
        binding.herdPenInput.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = penAdapter.getItem(position);
            if (item != null && !item.disabled) {
                vm.onPenSelected(item.id);
            }
        });
        vm.getPenOptions().observe(this, this::renderPens);
        vm.getPenId().observe(this, id -> {
            OptionAdapter.Item item = penAdapter.findById(id);
            binding.herdPenInput.setText(item == null ? "" : item.title, false);
        });
        breedAdapter = new OptionAdapter(this);
        binding.herdBreedInput.setAdapter(breedAdapter);
        binding.herdBreedInput.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = breedAdapter.getItem(position);
            if (item != null) {
                vm.onBreedSelected(item.id);
            }
        });
        vm.getBreedTree().observe(this, this::renderBreeds);
        vm.getBreedId().observe(this, id -> {
            OptionAdapter.Item item = breedAdapter.findById(id);
            binding.herdBreedInput.setText(item == null ? "" : item.title, false);
        });

        // 用途与来源连接按钮组
        binding.herdPurposeGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (isChecked) {
                vm.onPurposeChanged(checkedId == R.id.herd_purpose_breeding ? BatchEntryViewModel.PURPOSE_BREEDING
                        : BatchEntryViewModel.PURPOSE_COMMERCIAL);
            }
        });
        vm.getPurpose().observe(this, p -> binding.herdPurposeGroup.check(
                BatchEntryViewModel.PURPOSE_BREEDING.equals(p) ? R.id.herd_purpose_breeding
                        : R.id.herd_purpose_commercial));
        binding.herdSourceGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (isChecked) {
                vm.onSourceChanged(checkedId == R.id.herd_source_self ? BatchEntryViewModel.SOURCE_SELF_BRED
                        : BatchEntryViewModel.SOURCE_PURCHASED);
            }
        });
        vm.getSource().observe(this, s -> binding.herdSourceGroup.check(
                BatchEntryViewModel.SOURCE_SELF_BRED.equals(s) ? R.id.herd_source_self : R.id.herd_source_purchased));
        vm.getUnitText().observe(this, unit -> binding.herdPriceLayout.setSuffixText("元 / " + HerdFormat.unit(unit)));

        // 日期
        ClickThrottle.setOnClickListener(binding.herdDateInput, v -> pickDate());
        binding.herdDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDateSelected(DateTimePicker.getDate(result)));

        // 超容量横幅：展开 250 毫秒，勾选“我已确认，仍要入栏”
        vm.getCapacityBanner().observe(this, text -> {
            boolean show = text != null;
            if (show) {
                binding.herdCapacityBanner.setNotice(getString(R.string.herd_entry_capacity_title), text);
            }
            if (show != (binding.herdCapacityBanner.getVisibility() == View.VISIBLE)) {
                binding.herdCapacityBanner.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    binding.herdCapacityBanner.setAlpha(0f);
                    binding.herdCapacityBanner.animate().alpha(1f).setDuration(250).start();
                }
            }
        });
        binding.herdCapacityBanner.setOnCheckedChangeListener((b, checked) -> {
            if (!Boolean.valueOf(checked).equals(vm.getCapacityChecked().getValue())) {
                vm.getCapacityChecked().setValue(checked);
            }
        });
        vm.getCapacityChecked().observe(this, c -> {
            boolean checked = Boolean.TRUE.equals(c);
            if (binding.herdCapacityBanner.isChecked() != checked) {
                binding.herdCapacityBanner.setChecked(checked);
            }
        });
        vm.getBizBanner().observe(this, text -> binding.herdBizBanner.setText(text));
        vm.getChangeRequestEvent().observe(this, eventId -> binding.herdBizBanner.setAction(
                getString(R.string.herd_entry_change_request), v -> {
                    Bundle extras = new Bundle();
                    extras.putString(RouterPath.EXTRA_BIZ_TYPE, HerdExtras.BIZ_BATCH_ENTRY);
                    if (eventId != null) {
                        extras.putLong(RouterPath.EXTRA_BIZ_ID, eventId);
                    }
                    Router.open(this, RouterPath.U01, extras);
                }));

        binding.herdActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getCanSubmit().observe(this, can -> binding.herdActionBar.setPrimaryEnabled(Boolean.TRUE.equals(can)
                || Boolean.TRUE.equals(vm.getSubmitting().getValue())));
        vm.getSubmitting().observe(this, s -> binding.herdActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFieldErrors().observe(this, errors -> {
            if (errors != null && !errors.isEmpty()) {
                binding.herdScroll.smoothScrollTo(0, 0);
            }
        });
        vm.getSuccessEvent().observe(this, result -> {
            // L09 替换本页进入返回栈，返回结果转交给打开 L08 的页面（L03 刷新列表）
            Intent intent = Router.intent(this, RouterPath.L09, null)
                    .putExtra(HerdExtras.EXTRA_RESULT_JSON, GsonFactory.get().toJson(result))
                    .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            if (Router.canOpen(this, RouterPath.L09)) {
                startActivity(intent);
            }
            finish();
        });
        vm.getFinishEvent().observe(this, v -> {
            setResult(RESULT_OK);
            finish();
        });
        vm.getDiscardEvent().observe(this, v -> ConfirmDialogFragment.builder(REQ_DISCARD)
                .title(getString(R.string.herd_discard_title))
                .message(getString(R.string.herd_discard_message))
                .negative(getString(R.string.herd_action_keep_editing))
                .positive(getString(R.string.herd_action_discard))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getEntryDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void renderPens(@Nullable List<PenOptionVO> pens) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        String unit = vm.currentUnit();
        if (pens != null) {
            for (PenOptionVO p : pens) {
                String sub = p.isDisabled() && p.getHint() != null ? p.getHint()
                        : HerdFormat.stockOfCapacity(p.getStockCount(), p.getCapacity(), unit);
                items.add(new OptionAdapter.Item(p.getPenId(), p.getPenName(), sub, p.isDisabled()));
            }
        }
        penAdapter.setItems(items);
        OptionAdapter.Item selected = penAdapter.findById(vm.getPenId().getValue());
        binding.herdPenInput.setText(selected == null ? "" : selected.title, false);
    }

    private void renderBreeds(@Nullable List<CategoryVO> tree) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (tree != null) {
            for (CategoryVO c : tree) {
                if (c.getBreeds() == null) {
                    continue;
                }
                for (CategoryBreedVO b : c.getBreeds()) {
                    items.add(new OptionAdapter.Item(b.getId(), b.getName(), c.getName(), false));
                }
            }
        }
        breedAdapter.setItems(items);
        OptionAdapter.Item selected = breedAdapter.findById(vm.getBreedId().getValue());
        binding.herdBreedInput.setText(selected == null ? "" : selected.title, false);
    }
}
