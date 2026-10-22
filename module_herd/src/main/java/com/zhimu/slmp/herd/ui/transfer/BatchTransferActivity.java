package com.zhimu.slmp.herd.ui.transfer;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchTransferBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.ui.common.FormViews;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.OptionAdapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * L10 转栏登记（L06 更多菜单“转栏”）/ 修改转栏记录（L06 长按转栏记录）。成功后返回 L06 并显示 TransferResultVO.message。
 */
public class BatchTransferActivity extends BaseActivity {

    private static final String REQ_DATE = "herd_transfer_date";
    private static final String REQ_DISCARD = "herd_transfer_discard";

    private HerdActivityBatchTransferBinding binding;
    private BatchTransferViewModel vm;
    private OptionAdapter batchAdapter;
    private OptionAdapter fromAdapter;
    private OptionAdapter toAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_batch_transfer);
        vm = obtainViewModel(HerdInjector.factory(), BatchTransferViewModel.class);
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

        vm.getBatch().observe(this, this::renderBatch);
        batchAdapter = FormViews.bindDropdown(binding.herdBatchInput, vm::onBatchSelected);
        vm.getBatchOptions().observe(this, this::renderBatchOptions);
        fromAdapter = FormViews.bindDropdown(binding.herdFromInput, vm::onFromPenSelected);
        vm.getFromPens().observe(this, this::renderFrom);
        vm.getFromPenId().observe(this, id -> FormViews.showSelected(binding.herdFromInput, fromAdapter, id));
        toAdapter = FormViews.bindDropdown(binding.herdToInput, vm::onToPenSelected);
        vm.getToPenOptions().observe(this, this::renderTo);
        vm.getToPenId().observe(this, id -> FormViews.showSelected(binding.herdToInput, toAdapter, id));
        vm.getMaxQuantity().observe(this, max -> {
            binding.herdQuantityLayout.setHelperText(max == null ? null : vm.quantityLimitText(max));
            binding.herdQuantityLayout.setSuffixText(vm.unit());
        });
        binding.herdQuantityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.validateQuantityLive();
            }
        });
        FormViews.bindCapacityBanner(this, binding.herdCapacityBanner, vm.getCapacityBanner(),
                vm.getCapacityChecked(), getString(R.string.herd_transfer_capacity_title));

        ClickThrottle.setOnClickListener(binding.herdDateInput, v -> pickDate());
        binding.herdDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDateSelected(DateTimePicker.getDate(result)));

        binding.herdActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getCanSubmit().observe(this, can -> binding.herdActionBar.setPrimaryEnabled(Boolean.TRUE.equals(can)
                || Boolean.TRUE.equals(vm.getSubmitting().getValue())));
        vm.getSubmitting().observe(this, s -> binding.herdActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFinishEvent().observe(this, msg -> HerdNav.finishWithMessage(this, msg));
        vm.getDiscardEvent().observe(this, v -> FormViews.showDiscard(this, REQ_DISCARD));
        FormViews.listenDiscard(this, REQ_DISCARD);
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getEventDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(DateFormatter.parseDate(vm.getMinDate().getValue()), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void renderBatch(@Nullable BatchDetailVO d) {
        if (d == null) {
            return;
        }
        binding.herdBatchCard.setTitle(d.getBatchNo());
        binding.herdBatchCard.setSubtitle(d.getBreedTitle() + HerdFormat.DOT + "共 "
                + HerdFormat.qty(d.getCurrentCount(), d.getUnitText()));
        FormViews.showSelected(binding.herdBatchInput, batchAdapter, d.getBatchId());
    }

    private void renderBatchOptions(@Nullable List<BatchListItemVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (BatchListItemVO b : list) {
                items.add(new OptionAdapter.Item(b.getBatchId(), b.getBatchNo(),
                        "共 " + HerdFormat.qty(b.getCurrentCount(), b.getUnitText()), false));
            }
        }
        batchAdapter.setItems(items);
    }

    private void renderFrom(@Nullable List<PenStockVO> pens) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (pens != null) {
            for (PenStockVO p : pens) {
                items.add(new OptionAdapter.Item(p.getPenId(), p.getPenName(),
                        "本批次在栏 " + HerdFormat.qty(p.getStockCount(), vm.unit()), false));
            }
        }
        fromAdapter.setItems(items);
        FormViews.showSelected(binding.herdFromInput, fromAdapter, vm.getFromPenId().getValue());
    }

    private void renderTo(@Nullable List<PenOptionVO> pens) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (pens != null) {
            for (PenOptionVO p : pens) {
                String sub = p.isDisabled() && p.getHint() != null ? p.getHint()
                        : HerdFormat.stockOfCapacity(p.getStockCount(), p.getCapacity(), vm.unit());
                items.add(new OptionAdapter.Item(p.getPenId(), p.getPenName(), sub, p.isDisabled()));
            }
        }
        toAdapter.setItems(items);
        FormViews.showSelected(binding.herdToInput, toAdapter, vm.getToPenId().getValue());
    }
}
