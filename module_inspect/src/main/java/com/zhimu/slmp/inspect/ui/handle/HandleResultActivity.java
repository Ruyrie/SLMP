package com.zhimu.slmp.inspect.ui.handle;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectActivityHandleBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.inspect.model.vo.HandleVO;
import com.zhimu.slmp.inspect.ui.common.InspectNav;

/**
 * I07 登记 / 修改处理结果（所有者、场长）：入口为 I06 底部按钮；保存后返回 I06（setResult 刷新），转用药进入 D04。
 */
public class HandleResultActivity extends BaseActivity {

    private static final String REQ_CLOSE = "inspect_handle_close";
    private static final String REQ_DISCARD = "inspect_handle_discard";

    private InspectActivityHandleBinding binding;
    private HandleResultViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.inspect_activity_handle);
        vm = obtainViewModel(InspectInjector.factory(), HandleResultViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());
        vm.getProgress().observe(this, p -> binding.inspectTotalProgress.setProgressCompat(p == null ? 0 : p, true));
        vm.getDetail().observe(this, this::renderUrgency);
        TextWatcher recalc = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.recalc();
            }
        };
        binding.inspectCuredInput.addTextChangedListener(recalc);
        binding.inspectCulledInput.addTextChangedListener(recalc);

        vm.getAiMatch().observe(this, m -> {
            int id = HandleVO.MATCH.equals(m) ? R.id.inspect_match_yes
                    : HandleVO.NOT_MATCH.equals(m) ? R.id.inspect_match_no : View.NO_ID;
            if (id == View.NO_ID) {
                binding.inspectAiMatch.clearChecked();
            } else if (binding.inspectAiMatch.getCheckedButtonId() != id) {
                binding.inspectAiMatch.check(id);
            }
        });
        binding.inspectAiMatch.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                vm.setAiMatch(checkedId == R.id.inspect_match_yes ? HandleVO.MATCH : HandleVO.NOT_MATCH);
            }
        });
        ClickThrottle.setOnClickListener(binding.inspectToMedication, v -> vm.convertToMedication());
        ClickThrottle.setOnClickListener(binding.inspectViewMedication, v -> {
            DiagnosisDetailVO d = vm.getDetail().getValue();
            Bundle extras = new Bundle();
            if (d != null && d.getReport() != null) {
                extras.putLong(RouterPath.EXTRA_BATCH_ID, d.getReport().getBatchId());
            }
            Router.open(this, RouterPath.D05, extras);
        });

        binding.inspectActionBar.setOnPrimaryClickListener(v -> vm.save());
        vm.getSubmitting().observe(this, s -> binding.inspectActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getShowCloseConfirm().observe(this, sick -> ConfirmDialogFragment.builder(REQ_CLOSE)
                .title(getString(R.string.inspect_handle_close_title))
                .message("治愈和死淘合计已达到发病数量 " + sick + " 头，保存后记录自动结案，不能再修改。")
                .negative(getString(R.string.inspect_handle_close_keep))
                .positive(getString(R.string.inspect_handle_close_confirm))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CLOSE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmClose();
            }
        });
        vm.getNavEvent().observe(this, t -> {
            setResult(RESULT_OK);
            InspectNav.open(this, null, t);
        });
        vm.getFinishEvent().observe(this, m -> InspectNav.finishWithMessage(this, m));
        vm.getShowDiscard().observe(this, v -> ConfirmDialogFragment.builder(REQ_DISCARD)
                .title(getString(R.string.inspect_handle_discard_title))
                .negative(getString(R.string.inspect_action_keep_editing))
                .positive(getString(R.string.inspect_action_discard))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
    }

    private void renderUrgency(@Nullable DiagnosisDetailVO d) {
        DiagnosisResultVO dx = d == null ? null : d.getDiagnosis();
        StatusTagView tag = binding.inspectHandleUrgency;
        if (dx == null) {
            tag.setVisibility(View.GONE);
            return;
        }
        String u = dx.getUrgency() == null ? "" : dx.getUrgency();
        int type = DiagnosisResultVO.URGENCY_URGENT.equals(u) ? StatusTagView.ERROR
                : DiagnosisResultVO.URGENCY_ATTENTION.equals(u) ? StatusTagView.WARNING : StatusTagView.NEUTRAL;
        tag.setStatus(dx.getUrgencyText(), type);
        tag.setTagIcon(DiagnosisResultVO.URGENCY_URGENT.equals(u) ? com.zhimu.slmp.base.R.drawable.ic_emergency
                : DiagnosisResultVO.URGENCY_ATTENTION.equals(u) ? com.zhimu.slmp.base.R.drawable.ic_priority_high : 0);
        tag.setVisibility(View.VISIBLE);
    }
}
