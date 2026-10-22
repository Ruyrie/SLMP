package com.zhimu.slmp.herd.ui.penedit;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityPenEditBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.OptionAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * L05 新增栏舍（L03 顶部栏 add）/ 编辑栏舍（L04 菜单“编辑栏舍”，编号只读）。
 */
public class PenEditActivity extends BaseActivity {

    private static final String REQ_DISCARD = "herd_pen_discard";

    private HerdActivityPenEditBinding binding;
    private PenEditViewModel vm;
    private OptionAdapter categoryAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.herd_activity_pen_edit);
        vm = obtainViewModel(HerdInjector.factory(), PenEditViewModel.class);
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

        categoryAdapter = new OptionAdapter(this);
        binding.herdCategoryInput.setAdapter(categoryAdapter);
        binding.herdCategoryInput.setOnItemClickListener((parent, view, position, id) -> {
            OptionAdapter.Item item = categoryAdapter.getItem(position);
            if (item != null) {
                vm.getCategoryId().setValue(item.id);
                vm.clearFieldError(PenEditViewModel.F_CATEGORY);
            }
        });
        vm.getFormOptions().observe(this, this::renderCategories);
        vm.getCategoryId().observe(this, id -> {
            OptionAdapter.Item item = categoryAdapter.findById(id);
            binding.herdCategoryInput.setText(item == null ? "" : item.title, false);
        });
        vm.getPenTypes().observe(this, this::renderTypes);
        vm.getPenType().observe(this, this::checkType);
        vm.getEditMode().observe(this, edit -> {
            boolean readOnly = Boolean.TRUE.equals(edit);
            binding.herdCodeInput.setFocusable(!readOnly);
            binding.herdCodeInput.setFocusableInTouchMode(!readOnly);
            binding.herdCodeInput.setCursorVisible(!readOnly);
            binding.herdCodeInput.setTextColor(MaterialColors.getColor(binding.herdCodeInput, readOnly
                    ? com.google.android.material.R.attr.colorOnSurfaceVariant
                    : com.google.android.material.R.attr.colorOnSurface));
        });
        vm.getCapacityWarning().observe(this, warning -> {
            boolean warn = warning != null;
            binding.herdCapacityLayout.setHelperText(warn ? warning : getString(R.string.herd_pen_edit_capacity_helper));
            int color = MaterialColors.getColor(binding.herdCapacityLayout, warn
                    ? com.zhimu.slmp.base.R.attr.colorWarning
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            binding.herdCapacityLayout.setHelperTextColor(ColorStateList.valueOf(color));
        });
        clearOnInput(binding.herdCodeInput, PenEditViewModel.F_CODE);
        clearOnInput(binding.herdNameInput, PenEditViewModel.F_NAME);
        clearOnInput(binding.herdCapacityInput, PenEditViewModel.F_CAPACITY);
        vm.getFieldErrors().observe(this, this::scrollToFirstError);

        binding.herdActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSubmitting().observe(this, s -> binding.herdActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFinishEvent().observe(this, msg -> HerdNav.finishWithMessage(this, msg));
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

    private void renderCategories(@Nullable PenFormOptionsVO options) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (options != null) {
            for (PenFormOptionsVO.CategoryOption c : options.getCategories()) {
                items.add(new OptionAdapter.Item(c.getCategoryId(), c.getCategoryName(), null, false));
            }
        }
        categoryAdapter.setItems(items);
        OptionAdapter.Item selected = categoryAdapter.findById(vm.getCategoryId().getValue());
        binding.herdCategoryInput.setText(selected == null ? "" : selected.title, false);
    }

    private void renderTypes(@Nullable List<DictItemVO> types) {
        binding.herdTypeChips.removeAllViews();
        if (types == null) {
            return;
        }
        for (DictItemVO t : types) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.herd_item_choice_chip, binding.herdTypeChips,
                    false);
            chip.setText(t.getName());
            chip.setTag(t.getCode());
            chip.setChecked(Objects.equals(t.getCode(), vm.getPenType().getValue()));
            ClickThrottle.setOnClickListener(chip, v -> {
                vm.getPenType().setValue(t.getCode());
                vm.clearFieldError(PenEditViewModel.F_TYPE);
            });
            binding.herdTypeChips.addView(chip);
        }
    }

    private void checkType(@Nullable String code) {
        for (int i = 0; i < binding.herdTypeChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.herdTypeChips.getChildAt(i);
            chip.setChecked(Objects.equals(chip.getTag(), code));
        }
    }

    private void clearOnInput(@NonNull EditText input, @NonNull String field) {
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
                    vm.clearFieldError(field);
                }
            }
        });
    }

    private void scrollToFirstError(@Nullable Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        String first = errors.keySet().iterator().next();
        android.view.View target = PenEditViewModel.F_CODE.equals(first) ? binding.herdCodeInput
                : PenEditViewModel.F_NAME.equals(first) ? binding.herdNameInput
                : PenEditViewModel.F_TYPE.equals(first) ? binding.herdTypeChips
                : PenEditViewModel.F_CATEGORY.equals(first) ? binding.herdCategoryInput : binding.herdCapacityInput;
        binding.herdScroll.post(() -> binding.herdScroll.smoothScrollTo(0, Math.max(0, target.getTop() - 48)));
    }
}
