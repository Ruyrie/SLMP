package com.zhimu.slmp.breed.ui.mating;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedSheetPregCheckBinding;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.ui.common.PregCheckHost;

import java.time.LocalDate;

/**
 * “登记妊检”底部面板（B03 待妊检、B06 头部“登记妊检”与妊检记录“编辑”）：复用宿主 ViewModel（PregCheckHost），
 * 两个字段：妊检日期、妊检结果；“确认结果”提交中显示加载，成功后由宿主关闭面板并显示 Snackbar。
 * 下拉、点遮罩或返回键关闭面板，不保存。
 */
public class PregCheckSheet extends BaseBottomSheetDialogFragment {

    private static final String REQ_DATE = "breed_check_date";

    private PregCheckHost host;

    @NonNull
    public static PregCheckSheet newInstance(boolean edit) {
        PregCheckSheet s = new PregCheckSheet();
        s.setArguments(baseArgs(null, null, null, null));
        s.getArguments().putBoolean("edit", edit);
        return s;
    }

    public static void show(@NonNull FragmentActivity activity, boolean edit) {
        newInstance(edit).showSafely(activity.getSupportFragmentManager(), PregCheckSheet.class.getName());
    }

    public static void dismissIn(@NonNull FragmentManager fm) {
        PregCheckSheet s = (PregCheckSheet) fm.findFragmentByTag(PregCheckSheet.class.getName());
        if (s != null) {
            s.dismissAllowingStateLoss();
        }
    }

    private boolean isEdit() {
        return getArguments() != null && getArguments().getBoolean("edit");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        host = ((PregCheckHost.Provider) requireActivity()).pregCheckHost();
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(isEdit() ? R.string.breed_check_edit_title : R.string.breed_check_title);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(isEdit() ? R.string.breed_action_save_edit : R.string.breed_check_confirm);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        BreedSheetPregCheckBinding b = BreedSheetPregCheckBinding.inflate(inflater, container, false);
        b.setLifecycleOwner(this);
        b.setHost(host);
        ClickThrottle.setOnClickListener(b.breedCheckDateInput, v -> pickDate());
        b.breedCheckDateLayout.setEndIconOnClickListener(v -> pickDate());
        ClickThrottle.setOnClickListener(b.breedCheckPositive, v -> host.onCheckResultPicked(BreedCodes.POSITIVE));
        ClickThrottle.setOnClickListener(b.breedCheckNegative, v -> host.onCheckResultPicked(BreedCodes.NEGATIVE));
        getChildFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> host.onCheckDatePicked(DateTimePicker.getDate(result)));
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 成功后的关闭由宿主页面观察 getDismissCheckSheet 后调用 dismissIn（一次性事件只交给一个观察者）
        host.getCheckSubmitting().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
    }

    @Override
    protected void onPrimaryButtonClick() {
        host.submitCheck();
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(host.getCheckDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(host.checkMinDate(), DateFormatter.today())
                .show(getChildFragmentManager());
    }
}
