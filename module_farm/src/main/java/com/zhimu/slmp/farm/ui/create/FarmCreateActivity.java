package com.zhimu.slmp.farm.ui.create;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityCreateBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;
import com.zhimu.slmp.map.contract.PickLocationContract;
import com.zhimu.slmp.map.model.vo.LocationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * F02 新建养殖场 / 修改注册信息（EXTRA_MODE = EDIT，从 F03 进入）。
 * 地址框点按进入 F04（PickLocationContract），资质图用 MediaPickerView；成功后 CREATE 打开 F03，EDIT 返回 F03。
 */
public class FarmCreateActivity extends BaseActivity {

    private static final String REQ_DISCARD = "farm_create_discard";

    private FarmActivityCreateBinding binding;
    private FarmCreateViewModel vm;
    private final List<CategoryVO> categoryList = new ArrayList<>();

    private final ActivityResultLauncher<com.zhimu.slmp.map.model.vo.PickLocationInput> mapLauncher =
            registerForActivityResult(new PickLocationContract(), result -> {
                if (result != null) {
                    vm.onLocationPicked(result);
                    highlightAddress();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_create);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), FarmCreateViewModel.class);
        observeBase(vm);
        binding.setVm(vm);

        boolean edit = vm.isEdit();
        FarmUi.setupTopBar(binding.topBar,
                getString(edit ? R.string.farm_create_title_edit : R.string.farm_create_title));
        binding.actionBar.setPrimaryText(getString(edit ? R.string.farm_create_submit_edit : R.string.farm_create_submit));
        binding.qualFiles.bind(this, vm.getQualFiles());
        PhoneTextWatcher.attach(binding.etContactPhone);

        binding.etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                vm.onNameFocusLost();
            }
        });
        ClickThrottle.setOnClickListener(binding.etAddress, v -> vm.onAddressClick());
        binding.tilAddress.setEndIconOnClickListener(v -> vm.onAddressClick());
        binding.etCategory.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < categoryList.size()) {
                vm.onCategorySelected(categoryList.get(position).getId());
            }
        });
        ClickThrottle.setOnClickListener(binding.actionBar.getPrimaryButton(), v -> {
            hideKeyboard(null);
            vm.submit();
        });
        binding.state.setOnRetryListener(v -> vm.retry());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
        observeViewModel();
    }

    private void observeViewModel() {
        vm.getUiState().observe(this, binding.state::render);
        vm.getCategories().observe(this, list -> {
            categoryList.clear();
            categoryList.addAll(list == null ? Collections.emptyList() : list);
            List<String> names = new ArrayList<>();
            for (CategoryVO c : categoryList) {
                names.add(c.getName());
            }
            binding.etCategory.setSimpleItems(names.toArray(new String[0]));
            renderCategory();
        });
        vm.getMainCategoryId().observe(this, id -> renderCategory());
        vm.getLocation().observe(this, this::renderLocation);
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getScrollToField().observe(this, this::scrollToField);
        vm.getOpenMapPick().observe(this, mapLauncher::launch);
        vm.getSubmitting().observe(this, s -> binding.actionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, ok -> binding.actionBar.setPrimaryEnabled(Boolean.TRUE.equals(ok)));
        vm.getShowDiscard().observe(this, v -> {
            ConfirmDialogFragment dialog = vm.isEdit()
                    ? ConfirmDialogFragment.discardChanges(this, REQ_DISCARD)
                    : ConfirmDialogFragment.builder(REQ_DISCARD)
                    .title(getString(R.string.farm_create_discard_title))
                    .message(getString(R.string.farm_create_discard_message))
                    .positive(getString(com.zhimu.slmp.base.R.string.base_action_discard))
                    .negative(getString(com.zhimu.slmp.base.R.string.base_action_keep_editing))
                    .danger(true)
                    .build();
            dialog.showSafely(getSupportFragmentManager());
        });
        vm.getSubmitted().observe(this, farmId -> {
            if (vm.isEdit()) {
                setResult(RESULT_OK, new Intent().putExtra(FarmExtras.RESULT_MESSAGE,
                        getString(R.string.farm_create_resubmitted)));
            } else {
                Bundle extras = new Bundle();
                extras.putLong(RouterPath.EXTRA_FARM_ID, farmId);
                extras.putString(RouterPath.EXTRA_MESSAGE, getString(R.string.farm_create_submitted));
                Router.open(this, RouterPath.F03, extras);
            }
            finish();
        });
    }

    private void renderCategory() {
        Long id = vm.getMainCategoryId().getValue();
        String text = "";
        if (id != null) {
            for (CategoryVO c : categoryList) {
                if (c.getId() == id) {
                    text = c.getName();
                    break;
                }
            }
        }
        binding.etCategory.setText(text, false);
    }

    private void renderLocation(@Nullable LocationResult loc) {
        binding.etAddress.setText(loc == null ? null : loc.getAddress());
        binding.tilAddress.setHelperText(loc == null ? null : getString(R.string.farm_field_address_picked));
    }

    private void highlightAddress() {
        binding.tilAddress.setAlpha(0.4f);
        binding.tilAddress.animate().alpha(1f).setDuration(300).start();
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        FarmUi.setError(binding.tilName, errors, FarmCreateViewModel.F_NAME);
        FarmUi.setError(binding.tilAddress, errors, FarmCreateViewModel.F_ADDRESS, "province", "city", "district",
                "township", "adcode", "longitude", "latitude", "loc_accuracy");
        if (!binding.tilAddress.isErrorEnabled()) {
            renderLocation(vm.getLocation().getValue());
        }
        FarmUi.setError(binding.tilScale, errors, FarmCreateViewModel.F_SCALE);
        FarmUi.setError(binding.tilCategory, errors, FarmCreateViewModel.F_CATEGORY);
        FarmUi.setError(binding.tilContactName, errors, FarmCreateViewModel.F_CONTACT_NAME);
        FarmUi.setError(binding.tilContactPhone, errors, FarmCreateViewModel.F_CONTACT_PHONE);
        String qual = errors == null ? null : errors.get(FarmCreateViewModel.F_QUAL);
        binding.qualRule.setText(qual != null ? qual : getString(R.string.farm_qual_rule));
        binding.qualRule.setTextColor(MaterialColors.getColor(binding.qualRule, qual != null
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private void scrollToField(@Nullable String field) {
        View target;
        if (FarmCreateViewModel.F_NAME.equals(field)) {
            target = binding.tilName;
        } else if (FarmCreateViewModel.F_SCALE.equals(field)) {
            target = binding.tilScale;
        } else if (FarmCreateViewModel.F_CATEGORY.equals(field)) {
            target = binding.tilCategory;
        } else if (FarmCreateViewModel.F_CONTACT_NAME.equals(field)) {
            target = binding.tilContactName;
        } else if (FarmCreateViewModel.F_CONTACT_PHONE.equals(field)) {
            target = binding.tilContactPhone;
        } else if (FarmCreateViewModel.F_QUAL.equals(field)) {
            target = binding.qualTitle;
        } else {
            target = binding.tilAddress;
        }
        FarmUi.scrollTo(binding.scroll, target);
    }
}
