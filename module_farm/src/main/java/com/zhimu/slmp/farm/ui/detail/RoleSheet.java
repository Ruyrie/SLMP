package com.zhimu.slmp.farm.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.radiobutton.MaterialRadioButton;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.MemberDetailVO;

/**
 * F09 修改角色面板（复用 MemberDetailViewModel）：标题“修改李明的角色”，场长 / 员工单选（当前角色预选），底部“保存”。
 */
public class RoleSheet extends BaseBottomSheetDialogFragment {

    private MemberDetailViewModel vm;
    private MaterialRadioButton managerRadio;
    private MaterialRadioButton staffRadio;

    @NonNull
    public static RoleSheet newInstance() {
        RoleSheet sheet = new RoleSheet();
        sheet.setArguments(baseArgs(null, null, null, null));
        return sheet;
    }

    @NonNull
    private MemberDetailViewModel vm() {
        if (vm == null) {
            vm = new ViewModelProvider(requireActivity(), FarmInjector.factory()).get(MemberDetailViewModel.class);
        }
        return vm;
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        MemberDetailVO m = vm().getMember().getValue();
        return getString(R.string.farm_member_role_sheet_title, m == null || m.getName() == null ? "" : m.getName());
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.farm_action_save);
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm().saveRole();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.farm_view_role_options, container, false);
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin);
        view.setPadding(pad, 0, pad, 0);
        managerRadio = view.findViewById(R.id.role_manager_radio);
        staffRadio = view.findViewById(R.id.role_staff_radio);
        view.findViewById(R.id.role_manager).setOnClickListener(v -> vm().onRoleSelected(FarmContextVO.ROLE_MANAGER));
        view.findViewById(R.id.role_staff).setOnClickListener(v -> vm().onRoleSelected(FarmContextVO.ROLE_STAFF));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm().getRoleDraft().observe(getViewLifecycleOwner(), role -> {
            boolean manager = FarmContextVO.ROLE_MANAGER.equals(role);
            managerRadio.setChecked(manager);
            staffRadio.setChecked(!manager);
        });
        vm().getSaving().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm().getSheetDone().observe(getViewLifecycleOwner(), v -> dismiss());
    }
}
