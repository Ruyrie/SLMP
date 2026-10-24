package com.zhimu.slmp.account.ui.mine;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountSheetEditNameBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;

/**
 * A07 修改个人信息底部面板（第 4 级）：头像更换/恢复默认 + 姓名；通过父 Fragment 获取 MineViewModel；
 * 关闭不保存输入。头像用系统照片选择器，不申请存储权限。
 */
public class EditNameBottomSheet extends BaseBottomSheetDialogFragment {

    private AccountSheetEditNameBinding binding;
    private MineViewModel vm;

    /** 系统照片选择器（单选图片）；选好后关闭面板并交给 ViewModel 上传 */
    private final ActivityResultLauncher<PickVisualMediaRequest> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onAvatarPicked);

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.account_edit_name_title);
    }

    @Nullable
    @Override
    protected CharSequence getSheetDescription() {
        return getString(R.string.account_edit_name_desc);
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), AccountInjector.factory())
                .get(MineViewModel.class);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = AccountSheetEditNameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        if (binding.accountEditNameField.getEditText() != null) {
            binding.accountEditNameField.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
            binding.accountEditNameField.getEditText().addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    vm.clearNameError();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
            FormBinder.showKeyboard(binding.accountEditNameField.getEditText());
            binding.accountEditNameField.getEditText().requestFocus();
        }
        vm.getNameError().observe(getViewLifecycleOwner(), err -> binding.accountEditNameField.setError(err));
        vm.getDismissNameSheet().observe(getViewLifecycleOwner(), d -> dismissAllowingStateLoss());
        ClickThrottle.setOnClickListener(binding.accountEditNameCancel, v -> dismiss());
        ClickThrottle.setOnClickListener(binding.accountEditNameSave, v -> vm.saveName());
        ClickThrottle.setOnClickListener(binding.accountEditAvatarPick, v -> openAvatarPicker());
        ClickThrottle.setOnClickListener(binding.accountEditAvatarClear, v -> clearAvatar());
    }

    private void openAvatarPicker() {
        if (vm == null || Boolean.TRUE.equals(vm.getUploadingAvatar().getValue())) {
            return;
        }
        avatarPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onAvatarPicked(@Nullable Uri uri) {
        if (uri == null || vm == null) {
            return;
        }
        dismissAllowingStateLoss();
        vm.uploadAvatar(uri);
    }

    private void clearAvatar() {
        if (vm == null || Boolean.TRUE.equals(vm.getUploadingAvatar().getValue())) {
            return;
        }
        dismissAllowingStateLoss();
        vm.clearAvatar();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
