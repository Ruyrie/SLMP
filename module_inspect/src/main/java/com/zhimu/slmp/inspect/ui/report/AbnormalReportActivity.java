package com.zhimu.slmp.inspect.ui.report;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectActivityReportBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.ReportFormVO;
import com.zhimu.slmp.inspect.model.vo.SymptomVO;
import com.zhimu.slmp.inspect.ui.common.InspectNav;
import com.zhimu.slmp.inspect.ui.common.OptionAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * I05 异常上报：入口为 I02 与 I01 员工版的“转上报”（带入栏舍、批次、巡检照片）、T02 快捷登记“异常上报”（直接上报）。
 * 提交成功后进入 I06（替换本页）。
 */
public class AbnormalReportActivity extends BaseActivity {

    private static final String REQ_DISCARD = "inspect_report_discard";

    private InspectActivityReportBinding binding;
    private AbnormalReportViewModel vm;
    private OptionAdapter penAdapter;
    private OptionAdapter batchAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.inspect_activity_report);
        vm = obtainViewModel(InspectInjector.factory(), AbnormalReportViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        if (vm.getMedia() != null) {
            binding.inspectMedia.bind(this, vm.getMedia());
        }
        binding.inspectMedia.setCameraTitle(getString(R.string.inspect_report_title));

        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());

        penAdapter = OptionAdapter.bind(binding.inspectPenInput, vm::selectPen);
        batchAdapter = OptionAdapter.bind(binding.inspectBatchInput, vm::selectBatch);
        vm.getPens().observe(this, this::renderPens);
        vm.getPenId().observe(this, id -> {
            penAdapter.showSelected(binding.inspectPenInput, id);
            ReportFormVO.ReportPen pen = vm.getFormCtx().getValue() == null ? null
                    : vm.getFormCtx().getValue().findPen(id);
            if (pen != null) {
                binding.inspectMedia.setCameraTitle(getString(R.string.inspect_report_title) + " · " + pen.getPenName());
            }
        });
        vm.getBatches().observe(this, this::renderBatches);
        vm.getBatchId().observe(this, id -> batchAdapter.showSelected(binding.inspectBatchInput, id));
        binding.inspectSickInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.validateSick();
            }
        });
        vm.getSymptoms().observe(this, this::renderSymptoms);
        vm.getSymptomIds().observe(this, this::checkSymptoms);
        vm.getFocusSick().observe(this, v -> binding.inspectSickInput.post(() -> {
            binding.inspectSickInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.inspectSickInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }));
        vm.getScrollTo().observe(this, this::scrollToField);

        binding.inspectActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.inspectActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getNavEvent().observe(this, t -> InspectNav.open(this, null, t));
        vm.getShowDiscard().observe(this, v -> ConfirmDialogFragment.builder(REQ_DISCARD)
                .title(getString(R.string.inspect_report_discard_title))
                .message(getString(R.string.inspect_report_discard_message))
                .negative(getString(R.string.inspect_action_keep_editing))
                .positive(getString(R.string.inspect_action_discard))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if ("inspect_report_retry".equals(actionId)) {
            vm.submit();
        }
    }

    private void renderPens(@Nullable List<ReportFormVO.ReportPen> pens) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (pens != null) {
            for (ReportFormVO.ReportPen p : pens) {
                items.add(new OptionAdapter.Item(p.getPenId(), p.getPenName(), null));
            }
        }
        penAdapter.setItems(items);
        penAdapter.showSelected(binding.inspectPenInput, vm.getPenId().getValue());
    }

    private void renderBatches(@Nullable List<ReportFormVO.ReportBatch> batches) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (batches != null) {
            for (ReportFormVO.ReportBatch b : batches) {
                items.add(new OptionAdapter.Item(b.getBatchId(), b.getBatchNo(), "存栏 " + b.getStockCount() + " 头"));
            }
        }
        batchAdapter.setItems(items);
        batchAdapter.showSelected(binding.inspectBatchInput, vm.getBatchId().getValue());
    }

    private void renderSymptoms(@Nullable List<SymptomVO> list) {
        binding.inspectSymptoms.removeAllViews();
        if (list == null) {
            return;
        }
        for (SymptomVO s : list) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.inspect_item_option_chip, binding.inspectSymptoms,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(s.getName());
            chip.setTag(s.getSymptomId());
            ClickThrottle.setOnClickListener(chip, v -> vm.toggleSymptom(s.getSymptomId()));
            binding.inspectSymptoms.addView(chip);
        }
        checkSymptoms(vm.getSymptomIds().getValue());
    }

    private void checkSymptoms(@Nullable Set<Long> ids) {
        Set<Long> set = ids == null ? Collections.emptySet() : ids;
        for (int i = 0; i < binding.inspectSymptoms.getChildCount(); i++) {
            Chip c = (Chip) binding.inspectSymptoms.getChildAt(i);
            c.setChecked(set.contains((Long) c.getTag()));
        }
    }

    private void scrollToField(@Nullable String key) {
        View target;
        if (AbnormalReportViewModel.F_PEN.equals(key) || AbnormalReportViewModel.F_BATCH.equals(key)) {
            target = binding.inspectPenLayout;
        } else if (AbnormalReportViewModel.F_SICK.equals(key)) {
            target = binding.inspectSickLayout;
        } else if (AbnormalReportViewModel.F_DESCRIPTION.equals(key)) {
            target = binding.inspectDescriptionLayout;
        } else {
            return;
        }
        View t = target;
        binding.inspectScroll.post(() -> {
            Rect r = new Rect();
            t.getDrawingRect(r);
            binding.inspectScroll.offsetDescendantRectToMyCoords(t, r);
            binding.inspectScroll.smoothScrollTo(0, Math.max(0, r.top - 32));
        });
    }
}
