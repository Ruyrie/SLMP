package com.zhimu.slmp.herd.ui.death;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchDeathBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.ExecutorOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.ui.common.FormViews;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.OptionAdapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * L11 死淘登记（L06 更多菜单 / 员工 FAB / T02）与修改死淘记录（L06 长按）。成功后返回来源页并显示 DeathResultVO.message。
 */
public class BatchDeathActivity extends BaseActivity {

    private static final String REQ_DATE = "herd_death_date";
    private static final String REQ_DISCARD = "herd_death_discard";
    private static final String REQ_DRAFT = "herd_death_draft";
    private static final long QUANTITY_DEBOUNCE_MS = 500;

    private HerdActivityBatchDeathBinding binding;
    private BatchDeathViewModel vm;
    private OptionAdapter batchAdapter;
    private OptionAdapter penAdapter;
    private OptionAdapter executorAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable validateQuantity = () -> vm.validateQuantity();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_batch_death);
        vm = obtainViewModel(HerdInjector.factory(), BatchDeathViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        // 照片网格：必须在页面 STARTED 之前 bind（内部注册 Activity Result）
        binding.herdPhotos.bind(this, vm.getPhotos());

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

        batchAdapter = FormViews.bindDropdown(binding.herdBatchInput, vm::onBatchSelected);
        vm.getBatchOptions().observe(this, this::renderBatches);
        vm.getBatchId().observe(this, id -> FormViews.showSelected(binding.herdBatchInput, batchAdapter, id));
        penAdapter = FormViews.bindDropdown(binding.herdPenInput, vm::onPenSelected);
        vm.getPenStocks().observe(this, this::renderPens);
        vm.getPenId().observe(this, id -> FormViews.showSelected(binding.herdPenInput, penAdapter, id));
        executorAdapter = FormViews.bindDropdown(binding.herdExecutorInput, vm::onExecutorSelected);
        vm.getExecutors().observe(this, this::renderExecutors);
        vm.getExecutorUserId().observe(this, id -> FormViews.showSelected(binding.herdExecutorInput, executorAdapter, id));
        vm.getBatch().observe(this, b -> binding.herdQuantityLayout.setSuffixText(vm.unit()));
        vm.getReasons().observe(this, this::renderReasons);
        vm.getReasonCode().observe(this, this::checkReason);

        // 数量：停止输入 500 毫秒或失焦后校验
        binding.herdQuantityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(validateQuantity);
                handler.postDelayed(validateQuantity, QUANTITY_DEBOUNCE_MS);
            }
        });
        binding.herdQuantityInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                handler.removeCallbacks(validateQuantity);
                vm.validateQuantity();
            }
        });

        ClickThrottle.setOnClickListener(binding.herdDateInput, v -> pickDate());
        binding.herdDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDateSelected(DateTimePicker.getDate(result)));

        binding.herdActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.herdActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFinishEvent().observe(this, msg -> HerdNav.finishWithMessage(this, msg));
        vm.getDiscardEvent().observe(this, v -> FormViews.showDiscard(this, REQ_DISCARD));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        vm.getRestoreDraftEvent().observe(this, v -> ConfirmDialogFragment.builder(REQ_DRAFT)
                .title(getString(R.string.herd_death_draft_title))
                .message(getString(R.string.herd_death_draft_message))
                .negative(getString(R.string.herd_death_draft_discard))
                .positive(getString(R.string.herd_death_draft_restore))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DRAFT, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.restoreDraft();
            } else {
                vm.discardDraft();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            vm.saveDraft();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(validateQuantity);
        super.onDestroy();
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getEventDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(vm.minDate(), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void renderBatches(@Nullable List<BatchListItemVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (BatchListItemVO b : list) {
                StringBuilder sub = new StringBuilder(b.getBreedTitle() == null ? "" : b.getBreedTitle());
                if (!b.getPens().isEmpty()) {
                    sub.append(HerdFormat.DOT).append(b.getPens().get(0).getPenName());
                }
                items.add(new OptionAdapter.Item(b.getBatchId(), b.getBatchNo(), sub.toString(), false));
            }
        }
        batchAdapter.setItems(items);
        FormViews.showSelected(binding.herdBatchInput, batchAdapter, vm.getBatchId().getValue());
    }

    private void renderPens(@Nullable List<PenStockVO> pens) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (pens != null) {
            for (PenStockVO p : pens) {
                items.add(new OptionAdapter.Item(p.getPenId(), p.getPenName(),
                        "在栏 " + HerdFormat.qty(p.getStockCount(), vm.unit()), false));
            }
        }
        penAdapter.setItems(items);
        FormViews.showSelected(binding.herdPenInput, penAdapter, vm.getPenId().getValue());
    }

    private void renderExecutors(@Nullable List<ExecutorOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (ExecutorOptionVO o : list) {
                items.add(new OptionAdapter.Item(o.getUserId(), o.getDisplayName() != null ? o.getDisplayName()
                        : o.getName(), null, false));
            }
        }
        executorAdapter.setItems(items);
        FormViews.showSelected(binding.herdExecutorInput, executorAdapter, vm.getExecutorUserId().getValue());
    }

    private void renderReasons(@Nullable List<DictItemVO> reasons) {
        binding.herdReasonChips.removeAllViews();
        if (reasons == null) {
            return;
        }
        for (DictItemVO r : reasons) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.herd_item_choice_chip, binding.herdReasonChips,
                    false);
            chip.setText(r.getName());
            chip.setTag(r.getCode());
            chip.setChecked(Objects.equals(r.getCode(), vm.getReasonCode().getValue()));
            ClickThrottle.setOnClickListener(chip, v -> vm.onReasonSelected(r.getCode()));
            binding.herdReasonChips.addView(chip);
        }
    }

    private void checkReason(@Nullable String code) {
        for (int i = 0; i < binding.herdReasonChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.herdReasonChips.getChildAt(i);
            chip.setChecked(Objects.equals(chip.getTag(), code));
        }
    }
}
