package com.zhimu.slmp.farm.ui.info;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmSheetBasicFieldBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.util.CountUnitText;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;

/**
 * F05 基本信息单字段修改面板（复用 FarmInfoViewModel）：标题“修改联系人”等；联系电话显示完整号码并按 3-4-4 分组；
 * 规模的标签为“设计存栏”、后缀“头 / 只”；简介为多行输入、计数 200；“保存”随键盘上移，成功后关闭并提示“已保存联系人”。
 */
public class BasicFieldSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_FIELD = "field";

    private FarmSheetBasicFieldBinding binding;
    private FarmInfoViewModel vm;

    @NonNull
    public static BasicFieldSheet newInstance(@NonNull String field) {
        Bundle args = baseArgs(null, null, null, null);
        args.putString(ARG_FIELD, field);
        BasicFieldSheet sheet = new BasicFieldSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    private String field() {
        String f = requireArguments().getString(ARG_FIELD);
        return f == null ? FarmInfoViewModel.FIELD_CONTACT_NAME : f;
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        switch (field()) {
            case FarmInfoViewModel.FIELD_CONTACT_PHONE:
                return getString(R.string.farm_edit_contact_phone);
            case FarmInfoViewModel.FIELD_SCALE:
                return getString(R.string.farm_edit_scale);
            case FarmInfoViewModel.FIELD_INTRO:
                return getString(R.string.farm_edit_intro);
            default:
                return getString(R.string.farm_edit_contact_name);
        }
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.farm_action_save);
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.saveField();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FarmSheetBasicFieldBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), FarmInjector.factory()).get(FarmInfoViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        configureInput();

        vm.getSaving().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getSaved().observe(getViewLifecycleOwner(), f -> {
            String label;
            switch (f == null ? "" : f) {
                case FarmInfoViewModel.FIELD_CONTACT_PHONE:
                    label = getString(R.string.farm_info_contact_phone);
                    break;
                case FarmInfoViewModel.FIELD_SCALE:
                    label = getString(R.string.farm_row_scale);
                    break;
                case FarmInfoViewModel.FIELD_INTRO:
                    label = getString(R.string.farm_info_intro);
                    break;
                default:
                    label = getString(R.string.farm_field_contact_name);
                    break;
            }
            String text = "已保存" + label;
            BaseActivity host = getActivity() instanceof BaseActivity ? (BaseActivity) getActivity() : null;
            dismiss();
            if (host != null) {
                host.showMessage(text);
            }
        });
        binding.etValue.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void configureInput() {
        switch (field()) {
            case FarmInfoViewModel.FIELD_CONTACT_PHONE:
                binding.tilValue.setHint(getString(R.string.farm_info_contact_phone));
                binding.etValue.setInputType(InputType.TYPE_CLASS_PHONE);
                PhoneTextWatcher.attach(binding.etValue);
                break;
            case FarmInfoViewModel.FIELD_SCALE:
                binding.tilValue.setHint(getString(R.string.farm_field_scale_design));
                binding.etValue.setInputType(InputType.TYPE_CLASS_NUMBER);
                String unit = vm.getDetail().getValue() == null ? null : vm.getDetail().getValue().getCountUnit();
                binding.tilValue.setSuffixText(CountUnitText.of(unit));
                break;
            case FarmInfoViewModel.FIELD_INTRO:
                binding.tilValue.setHint(getString(R.string.farm_info_intro));
                binding.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                binding.etValue.setMinLines(3);
                binding.etValue.setSingleLine(false);
                binding.tilValue.setCounterEnabled(true);
                binding.tilValue.setCounterMaxLength(FarmRules.INTRO_MAX);
                break;
            default:
                binding.tilValue.setHint(getString(R.string.farm_field_contact_name));
                binding.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
