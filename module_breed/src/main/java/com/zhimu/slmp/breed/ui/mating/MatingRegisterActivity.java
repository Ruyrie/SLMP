package com.zhimu.slmp.breed.ui.mating;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityMatingBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.OptionAdapter;
import com.zhimu.slmp.breed.ui.common.PregCheckHost;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * B03 登记配种 / 妊检（RouterPath.B03）：参数 EXTRA_ANIMAL_ID 预选母猪；EXTRA_RECORD_ID + EXTRA_RECORD_JSON 为修改配种。
 * 成功后返回上一页，Snackbar“已登记配种，预产期 2027年2月10日 周三”。
 */
public class MatingRegisterActivity extends BaseActivity implements PregCheckHost.Provider {

    private static final String REQ_DATE = "breed_mating_date";
    private static final String REQ_DISCARD = "breed_mating_discard";

    private BreedActivityMatingBinding binding;
    private MatingRegisterViewModel vm;
    private OptionAdapter animalAdapter;
    private PendingCheckAdapter pendingAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.breed_activity_mating);
        vm = obtainViewModel(BreedInjector.factory(), MatingRegisterViewModel.class);
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
        BreedUi.applyTone(binding.breedDueIconBox, BreedUi.TONE_SECONDARY, binding.breedDueIcon);

        animalAdapter = OptionAdapter.bind(binding.breedAnimalInput, vm::selectAnimal);
        animalAdapter.setEmptyText(MatingRegisterViewModel.MSG_NO_ANIMAL);
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
                (key, result) -> vm.onMatingDatePicked(DateTimePicker.getDate(result)));
        binding.breedMethodArtificial.setOnClickListener(v -> vm.onMethodPicked(BreedCodes.METHOD_ARTIFICIAL));
        binding.breedMethodNatural.setOnClickListener(v -> vm.onMethodPicked(BreedCodes.METHOD_NATURAL));
        binding.breedBoarInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding.breedBoarInput.hasFocus()) {
                    vm.onBoarEdited();
                }
            }
        });

        binding.breedPendingList.setLayoutManager(new LinearLayoutManager(this));
        binding.breedPendingList.addItemDecoration(new GroupedListDecoration(this));
        pendingAdapter = new PendingCheckAdapter(vm::openCheck);
        binding.breedPendingList.setAdapter(pendingAdapter);
        vm.getPendingChecks().observe(this, pendingAdapter::submitList);
        vm.getPendingTotal().observe(this, total -> renderPending());
        vm.getEditMode().observe(this, edit -> {
            renderPending();
            binding.breedActionBar.setPrimaryText(getString(Boolean.TRUE.equals(edit)
                    ? R.string.breed_action_save_edit : R.string.breed_action_register_mating));
        });

        binding.breedActionBar.setOnPrimaryClickListener(v -> vm.submitMating());
        vm.getSubmitting().observe(this, s -> binding.breedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, c -> binding.breedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getScrollToError().observe(this, field -> scrollTo(field));
        vm.getFinishWithMessage().observe(this, msg -> BreedNav.finishWithMessage(this, msg, null));
        vm.getNoPermission().observe(this, v -> finish());
        vm.getConfirmDiscard().observe(this, v -> BreedNav.showDiscard(this, REQ_DISCARD));
        BreedNav.listenDiscard(this, REQ_DISCARD);

        vm.getShowCheckSheet().observe(this, v -> PregCheckSheet.show(this, false));
        vm.getDismissCheckSheet().observe(this, msg -> {
            PregCheckSheet.dismissIn(getSupportFragmentManager());
            if (msg != null) {
                showMessage(msg);
            }
        });
    }

    @NonNull
    @Override
    public PregCheckHost pregCheckHost() {
        if (vm == null) {
            vm = obtainViewModel(BreedInjector.factory(), MatingRegisterViewModel.class);
        }
        return vm;
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    private void renderOptions(@Nullable List<BreedingAnimalOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (BreedingAnimalOptionVO o : list) {
                items.add(new OptionAdapter.Item(o.getAnimalId(),
                        BreedTextFormatter.animalLabel(o.getEarTag(), o.getBreedName(), o.getParity()),
                        o.getPenName(), o.getStatusText(), BreedUi.statusTagType(o.getStatus()), false));
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

    private void renderPending() {
        Integer total = vm.getPendingTotal().getValue();
        boolean show = !Boolean.TRUE.equals(vm.getEditMode().getValue()) && total != null && total > 0;
        binding.breedPendingTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.breedPendingList.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.breedPendingTitle.setText(getString(R.string.breed_pending_title, total));
        }
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getMatingDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(vm.minMatingDate(), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target = "animal_id".equals(field) ? binding.breedAnimalLayout
                : "mating_date".equals(field) ? binding.breedDateLayout : binding.breedBoarInput;
        binding.breedScroll.post(() -> binding.breedScroll.smoothScrollTo(0, Math.max(0, target.getTop() - 24)));
    }
}
