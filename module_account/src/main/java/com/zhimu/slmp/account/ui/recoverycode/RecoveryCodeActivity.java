package com.zhimu.slmp.account.ui.recoverycode;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityRecoveryCodeBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * A04 保存恢复码：注册成功后、冷启动 next_step 为 SAVE_RECOVERY_CODE、A08 重新生成后进入。
 * 强制步骤：顶部栏没有返回按钮，系统返回键把 App 退到后台，下次打开回到本步骤。
 */
public class RecoveryCodeActivity extends BaseActivity {

    private static final String ACTION_VIEW_IMAGE = "view_image";
    /** “进入智牧通”后首页的提示（找回场景） */
    public static final String MSG_RECOVERY_DONE = "密码已修改，新的恢复码已生效";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private RecoveryCodeViewModel vm;
    private AccountActivityRecoveryCodeBinding binding;
    @Nullable
    private Uri savedUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), RecoveryCodeViewModel.class);
        binding = AccountActivityRecoveryCodeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        observeBase(vm);
        binding.accountCodeTopBar.baseToolbar.setNavigationIcon(null);
        binding.accountCodeTopBar.baseToolbar.setTitle(R.string.account_code_title);

        RecoveryCodeUi.bindBody(this, binding.accountCodeBody, vm.getDescText(), vm.getCodeGroups(), vm.savedChecked,
                Transformations.map(vm.getRecoveryCode(),
                        c -> c == null || !RecoveryCodeViewModel.SCENE_REGENERATE.equals(c.getScene())),
                vm::copyCode, vm::saveImage);
        vm.getRecoveryCode().observe(this, this::renderTitle);
        vm.getUiState().observe(this, binding.accountCodeState::render);
        binding.accountCodeState.setOnRetryListener(v -> vm.retry());
        vm.getPrimaryText().observe(this, binding.accountCodeBar::setPrimaryText);
        vm.getCanSubmit().observe(this, binding.accountCodeBar::setPrimaryEnabled);
        vm.getSubmitting().observe(this, s -> binding.accountCodeBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        binding.accountCodeBar.setOnPrimaryClickListener(v -> vm.confirm());

        vm.getCopyText().observe(this, code -> {
            if (RecoveryCodeUi.copy(this, code)) {
                showMessage(getString(R.string.account_code_copied));
            }
        });
        vm.getSaveImage().observe(this, this::saveImage);
        vm.getDone().observe(this, this::onDone);
        vm.getNextStep().observe(this, step -> NextStepRouter.dispatch(this, step));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    private void renderTitle(@Nullable RecoveryCodeVO c) {
        boolean recovery = c != null && RecoveryCodeViewModel.SCENE_RECOVERY.equals(c.getScene()) && !c.isRegenerated();
        binding.accountCodeTopBar.baseToolbar.setTitle(recovery ? R.string.account_code_title_new : R.string.account_code_title);
    }

    private void saveImage(@NonNull RecoveryCodeVO vo) {
        disposables.add(RecoveryCodeUi.saveImage(this, vo, uri -> {
            savedUri = uri;
            showMessage(UiMessage.withAction(getString(R.string.account_code_image_saved),
                    getString(R.string.account_code_image_view), ACTION_VIEW_IMAGE));
        }, () -> showMessage(getString(R.string.account_code_image_failed))));
    }

    private void onDone(@NonNull String scene) {
        if (RecoveryCodeViewModel.SCENE_REGENERATE.equals(scene)) {
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }
        NextStepRouter.openMain(this, RecoveryCodeViewModel.SCENE_RECOVERY.equals(scene) ? MSG_RECOVERY_DONE : null);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (ACTION_VIEW_IMAGE.equals(actionId)) {
            RecoveryCodeUi.view(this, savedUri);
        }
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
