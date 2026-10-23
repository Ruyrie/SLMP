package com.zhimu.slmp.stock.ui.drugstock;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockSheetNearDaysBinding;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.ui.common.SimpleTextWatcher;
import com.zhimu.slmp.stock.util.StockSettingDelegate;

import java.util.Map;

/**
 * “临期天数设置”底部面板（M05 菜单、M09“临期天数 · 修改”共用）：数据与保存交给宿主 Activity（{@link Host}），
 * 保存成功后由宿主关闭面板并提示“已保存临期天数设置”；面板中的保存与 M09 表单互不影响。
 */
public class NearDaysSheet extends BaseBottomSheetDialogFragment {

    public static final String TAG = "stock_near_days";
    private static final String ARG_FOCUS = "focus_category";

    /** 宿主 */
    public interface Host {
        @NonNull
        StockSettingDelegate nearDaysDelegate();

        @NonNull
        LiveData<Boolean> nearDaysSaving();

        void onSaveNearDays();
    }

    private StockSheetNearDaysBinding binding;

    @NonNull
    public static NearDaysSheet newInstance(@NonNull CharSequence title, @NonNull CharSequence description,
                                            @Nullable String focusCategory) {
        NearDaysSheet s = new NearDaysSheet();
        Bundle args = baseArgs(null, title, description, null);
        args.putString(ARG_FOCUS, focusCategory);
        s.setArguments(args);
        return s;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = StockSheetNearDaysBinding.inflate(inflater, container, false);
        if (!(requireActivity() instanceof Host)) {
            return binding.getRoot();
        }
        Host host = (Host) requireActivity();
        StockSettingDelegate d = host.nearDaysDelegate();
        binding.stockNearVet.setText(d.getNearVet().getValue());
        binding.stockNearVaccine.setText(d.getNearVaccine().getValue());
        binding.stockNearDis.setText(d.getNearDisinfectant().getValue());
        binding.stockNearVet.addTextChangedListener(new SimpleTextWatcher(t -> d.getNearVet().setValue(t)));
        binding.stockNearVaccine.addTextChangedListener(new SimpleTextWatcher(t -> d.getNearVaccine().setValue(t)));
        binding.stockNearDis.addTextChangedListener(new SimpleTextWatcher(t -> d.getNearDisinfectant().setValue(t)));
        binding.stockNearCancel.setOnClickListener(v -> dismiss());
        binding.stockNearSave.setOnClickListener(v -> host.onSaveNearDays());
        String focus = requireArguments().getString(ARG_FOCUS);
        View target = StockEnums.CAT_VACCINE.equals(focus) ? binding.stockNearVaccine
                : StockEnums.CAT_DISINFECTANT.equals(focus) ? binding.stockNearDis : binding.stockNearVet;
        if (focus != null && savedInstanceState == null) {
            target.post(target::requestFocus);
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!(requireActivity() instanceof Host)) {
            return;
        }
        Host host = (Host) requireActivity();
        host.nearDaysDelegate().getErrors().observe(getViewLifecycleOwner(), this::renderErrors);
        host.nearDaysSaving().observe(getViewLifecycleOwner(),
                s -> ButtonLoading.setLoading(binding.stockNearSave, Boolean.TRUE.equals(s)));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void renderErrors(@Nullable Map<String, String> e) {
        if (binding == null) {
            return;
        }
        error(binding.stockNearVetLayout, e == null ? null : e.get(StockSettingDelegate.F_VET));
        error(binding.stockNearVaccineLayout, e == null ? null : e.get(StockSettingDelegate.F_VACCINE));
        error(binding.stockNearDisLayout, e == null ? null : e.get(StockSettingDelegate.F_DISINFECTANT));
    }

    private static void error(@NonNull TextInputLayout layout, @Nullable String msg) {
        layout.setError(msg);
        layout.setErrorEnabled(!TextUtils.isEmpty(msg));
    }

    /** 面板说明 */
    @NonNull
    public static String description(@NonNull android.content.Context c) {
        return c.getString(R.string.stock_near_days_desc);
    }
}
