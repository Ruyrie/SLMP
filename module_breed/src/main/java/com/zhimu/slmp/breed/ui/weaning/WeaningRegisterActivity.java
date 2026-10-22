package com.zhimu.slmp.breed.ui.weaning;

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
import com.zhimu.slmp.breed.databinding.BreedActivityWeaningBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * B05 登记断奶（RouterPath.B05）：参数 EXTRA_ANIMAL_ID 预选哺乳母猪；EXTRA_RECORD_ID + EXTRA_RECORD_JSON 为修改断奶。
 */
public class WeaningRegisterActivity extends BaseActivity {

    private static final String REQ_DATE = "breed_wean_date";
    private static final String REQ_DISCARD = "breed_wean_discard";

    private BreedActivityWeaningBinding binding;
    private WeaningRegisterViewModel vm;
    private OptionAdapter animalAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_weaning);
        vm = obtainViewModel(BreedInjector.factory(), WeaningRegisterViewModel.class);
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

        animalAdapter = OptionAdapter.bind(binding.breedAnimalInput, vm::selectAnimal);
        animalAdapter.setEmptyText(getString(R.string.breed_weaning_animal_empty));
        vm.getAnimalOptions().observe(this, this::renderOptions);
        vm.getSelectedAnimal().observe(this, a -> showSelected());
        vm.getEditAnimalLabel().observe(this, label -> {
            if (label != null) {
                binding.breedAnimalInput.setText(label, false);
            }
        });

        ClickThrottle.setOnClickListener(binding.breedDateInput, v -> pickDate());
        binding.breedDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDatePicked(DateTimePicker.getDate(result)));
        watch(binding.breedWeanedInput, BreedFormRules.F_WEANED);
        watch(binding.breedWeightInput, BreedFormRules.F_WEIGHT);

        vm.getEditMode().observe(this, edit -> binding.breedActionBar.setPrimaryText(getString(
                Boolean.TRUE.equals(edit) ? R.string.breed_action_save_edit : R.string.breed_weaning_title)));
        binding.breedActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.breedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, c -> binding.breedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getScrollToError().observe(this, this::scrollTo);
        vm.getFinishWithMessage().observe(this, msg -> BreedNav.finishWithMessage(this, msg, null));
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

    private void renderOptions(@Nullable List<BreedingAnimalOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (BreedingAnimalOptionVO o : list) {
                String sub = BreedTextFormatter.nz(o.getPenName()) + BreedTextFormatter.DOT
                        + BreedTextFormatter.md(o.getLastFarrowDate()) + "产仔";
                items.add(new OptionAdapter.Item(o.getAnimalId(),
                        BreedTextFormatter.animalLabel(o.getEarTag(), o.getBreedName(), o.getParity()), sub,
                        o.getStatusText(), BreedUi.statusTagType(o.getStatus()), false));
            }
        }
        animalAdapter.setItems(items);
        showSelected();
    }

    private void showSelected() {
        if (Boolean.TRUE.equals(vm.getEditMode().getValue())) {
            return;
        }
        BreedingAnimalOptionVO a = vm.getSelectedAnimal().getValue();
        binding.breedAnimalInput.setText(a == null ? ""
                : BreedTextFormatter.animalLabel(a.getEarTag(), a.getBreedName(), a.getParity()), false);
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getWeanDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(vm.minWeanDate(), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target = BreedFormRules.F_WEAN_DATE.equals(field) ? binding.breedDateLayout
                : BreedFormRules.F_WEANED.equals(field) ? binding.breedWeanedLayout
                : BreedFormRules.F_WEIGHT.equals(field) ? binding.breedWeightLayout : binding.breedAnimalLayout;
        binding.breedScroll.post(() -> binding.breedScroll.smoothScrollTo(0, Math.max(0, target.getTop() - 24)));
    }
}
