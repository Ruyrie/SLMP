package com.zhimu.slmp.farm.ui.detail;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmSheetPensBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.ui.PenChips;

import java.util.List;
import java.util.Set;

/**
 * F09 调整负责栏舍面板（复用 MemberDetailViewModel）：多选 Chip；取消某栏舍后若将没有负责员工，Chip 区下方显示 warning 提示；底部“保存”。
 */
public class PenSheet extends BaseBottomSheetDialogFragment {

    private FarmSheetPensBinding binding;
    private MemberDetailViewModel vm;

    @NonNull
    public static PenSheet newInstance() {
        PenSheet sheet = new PenSheet();
        sheet.setArguments(baseArgs(null, null, null, null));
        return sheet;
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.farm_member_pen_sheet_title);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.farm_action_save);
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.savePens();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FarmSheetPensBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), FarmInjector.factory()).get(MemberDetailViewModel.class);
        FarmContextVO ctx = CoreInjector.get(requireContext()).farmContext().current();
        binding.penEmpty.setText(getString(R.string.farm_invite_no_pens,
                ctx == null || ctx.getName() == null ? getString(R.string.farm_this_farm) : ctx.getName()));
        vm.getPenOptions().observe(getViewLifecycleOwner(), list -> render());
        vm.getPenDraft().observe(getViewLifecycleOwner(), ids -> render());
        vm.getPenWarning().observe(getViewLifecycleOwner(), w -> {
            binding.penWarning.setText(w);
            binding.penWarning.setVisibility(TextUtils.isEmpty(w) ? View.GONE : View.VISIBLE);
        });
        vm.getSaving().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getSheetDone().observe(getViewLifecycleOwner(), v -> dismiss());
    }

    private void render() {
        List<com.zhimu.slmp.farm.model.vo.PenOptionVO> options = vm.getPenOptions().getValue();
        Set<Long> draft = vm.getPenDraft().getValue();
        PenChips.render(binding.penChips, options, draft, vm::togglePen);
        int n = draft == null ? 0 : draft.size();
        binding.penHint.setText("可多选，已选 " + n + " 个；一个栏舍可以有多名负责员工");
        boolean empty = options == null || options.isEmpty();
        binding.penEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
