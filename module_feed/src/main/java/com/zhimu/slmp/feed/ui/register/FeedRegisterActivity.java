package com.zhimu.slmp.feed.ui.register;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.UserBriefVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityRegisterBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.vo.FeedOptionBriefVO;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.ui.common.FeedViews;
import com.zhimu.slmp.feed.ui.common.OptionAdapter;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * G02 登记投喂 / 修改投喂 / 投喂记录。成功后 setResult 并以全局提示返回来源页（例如“已登记投喂，待办已自动完成”）。
 */
public class FeedRegisterActivity extends BaseActivity {

    private static final String REQ_DISCARD = "feed_register_discard";
    private static final String REQ_VOID = "feed_register_void";
    private static final int MENU_VOID = 1;

    private FeedActivityRegisterBinding binding;
    private FeedRegisterViewModel vm;
    private OptionAdapter feedAdapter;
    private OptionAdapter executorAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.feed_activity_register);
        vm = obtainViewModel(FeedInjector.factory(), FeedRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.feedTopBar.baseToolbar, getString(R.string.feed_register_title));
        binding.feedTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedTopBar.baseToolbar.inflateMenu(R.menu.feed_menu_register);
        TopAppBarHelper.tintMenu(binding.feedTopBar.baseToolbar);
        binding.feedTopBar.baseToolbar.getMenu().findItem(R.id.feed_menu_more).setVisible(false);
        binding.feedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_more) {
                showMoreMenu();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });

        binding.feedState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, this::renderState);

        feedAdapter = FeedViews.bindDropdown(binding.feedRegFeedInput, vm::onFeedSelectedById);
        vm.getFeedOptions().observe(this, this::renderFeeds);
        vm.getSelectedFeed().observe(this, this::renderSelectedFeed);
        executorAdapter = FeedViews.bindDropdown(binding.feedRegExecutorInput, vm::onExecutorSelectedById);
        vm.getExecutorOptions().observe(this, this::renderExecutors);
        vm.getSelectedExecutor().observe(this, u -> FeedViews.showSelected(binding.feedRegExecutorInput,
                executorAdapter, u == null ? null : u.getUserId()));
        binding.feedRegActualInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onActualChanged();
            }
        });

        // 备注聚焦后才显示字数“9/100”
        binding.feedRegRemarkInput.setOnFocusChangeListener((v, hasFocus) ->
                binding.feedRegRemarkLayout.setCounterEnabled(hasFocus));

        vm.getTaskCard().observe(this, card -> {
            if (card == null) {
                return;
            }
            binding.feedRegTaskCard.setTitle(card.title);
            binding.feedRegTaskCard.setSubtitle(card.subtitle);
            binding.feedRegTaskCard.setStatusTag(card.tag, "已逾期".equals(card.tag) ? StatusTagView.ERROR
                    : StatusTagView.NEUTRAL, "已逾期".equals(card.tag) ? com.zhimu.slmp.base.R.drawable.ic_schedule : 0);
        });
        vm.getMode().observe(this, m -> renderMode());
        vm.getBlockText().observe(this, t -> renderMode());
        vm.getReadonlyNotice().observe(this, t -> renderMode());
        vm.getCanRequestChange().observe(this, c -> renderMode());
        vm.getCanVoid().observe(this, c -> renderMode());
        vm.getReadonlyRows().observe(this, rows -> FeedViews.renderRows(binding.feedRegReadonlyRows, rows));
        vm.getRecord().observe(this, r -> binding.feedRegReadonlyTag.setVisibility(
                r != null && r.isAbnormal() && vm.getMode().getValue() == FeedRegisterMode.READONLY
                        ? View.VISIBLE : View.GONE));
        vm.getStockError().observe(this, e -> binding.feedRegPlanSource.setVisibility(e == null ? View.VISIBLE
                : View.GONE));
        vm.getCanStockIn().observe(this, c -> bindStockInAction());

        binding.feedActionBar.setOnPrimaryClickListener(v -> vm.submit());
        binding.feedActionBar.setOnSecondaryClickListener(v -> vm.requestChange());
        vm.getCanSubmit().observe(this, c -> binding.feedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getSubmitting().observe(this, s -> binding.feedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));

        vm.getFocusActualQty().observe(this, v -> focusActual());
        vm.getCloseWithResult().observe(this, msg -> FeedNav.finishWithMessage(this, msg));
        vm.getShowDiscardDialog().observe(this, v -> FeedViews.showDiscard(this, REQ_DISCARD));
        vm.getShowVoidDialog().observe(this, this::showVoidDialog);
        vm.getOpenChangeRequest().observe(this, args -> Router.open(this, RouterPath.U01, args));
        vm.getOpenStockIn().observe(this, id -> Router.open(this, RouterPath.M03, FeedNav.stockInArgs(id)));
        vm.getRedirectToday().observe(this, penId -> {
            Bundle args = new Bundle();
            if (penId != null) {
                args.putLong(RouterPath.EXTRA_PEN_ID, penId);
            }
            Router.open(this, RouterPath.G01, args);
            finish();
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_VOID, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmVoid();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            vm.saveDraft();
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        switch (actionId) {
            case FeedErrors.ACTION_RESTORE_DRAFT:
                vm.restoreDraft();
                break;
            case FeedErrors.ACTION_REFRESH:
                vm.retry();
                break;
            case FeedErrors.ACTION_RETRY:
                vm.submit();
                break;
            default:
                break;
        }
    }

    private void renderState(@Nullable UiState state) {
        binding.feedState.render(state);
        renderMode();
    }

    /** 按模式切换标题、表单 / 只读卡片、底部按钮与 more_vert */
    private void renderMode() {
        FeedRegisterMode mode = vm.getMode().getValue();
        UiState state = vm.getUiState().getValue();
        boolean content = state == null || state.is(UiState.Status.CONTENT);
        String block = vm.getBlockText().getValue();
        String notice = vm.getReadonlyNotice().getValue();
        boolean readonly = mode == FeedRegisterMode.READONLY;
        boolean edit = mode == FeedRegisterMode.EDIT;

        binding.feedTopBar.baseToolbar.setTitle(readonly ? R.string.feed_register_title_readonly
                : edit ? R.string.feed_register_title_edit : R.string.feed_register_title);
        binding.feedTopBar.baseToolbar.getMenu().findItem(R.id.feed_menu_more)
                .setVisible(content && edit && Boolean.TRUE.equals(vm.getCanVoid().getValue()));

        binding.feedRegBlock.setVisibility(block != null ? View.VISIBLE : View.GONE);
        if (block != null) {
            binding.feedRegBlock.setText(block);
        }
        binding.feedRegReadonlyNotice.setVisibility(readonly && notice != null ? View.VISIBLE : View.GONE);
        if (notice != null) {
            binding.feedRegReadonlyNotice.setText(notice);
        }
        binding.feedRegForm.setVisibility(!readonly && block == null ? View.VISIBLE : View.GONE);
        binding.feedRegReadonlyCard.setVisibility(readonly ? View.VISIBLE : View.GONE);
        FeedRecordDetailVO r = vm.getRecord().getValue();
        binding.feedRegReadonlyTag.setVisibility(readonly && r != null && r.isAbnormal() ? View.VISIBLE : View.GONE);

        boolean requestChange = readonly && Boolean.TRUE.equals(vm.getCanRequestChange().getValue());
        boolean showBar = content && ((!readonly && block == null) || requestChange);
        binding.feedActionBar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        if (requestChange) {
            FeedViews.showRequestChange(binding.feedActionBar, getString(R.string.feed_action_request_change));
        } else if (edit) {
            binding.feedActionBar.setSecondary(null, 0);
            binding.feedActionBar.setPrimary(getString(R.string.feed_register_action_save), 0);
        } else {
            binding.feedActionBar.setSecondary(null, 0);
            binding.feedActionBar.setPrimary(getString(R.string.feed_register_action_confirm),
                    com.zhimu.slmp.base.R.drawable.ic_check);
        }
        binding.feedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(vm.getCanSubmit().getValue()));
    }

    /** 超结存横幅右侧“去入库”：只对所有者、场长显示 */
    private void bindStockInAction() {
        NoticeView error = binding.feedRegOutboundError;
        if (Boolean.TRUE.equals(vm.getCanStockIn().getValue())) {
            error.setAction(getString(R.string.feed_action_stock_in), v -> vm.onStockInClick());
        } else {
            error.setAction(null, null);
        }
    }

    private void renderFeeds(@Nullable List<FeedOptionBriefVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (FeedOptionBriefVO o : list) {
                String sub = o.isSelectable() ? "结存 " + FeedFormat.kg(o.getStockQty())
                        : o.getDisabledReason() != null ? o.getDisabledReason() : "结存 0 千克，不能出库";
                items.add(new OptionAdapter.Item(o.getFeedId(), FeedFormat.orEmpty(o.getFeedName()), sub,
                        o.isLowStock() ? getString(R.string.feed_tag_low_stock) : null, !o.isSelectable()));
            }
        }
        feedAdapter.setItems(items);
        renderSelectedFeed(vm.getSelectedFeed().getValue());
    }

    private void renderSelectedFeed(@Nullable FeedOptionBriefVO feed) {
        FeedViews.showSelected(binding.feedRegFeedInput, feedAdapter, feed == null ? null : feed.getFeedId());
        if (feed != null && feedAdapter.findById(feed.getFeedId()) == null) {
            binding.feedRegFeedInput.setText(FeedFormat.orEmpty(feed.getFeedName()), false);
        }
        binding.feedRegLowStock.setVisibility(feed != null && feed.isLowStock() ? View.VISIBLE : View.GONE);
    }

    private void renderExecutors(@Nullable List<UserBriefVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (UserBriefVO u : list) {
                items.add(new OptionAdapter.Item(u.getUserId(), FeedRegisterViewModel.userLabel(u), null, null,
                        false));
            }
        }
        executorAdapter.setItems(items);
        UserBriefVO current = vm.getSelectedExecutor().getValue();
        FeedViews.showSelected(binding.feedRegExecutorInput, executorAdapter,
                current == null ? null : current.getUserId());
    }

    /** 新增模式加载完成：实际投喂量框聚焦并弹出数字键盘 */
    private void focusActual() {
        binding.feedRegActualInput.post(() -> {
            if (binding.feedRegActualInput.requestFocus()) {
                InputMethodManager imm = getSystemService(InputMethodManager.class);
                if (imm != null) {
                    imm.showSoftInput(binding.feedRegActualInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    private void showMoreMenu() {
        View anchor = binding.feedTopBar.baseToolbar.findViewById(R.id.feed_menu_more);
        MoreMenuHelper.with(anchor != null ? anchor : binding.feedTopBar.baseToolbar)
                .addDanger(MENU_VOID, getString(R.string.feed_register_menu_void), com.zhimu.slmp.base.R.drawable.ic_delete)
                .setOnItemClickListener(id -> {
                    if (id == MENU_VOID) {
                        vm.onVoidMenuClick();
                    }
                })
                .show();
    }

    private void showVoidDialog(@NonNull String message) {
        ConfirmDialogFragment.builder(REQ_VOID)
                .title(getString(R.string.feed_register_void_title))
                .message(message)
                .negative(getString(R.string.feed_action_cancel))
                .positive(getString(R.string.feed_register_void_confirm))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager());
    }
}
