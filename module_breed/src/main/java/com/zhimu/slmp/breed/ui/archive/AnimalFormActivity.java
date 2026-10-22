package com.zhimu.slmp.breed.ui.archive;

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
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityAnimalFormBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.model.vo.CategoryBreedVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 档案表单（BreedExtras.ACTION_ANIMAL_FORM，无编号页面）：B01 面板“新增母猪”、B06 菜单“编辑档案”（EXTRA_ANIMAL_ID）。
 * 保存成功提示“已保存档案”，新增回 B01，编辑回 B06。
 */
public class AnimalFormActivity extends BaseActivity {

    private static final String REQ_BIRTH = "breed_form_birth";
    private static final String REQ_DISCARD = "breed_form_discard";

    private BreedActivityAnimalFormBinding binding;
    private AnimalFormViewModel vm;
    private OptionAdapter breedAdapter;
    private OptionAdapter penAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_animal_form);
        vm = obtainViewModel(BreedInjector.factory(), AnimalFormViewModel.class);
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

        breedAdapter = OptionAdapter.bind(binding.breedBreedInput, vm::onBreedPicked);
        vm.getBreedOptions().observe(this, this::renderBreeds);
        vm.getBreedId().observe(this, id -> OptionAdapter.showSelected(binding.breedBreedInput, breedAdapter, id));
        penAdapter = OptionAdapter.bind(binding.breedPenInput, vm::onPenPicked);
        vm.getPenOptions().observe(this, this::renderPens);
        vm.getPenId().observe(this, id -> OptionAdapter.showSelected(binding.breedPenInput, penAdapter, id));

        ClickThrottle.setOnClickListener(binding.breedBirthInput, v -> pickBirth());
        binding.breedBirthLayout.setEndIconOnClickListener(v -> pickBirth());
        getSupportFragmentManager().setFragmentResultListener(REQ_BIRTH, this,
                (key, result) -> vm.onBirthPicked(DateTimePicker.getDate(result)));
        binding.breedSourcePurchased.setOnClickListener(v -> vm.onSourcePicked(BreedCodes.SOURCE_PURCHASED));
        binding.breedSourceSelf.setOnClickListener(v -> vm.onSourcePicked(BreedCodes.SOURCE_SELF_BRED));
        watch(binding.breedEarInput, BreedFormRules.F_EAR_TAG);
        watch(binding.breedParityInput, BreedFormRules.F_INITIAL_PARITY);

        binding.breedActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSubmitting().observe(this, s -> binding.breedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, c -> binding.breedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getScrollToError().observe(this, this::scrollTo);
        vm.getFinishWithMessage().observe(this, msg -> {
            if (msg == null) {
                finish();
            } else {
                BreedNav.finishWithMessage(this, msg, null);
            }
        });
        vm.getNoPermission().observe(this, v -> finish());
        vm.getConfirmDiscard().observe(this, v -> BreedNav.showDiscard(this, REQ_DISCARD));
        BreedNav.listenDiscard(this, REQ_DISCARD);
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
                    vm.onFieldEdited(field);
                }
            }
        });
    }

    private void renderBreeds(@Nullable List<CategoryBreedVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (CategoryBreedVO b : list) {
                String hint = b.getGestationDays() == null ? null : "妊娠期 " + b.getGestationDays() + " 天";
                items.add(new OptionAdapter.Item(b.getId(), BreedTextFormatter.nz(b.getName()), hint));
            }
        }
        breedAdapter.setItems(items);
        OptionAdapter.showSelected(binding.breedBreedInput, breedAdapter, vm.getBreedId().getValue());
    }

    private void renderPens(@Nullable List<PenOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (PenOptionVO p : list) {
                items.add(new OptionAdapter.Item(p.getPenId(), BreedTextFormatter.nz(p.getPenName()), p.getHint(),
                        null, com.zhimu.slmp.base.widget.StatusTagView.NEUTRAL, p.isDisabled()));
            }
        }
        penAdapter.setItems(items);
        OptionAdapter.showSelected(binding.breedPenInput, penAdapter, vm.getPenId().getValue());
    }

    private void pickBirth() {
        LocalDate selected = DateFormatter.parseDate(vm.getBirthDate().getValue());
        DateTimePicker.date(REQ_BIRTH)
                .selection(selected != null ? selected : DateFormatter.today().minusYears(1))
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target = BreedFormRules.F_BREED.equals(field) ? binding.breedBreedLayout
                : BreedFormRules.F_PEN.equals(field) ? binding.breedPenLayout
                : BreedFormRules.F_BIRTH.equals(field) ? binding.breedBirthLayout
                : BreedFormRules.F_INITIAL_PARITY.equals(field) ? binding.breedParityLayout : binding.breedEarLayout;
        binding.breedScroll.post(() -> binding.breedScroll.smoothScrollTo(0, Math.max(0, target.getTop() - 24)));
    }
}
