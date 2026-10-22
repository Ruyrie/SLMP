package com.zhimu.slmp.account.ui.afterrecovery;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityAfterRecoveryBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.recoverycode.RecoveryCodeUi;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * A06 找回后首次登录：两步共享 Activity 范围的 AfterRecoveryViewModel。强制步骤，返回键把 App 退到后台。
 */
public class AfterRecoveryActivity extends BaseActivity {

    private static final String TAG_STEP1 = "step1";
    private static final String TAG_STEP2 = "step2";
    private static final String ACTION_VIEW_IMAGE = "view_image";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private AfterRecoveryViewModel vm;
    private AccountActivityAfterRecoveryBinding binding;
    @Nullable
    private Uri savedUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), AfterRecoveryViewModel.class);
        binding = AccountActivityAfterRecoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        observeBase(vm);
        binding.accountAfterTopBar.baseToolbar.setNavigationIcon(null);

        vm.getStep().observe(this, this::renderStep);
        vm.getProgress().observe(this, p -> binding.accountAfterProgress.setProgressCompat(p == null ? 50 : p, true));
        vm.getCanSubmit().observe(this, binding.accountAfterBar::setPrimaryEnabled);
        vm.getSubmitting().observe(this, s -> binding.accountAfterBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        binding.accountAfterBar.setOnPrimaryClickListener(v -> vm.onPrimaryClick());

        vm.getCopyText().observe(this, code -> {
            if (RecoveryCodeUi.copy(this, code)) {
                showMessage(getString(R.string.account_code_copied));
            }
        });
        vm.getSaveImage().observe(this, code -> disposables.add(RecoveryCodeUi.saveImage(this, code, uri -> {
            savedUri = uri;
            showMessage(UiMessage.withAction(getString(R.string.account_code_image_saved),
                    getString(R.string.account_code_image_view), ACTION_VIEW_IMAGE));
        }, () -> showMessage(getString(R.string.account_code_image_failed)))));
        vm.getDone().observe(this, d -> NextStepRouter.openMain(this, AfterRecoveryViewModel.MSG_DONE));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    private void renderStep(@Nullable Integer step) {
        boolean second = step != null && step == 2;
        binding.accountAfterTopBar.baseToolbar.setTitle(second ? R.string.account_code_title_new
                : R.string.account_after_title_password);
        binding.accountAfterStep.setText(second ? R.string.account_after_step2 : R.string.account_after_step1);
        binding.accountAfterNext.setVisibility(second ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.accountAfterBar.setPrimaryText(getString(second ? R.string.account_enter_app : R.string.account_after_submit));
        String tag = second ? TAG_STEP2 : TAG_STEP1;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.account_after_container);
        if (current != null && tag.equals(current.getTag())) {
            return;
        }
        Fragment f = second ? new AfterRecoveryCodeFragment() : new AfterRecoveryPasswordFragment();
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.account_after_container, f, tag)
                .commitAllowingStateLoss();
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
