package com.zhimu.slmp.stock.ui.stocktake;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityStocktakeBinding;
import com.zhimu.slmp.stock.databinding.StockItemStocktakeCardBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StocktakeSheetVO;
import com.zhimu.slmp.stock.ui.common.SimpleTextWatcher;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.util.StockQtyFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M04 盘点与报损：卡片按 feed_id 复用（报损后重取盘点表只更新账面，不清空已录入的实盘）；
 * 实盘框停止输入 300 ms 或失焦后计算差异；确认对话框“确认”后提交，成功 RESULT_OK 返回 M01。
 */
public class StocktakeActivity extends BaseActivity {

    private static final String REQ_CONFIRM = "stock_m04_confirm";
    private static final String REQ_BOOK = "stock_m04_book";
    private static final String REQ_DISCARD = "stock_m04_discard";
    private static final long DEBOUNCE_MS = 300;

    private StockActivityStocktakeBinding binding;
    private StocktakeViewModel vm;
    private final Map<Long, StockItemStocktakeCardBinding> cards = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, Runnable> pending = new LinkedHashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityStocktakeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), StocktakeViewModel.class);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_stocktake_title));
        binding.stockTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_history);
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_history)
                .setTitle(R.string.stock_stocktake_history_title);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            startActivity(new android.content.Intent(this, StocktakeHistoryActivity.class));
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });

        binding.stockState.setOnRetryListener(v -> vm.retry());
        binding.stockState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_inventory_2,
                getString(R.string.stock_feed_empty), null, null, 0,
                com.zhimu.slmp.base.widget.StateLayout.EMPTY_ACTION_TONAL, null);
        vm.getUiState().observe(this, s -> {
            binding.stockState.render(s);
            binding.stockActionBar.setVisibility(s != null && s.is(UiState.Status.CONTENT) ? View.VISIBLE : View.GONE);
        });
        vm.getInfoText().observe(this, binding.stockTakeInfo::setText);
        vm.getSheet().observe(this, this::renderSheet);
        vm.getDiffs().observe(this, d -> renderResults());
        vm.getActualErrors().observe(this, e -> renderResults());
        vm.getRestoredActual().observe(this, this::fillActual);

        binding.stockActionBar.setOnPrimaryClickListener(v -> {
            flushPending();
            vm.confirm();
        });
        vm.getSubmitting().observe(this, s -> {
            boolean loading = Boolean.TRUE.equals(s);
            binding.stockActionBar.setPrimaryLoading(loading);
            ConfirmDialogFragment.setLoading(getSupportFragmentManager(), REQ_CONFIRM, loading);
        });
        vm.getShowConfirm().observe(this, text -> ConfirmDialogFragment.builder(REQ_CONFIRM)
                .title(getString(R.string.stock_stocktake_confirm_title))
                .message(text)
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_action_confirm))
                .keepOpenOnConfirm()
                .build().showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFIRM, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.doConfirm();
            }
        });
        vm.getStocktakeDone().observe(this, msg -> {
            ConfirmDialogFragment.dismiss(getSupportFragmentManager(), REQ_CONFIRM);
            StockNav.finishWithMessage(this, UiMessage.of(msg));
        });
        vm.getBookChanged().observe(this, msg -> {
            ConfirmDialogFragment.dismiss(getSupportFragmentManager(), REQ_CONFIRM);
            ConfirmDialogFragment.builder(REQ_BOOK)
                    .title(msg)
                    .positive(getString(R.string.stock_action_refresh))
                    .negative(getString(R.string.stock_action_cancel))
                    .build().showSafely(getSupportFragmentManager());
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_BOOK, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onBookRefresh();
            }
        });
        vm.getShowLoss().observe(this, id -> FeedLossSheet.newInstance().showSafely(getSupportFragmentManager(),
                FeedLossSheet.TAG));
        vm.getLossDone().observe(this, msg -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(FeedLossSheet.TAG);
            if (f instanceof DialogFragment) {
                ((DialogFragment) f).dismissAllowingStateLoss();
            }
            showMessage(msg);
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            flushPending();
            vm.saveDraft();
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (StockFormViewModel.ACTION_RESTORE_DRAFT.equals(actionId)) {
            vm.restoreDraft();
        } else if (StockFormViewModel.ACTION_RETRY.equals(actionId)) {
            vm.retrySubmit();
        }
    }

    /** 首次创建卡片；之后只更新账面结存文字 */
    private void renderSheet(@Nullable StocktakeSheetVO s) {
        if (s == null || s.items == null) {
            return;
        }
        boolean same = cards.size() == s.items.size();
        if (same) {
            for (StocktakeSheetVO.Item i : s.items) {
                if (!cards.containsKey(i.feedId)) {
                    same = false;
                }
            }
        }
        if (!same) {
            cards.clear();
            binding.stockTakeCards.removeAllViews();
        }
        Map<Long, String> actual = vm.getActualQty().getValue();
        for (StocktakeSheetVO.Item i : s.items) {
            String unit = i.unitText == null ? "千克" : i.unitText;
            StockItemStocktakeCardBinding c = cards.get(i.feedId);
            if (c == null) {
                c = StockItemStocktakeCardBinding.inflate(getLayoutInflater(), binding.stockTakeCards, false);
                cards.put(i.feedId, c);
                binding.stockTakeCards.addView(c.getRoot());
                long id = i.feedId;
                StockItemStocktakeCardBinding card = c;
                c.stockTakeName.setText(i.feedName);
                c.stockTakeActualLayout.setSuffixText(unit);
                if (actual != null && actual.get(id) != null) {
                    c.stockTakeActual.setText(actual.get(id));
                }
                c.stockTakeActual.addTextChangedListener(new SimpleTextWatcher(t -> schedule(id, t)));
                c.stockTakeActual.setOnFocusChangeListener((v, focus) -> {
                    if (!focus) {
                        cancel(id);
                        vm.onActualChanged(id, card.stockTakeActual.getText() == null ? ""
                                : card.stockTakeActual.getText().toString());
                    }
                });
                c.stockTakeLoss.setOnClickListener(v -> vm.openLoss(id));
            }
            c.stockTakeBook.setText(getString(R.string.stock_stocktake_book, StockQtyFormatter.qty(i.bookQty, unit)));
        }
        renderResults();
    }

    private void fillActual(@Nullable Map<Long, String> actual) {
        if (actual == null) {
            return;
        }
        for (Map.Entry<Long, StockItemStocktakeCardBinding> e : cards.entrySet()) {
            String v = actual.get(e.getKey());
            e.getValue().stockTakeActual.setText(v == null ? "" : v);
        }
    }

    private void renderResults() {
        Map<Long, StocktakeViewModel.Diff> diffs = vm.getDiffs().getValue();
        Map<Long, String> errors = vm.getActualErrors().getValue();
        for (Map.Entry<Long, StockItemStocktakeCardBinding> e : cards.entrySet()) {
            StockItemStocktakeCardBinding c = e.getValue();
            String err = errors == null ? null : errors.get(e.getKey());
            c.stockTakeActualLayout.setError(err);
            c.stockTakeActualLayout.setErrorEnabled(err != null);
            StocktakeViewModel.Diff d = diffs == null ? null : diffs.get(e.getKey());
            if (d == null) {
                c.stockTakeResult.setVisibility(View.GONE);
                continue;
            }
            boolean show = c.stockTakeResult.getVisibility() != View.VISIBLE;
            c.stockTakeResult.setVisibility(View.VISIBLE);
            if (show) {
                c.stockTakeResult.setAlpha(0f);
                c.stockTakeResult.animate().alpha(1f).setDuration(150).start();
            }
            c.stockTakeResultIcon.setImageResource(StockEnums.RESULT_GAIN.equals(d.result)
                    ? com.zhimu.slmp.base.R.drawable.ic_trending_up
                    : StockEnums.RESULT_LOSS.equals(d.result) ? com.zhimu.slmp.base.R.drawable.ic_trending_down
                    : com.zhimu.slmp.base.R.drawable.ic_check);
            c.stockTakeResultQty.setText(d.qtyText);
            c.stockTakeResultAmount.setText(d.amountText);
            c.stockTakeResultAmount.setVisibility(d.amountText == null ? View.GONE : View.VISIBLE);
        }
    }

    private void schedule(long feedId, @NonNull String text) {
        cancel(feedId);
        Runnable r = () -> {
            pending.remove(feedId);
            vm.onActualChanged(feedId, text);
        };
        pending.put(feedId, r);
        handler.postDelayed(r, DEBOUNCE_MS);
    }

    private void cancel(long feedId) {
        Runnable r = pending.remove(feedId);
        if (r != null) {
            handler.removeCallbacks(r);
        }
    }

    /** 立即处理还在防抖中的输入（点“确认盘点”或离开页面时） */
    private void flushPending() {
        for (Map.Entry<Long, StockItemStocktakeCardBinding> e : cards.entrySet()) {
            if (pending.containsKey(e.getKey())) {
                cancel(e.getKey());
                CharSequence t = e.getValue().stockTakeActual.getText();
                vm.onActualChanged(e.getKey(), t == null ? "" : t.toString());
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
