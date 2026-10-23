package com.zhimu.slmp.breed.ui.farrowing;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityFarrowingBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.CapacityConfirmVO;
import com.zhimu.slmp.breed.model.vo.FarrowingDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * B04 登记产仔（RouterPath.B04）：参数 EXTRA_ANIMAL_ID（T04 去登记为待办 biz_id，兼容 sow_id）、EXTRA_TODO_ID；
 * 不带母猪时页面内下拉选择（B01 FAB）；EXTRA_RECORD_ID 为修改未转批次的产仔记录。
 */
public class FarrowingRegisterActivity extends BaseActivity {

    private static final String REQ_DATE = "breed_farrow_date";
    private static final String REQ_DISCARD = "breed_farrow_discard";
    private static final String REQ_PEN = "breed_farrow_pen";

    private BreedActivityFarrowingBinding binding;
    private FarrowingRegisterViewModel vm;
    private OptionAdapter animalAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_farrowing);
        vm = obtainViewModel(BreedInjector.factory(), FarrowingRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        binding.breedTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });
        binding.breedState.setOnRetryListener(v -> vm.retry());
        binding.breedState.setOnNoPermissionActionListener(v -> finish());
        vm.getUiState().observe(this, binding.breedState::render);
        BreedUi.applyTone(binding.breedTransferIconBox, BreedUi.TONE_SECONDARY, binding.breedTransferIcon);

        animalAdapter = OptionAdapter.bind(binding.breedAnimalInput, vm::selectAnimal);
        animalAdapter.setEmptyText(getString(R.string.breed_farrowing_animal_empty));
        vm.getAnimalOptions().observe(this, this::renderOptions);
        vm.getContext().observe(this, this::renderContext);
        vm.getEditDetail().observe(this, this::renderEdit);

        ClickThrottle.setOnClickListener(binding.breedDateInput, v -> pickDate());
        binding.breedDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDatePicked(DateTimePicker.getDate(result)));
        watch(binding.breedTotalInput, BreedFormRules.F_TOTAL);
        watch(binding.breedLiveInput, BreedFormRules.F_LIVE);
        watch(binding.breedStillInput, BreedFormRules.F_STILL);
        binding.breedTransferSwitch.setOnCheckedChangeListener((b, checked) -> vm.onTransferChanged(checked));

        ClickThrottle.setOnClickListener(binding.breedKvPen.getRoot(), v -> vm.onPickPen());
        vm.getShowPenPicker().observe(this, this::showPenPicker);
        getSupportFragmentManager().setFragmentResultListener(REQ_PEN, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            if (value != null) {
                vm.pickPen(Long.parseLong(value));
            }
        });

        vm.getCapacityText().observe(this, this::renderCapacity);
        binding.breedCapacityNotice.setOnCheckedChangeListener((b, checked) -> vm.onCapacityChecked(checked));
        vm.getCapacityConfirmed().observe(this, c -> {
            boolean v = Boolean.TRUE.equals(c);
            if (binding.breedCapacityNotice.isChecked() != v) {
                binding.breedCapacityNotice.setChecked(v);
            }
        });

        vm.getEditMode().observe(this, edit -> binding.breedActionBar.setPrimaryText(getString(
                Boolean.TRUE.equals(edit) ? R.string.breed_action_save_edit : R.string.breed_farrowing_title)));
        binding.breedActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.breedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, c -> binding.breedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getScrollToError().observe(this, this::scrollTo);
        vm.getFinishWithMessage().observe(this, e -> BreedNav.finishWithMessage(this, e.getText(), e.getBatchId()));
        vm.getNoPermission().observe(this, v -> finish());
        vm.getConfirmDiscard().observe(this, v -> BreedNav.showDiscard(this, REQ_DISCARD));
        BreedNav.listenDiscard(this, REQ_DISCARD);
        vm.getOpenArchive().observe(this, id -> {
            Router.open(this, RouterPath.B06, BreedNav.id(RouterPath.EXTRA_ANIMAL_ID, id));
            finish();
        });
        vm.getOpenChangeRequest().observe(this, id -> {
            Bundle b = new Bundle();
            b.putString(RouterPath.EXTRA_BIZ_TYPE, BreedCodes.BIZ_FARROWING);
            b.putLong(RouterPath.EXTRA_BIZ_ID, id);
            Router.open(this, RouterPath.U01, b);
            finish();
        });
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    private void watch(@NonNull EditText input, @NonNull String field) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (input.hasFocus()) {
                    vm.onCountEdited(field);
                }
            }
        });
    }

    private void renderOptions(@Nullable List<BreedingAnimalOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (BreedingAnimalOptionVO o : list) {
                String sub = BreedTextFormatter.nz(o.getPenName()) + BreedTextFormatter.DOT + "预产期 "
                        + BreedTextFormatter.md(o.getExpectedDueDate());
                items.add(new OptionAdapter.Item(o.getAnimalId(),
                        BreedTextFormatter.animalLabel(o.getEarTag(), o.getBreedName(), o.getParity()), sub,
                        o.getStatusText(), BreedUi.statusTagType(o.getStatus()), false));
            }
        }
        animalAdapter.setItems(items);
    }

    private void renderContext(@Nullable RegisterContextVO ctx) {
        if (ctx == null || ctx.getAnimal() == null) {
            return;
        }
        BreedingAnimalOptionVO a = ctx.getAnimal();
        binding.breedAnimalTitle.setText(BreedTextFormatter.animalLabel(a.getEarTag(), a.getBreedName(), a.getParity()));
        Integer days = ctx.getDaysToDue();
        String due = BreedTextFormatter.md(ctx.getExpectedDueDate());
        if (days != null && days == 0) {
            due += "（今天）";
        } else if (days != null && days < 0) {
            due += "（超 " + (-days) + " 天）";
        } else if (days != null) {
            due += "（还有 " + days + " 天）";
        }
        binding.breedAnimalSubtitle.setText(BreedTextFormatter.nz(a.getPenName()) + BreedTextFormatter.DOT
                + BreedTextFormatter.md(ctx.getFirstMatingDate()) + "配种" + BreedTextFormatter.DOT + "预产期 " + due);
        binding.breedAnimalTag.setStatus(a.getStatusText(), BreedUi.statusTagType(a.getStatus()));
        BreedUi.applyTone(binding.breedAnimalIconBox, BreedUi.animalTone(BreedCodes.PREGNANT, days),
                binding.breedAnimalIcon);
        if (Boolean.TRUE.equals(vm.getNeedPick().getValue())) {
            binding.breedAnimalInput.setText(binding.breedAnimalTitle.getText(), false);
        }
    }

    private void renderEdit(@Nullable FarrowingDetailVO d) {
        if (d == null) {
            return;
        }
        binding.breedAnimalTitle.setText(BreedTextFormatter.nz(d.getEarTag()) + BreedTextFormatter.DOT
                + BreedTextFormatter.parity(d.getParityNo()));
        binding.breedAnimalSubtitle.setText(BreedTextFormatter.md(d.getFarrowDate()) + "产仔");
        binding.breedAnimalTag.setStatus(getString(R.string.breed_status_lactating), StatusTagView.PRIMARY);
        BreedUi.applyTone(binding.breedAnimalIconBox, BreedUi.TONE_SECONDARY, binding.breedAnimalIcon);
    }

    private void renderCapacity(@Nullable String text) {
        boolean show = text != null;
        if (show) {
            binding.breedCapacityNotice.setNotice(null, text);
        }
        if (show != (binding.breedCapacityNotice.getVisibility() == View.VISIBLE)) {
            binding.breedCapacityNotice.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                binding.breedCapacityNotice.setAlpha(0f);
                binding.breedCapacityNotice.animate().alpha(1f).setDuration(250).start();
            }
        }
    }

    private void showPenPicker(@Nullable List<PenOptionVO> pens) {
        if (pens == null) {
            return;
        }
        RegisterContextVO.DefaultPen current = vm.getTargetPen().getValue();
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_PEN)
                .title(getString(R.string.breed_pick_pen_title));
        for (PenOptionVO p : pens) {
            String hint = p.getHint() != null ? p.getHint() : "在栏 " + BreedTextFormatter.qty(p.getStockCount(),
                    BreedTextFormatter.UNIT_HEAD) + BreedTextFormatter.DOT + "容量 "
                    + BreedTextFormatter.qty(p.getCapacity(), BreedTextFormatter.UNIT_HEAD);
            b.option(new OptionSheetDialogFragment.Option(String.valueOf(p.getPenId()),
                    BreedTextFormatter.nz(p.getPenName()), hint).disabled(p.isDisabled()));
        }
        if (current != null) {
            b.selected(String.valueOf(current.getPenId()));
        }
        b.build().showSafely(getSupportFragmentManager());
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getFarrowDate().getValue());
        RegisterContextVO ctx = vm.getContext().getValue();
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(ctx == null ? null : DateFormatter.parseDate(ctx.getFirstMatingDate()), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (BreedFormRules.F_CAPACITY.equals(field)) {
            target = binding.breedCapacityNotice;
        } else if (BreedFormRules.F_TRANSFER.equals(field)) {
            target = binding.breedTransferCard;
        } else if (BreedFormRules.F_LIVE.equals(field) || BreedFormRules.F_STILL.equals(field)) {
            target = binding.breedCountRow;
            binding.breedLiveInput.requestFocus();
        } else if (BreedFormRules.F_TOTAL.equals(field)) {
            target = binding.breedTotalLayout;
            binding.breedTotalInput.requestFocus();
        } else if (BreedFormRules.F_FARROW_DATE.equals(field)) {
            target = binding.breedDateLayout;
        } else {
            target = binding.breedAnimalLayout;
        }
        binding.breedScroll.post(() -> binding.breedScroll.smoothScrollTo(0, Math.max(0, target.getTop() - 24)));
    }
}
