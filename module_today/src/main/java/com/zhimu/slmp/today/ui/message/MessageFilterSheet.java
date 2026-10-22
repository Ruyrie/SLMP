package com.zhimu.slmp.today.ui.message;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.divider.MaterialDivider;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayItemFilterOptionBinding;
import com.zhimu.slmp.today.di.TodayInjector;

import java.util.List;
import java.util.Objects;

/**
 * T06 筛选面板（第 4 级，单选，点选后自动关闭）：
 * “消息类型”列出全部类型 + 7 类（员工只有全部类型、待办提醒、AI 诊断、成员与养殖场、平台通知）；
 * “全部养殖场”最上方为全部养殖场，分隔线下为加入的养殖场与角色，只筛选消息，不切换当前养殖场。
 * 选项从 MessageCenterViewModel（Activity 范围）的 categoryOptions、farmOptions 读取。
 */
public class MessageFilterSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_TYPE = "today_filter_type";

    private MessageCenterViewModel vm;
    @Nullable
    private LinearLayout list;

    public static void show(@NonNull FragmentManager fm, @NonNull String type, @NonNull CharSequence title,
                            @Nullable CharSequence description) {
        Bundle args = baseArgs(null, title, description, null);
        args.putString(ARG_TYPE, type);
        MessageFilterSheet sheet = new MessageFilterSheet();
        sheet.setArguments(args);
        sheet.showSafely(fm, "today_filter_" + type);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), TodayInjector.factory()).get(MessageCenterViewModel.class);
        super.onCreate(savedInstanceState);
    }

    private boolean isFarm() {
        return MessageCenterViewModel.SHEET_FARM.equals(requireArguments().getString(ARG_TYPE));
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(inflater.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_4));
        list = root;
        if (isFarm()) {
            vm.getFarmOptions().observe(this, this::renderFarms);
        } else {
            vm.getCategoryOptions().observe(this, this::renderCategories);
        }
        return root;
    }

    private void renderCategories(@Nullable List<String> options) {
        LinearLayout l = list;
        if (l == null || options == null) {
            return;
        }
        l.removeAllViews();
        String selected = vm.getCategory().getValue();
        for (String code : options) {
            boolean checked = Objects.equals(code.isEmpty() ? null : code, selected);
            addRow(l, MessageCenterViewModel.categoryName(code), null, checked, () -> {
                vm.setCategory(code);
                dismiss();
            });
        }
    }

    private void renderFarms(@Nullable List<FarmBriefVO> farms) {
        LinearLayout l = list;
        if (l == null) {
            return;
        }
        l.removeAllViews();
        FarmBriefVO selected = vm.getFarmFilter().getValue();
        addRow(l, getString(R.string.today_message_all_farms), null, selected == null, () -> {
            vm.setFarm(null);
            dismiss();
        });
        MaterialDivider divider = new MaterialDivider(requireContext());
        l.addView(divider);
        if (farms == null) {
            return;
        }
        for (FarmBriefVO farm : farms) {
            boolean checked = selected != null && selected.getFarmId() == farm.getFarmId();
            addRow(l, farm.getName(), roleText(farm.getRole()), checked, () -> {
                vm.setFarm(farm);
                dismiss();
            });
        }
    }

    @Nullable
    private static String roleText(@Nullable String role) {
        if ("OWNER".equals(role)) {
            return "所有者";
        }
        if ("MANAGER".equals(role)) {
            return "场长";
        }
        if ("STAFF".equals(role)) {
            return "员工";
        }
        return null;
    }

    private void addRow(@NonNull LinearLayout parent, @Nullable CharSequence label, @Nullable CharSequence hint,
                        boolean checked, @NonNull Runnable onClick) {
        TodayItemFilterOptionBinding row = TodayItemFilterOptionBinding.inflate(getLayoutInflater(), parent, false);
        row.label.setText(label);
        row.hint.setText(hint);
        row.hint.setVisibility(hint == null ? View.GONE : View.VISIBLE);
        row.check.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
        row.getRoot().setSelected(checked);
        ClickThrottle.setOnClickListener(row.getRoot(), v -> onClick.run());
        parent.addView(row.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        list = null;
    }
}
