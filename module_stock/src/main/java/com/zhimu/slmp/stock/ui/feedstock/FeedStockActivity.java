package com.zhimu.slmp.stock.ui.feedstock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityFeedStockBinding;
import com.zhimu.slmp.stock.databinding.StockItemFeedCardBinding;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.ui.flow.StockFlowListActivity;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.List;

/**
 * M01 饲料库存。子页面（M02、M03、M04、M10、流水）以 RESULT_OK 返回时刷新。
 */
public class FeedStockActivity extends BaseActivity {

    private static final int MENU_ADD = 1;
    private static final int MENU_OTHER_OUT = 2;
    private static final int MENU_FLOWS = 3;

    private StockActivityFeedStockBinding binding;
    private FeedStockViewModel vm;
    private ActivityResultLauncher<Intent> childLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityFeedStockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), FeedStockViewModel.class);
        observeBase(vm);
        childLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.refresh();
            }
        });

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_feed_title));
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_feed_stock);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.stock_menu_stocktake) {
                StockNav.open(this, childLauncher, RouterPath.M04, null);
            } else if (item.getItemId() == R.id.stock_menu_more) {
                showMenu();
            }
            return true;
        });

        binding.stockRefresh.setOnChildScrollUpCallback((p, c) -> binding.stockScroll.canScrollVertically(-1));
        binding.stockRefresh.setOnRefreshListener(vm::refresh);
        vm.getRefreshing().observe(this, r -> binding.stockRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.stockState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_inventory_2, getString(R.string.stock_feed_empty),
                null, getString(R.string.stock_feed_add), com.zhimu.slmp.base.R.drawable.ic_add,
                StateLayout.EMPTY_ACTION_TONAL, v -> vm.onAddFeed());
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, this::renderState);

        StockUi.bindSection(binding.stockSectionFeeds, getString(R.string.stock_feed_section_stock),
                getString(R.string.stock_feed_section_note), null, null);
        StockUi.bindSection(binding.stockSectionFlows, getString(R.string.stock_feed_section_recent), null,
                getString(R.string.stock_feed_all_flows), v -> vm.onAllFlows());
        binding.stockBanner.setAction(getString(R.string.stock_action_stock_in), v -> vm.onBannerAction());
        binding.stockFab.setOnClickListener(v -> vm.onFabClick());
        binding.stockScroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            if (y > oy + 8) {
                binding.stockFab.shrink();
            } else if (y < oy - 8) {
                binding.stockFab.extend();
            }
        });

        vm.getOverview().observe(this, this::renderOverview);
        vm.getFeeds().observe(this, this::renderFeeds);
        vm.getRecentFlows().observe(this, this::renderFlows);
        vm.getBannerText().observe(this, t -> {
            binding.stockBanner.setVisibility(t == null ? View.GONE : View.VISIBLE);
            binding.stockBanner.setText(t);
        });
        vm.getReadonlyText().observe(this, t -> {
            binding.stockReadonlyNotice.setVisibility(t == null ? View.GONE : View.VISIBLE);
            binding.stockReadonlyNotice.setText(t);
        });
        vm.getReadonly().observe(this, r -> renderFab());
        vm.getCurrentFarmId().observe(this, id -> {
        });

        vm.getOpenInbound().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M03,
                StockNav.idArgs(StockExtras.EXTRA_FEED_ID, id)));
        vm.getOpenArchive().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M02,
                StockNav.idArgs(StockExtras.EXTRA_FEED_ID, id)));
        vm.getOpenFlows().observe(this, this::openFlows);
        vm.getOpenRecord().observe(this, f -> StockNav.openFlow(this, childLauncher, f));
    }

    private void renderState(@Nullable UiState state) {
        binding.stockState.render(state);
        renderFab();
    }

    private void renderFab() {
        UiState s = vm.getUiState().getValue();
        boolean content = s != null && s.is(UiState.Status.CONTENT);
        binding.stockFab.setVisibility(content && !Boolean.TRUE.equals(vm.getReadonly().getValue())
                ? View.VISIBLE : View.GONE);
    }

    private void openFlows(@Nullable Long feedId) {
        String title = null;
        if (feedId != null) {
            List<FeedStockItemVO> list = vm.getFeeds().getValue();
            if (list != null) {
                for (FeedStockItemVO f : list) {
                    if (f.feedId == feedId) {
                        title = f.name + StockTexts.DOT + getString(R.string.stock_feed_flows);
                    }
                }
            }
        }
        childLauncher.launch(StockFlowListActivity.intent(this, StockEnums.MATERIAL_FEED, feedId, title));
    }

    private void showMenu() {
        View anchor = binding.stockTopBar.baseToolbar.findViewById(R.id.stock_menu_more);
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor != null ? anchor : binding.stockTopBar.baseToolbar);
        boolean readonly = Boolean.TRUE.equals(vm.getReadonly().getValue());
        if (!readonly) {
            b.add(MENU_ADD, getString(R.string.stock_feed_add), com.zhimu.slmp.base.R.drawable.ic_add);
        }
        if (Boolean.TRUE.equals(vm.getShowOtherOut().getValue())) {
            b.add(MENU_OTHER_OUT, getString(R.string.stock_other_out_menu), R.drawable.stock_ic_output);
        }
        b.add(MENU_FLOWS, getString(R.string.stock_flow_menu), com.zhimu.slmp.base.R.drawable.ic_receipt_long);
        b.setOnItemClickListener(id -> {
            if (id == MENU_ADD) {
                vm.onAddFeed();
            } else if (id == MENU_OTHER_OUT) {
                StockNav.open(this, childLauncher, RouterPath.M10, null);
            } else if (id == MENU_FLOWS) {
                vm.onAllFlows();
            }
        }).show();
    }

    private void renderOverview(@Nullable FeedStockOverviewVO o) {
        if (o == null) {
            return;
        }
        binding.stockStatCount.setValue(String.valueOf(o.feedCount), "种");
        binding.stockStatAmount.setValue(NumberFormatter.formatNumber(o.stockAmount, 0), "元");
        binding.stockStatWarn.setValue(String.valueOf(o.warnCount), "项");
    }

    private void renderFeeds(@Nullable List<FeedStockItemVO> list) {
        binding.stockFeedList.removeAllViews();
        if (list == null) {
            return;
        }
        for (FeedStockItemVO f : list) {
            StockItemFeedCardBinding c = StockItemFeedCardBinding.inflate(getLayoutInflater(),
                    binding.stockFeedList, false);
            c.stockFeedName.setText(f.name);
            c.stockFeedSubtitle.setText(StockTexts.feedSubtitle(f));
            c.stockFeedTag.setVisibility(f.lowStock ? View.VISIBLE : View.GONE);
            StockUi.tone(c.stockFeedLeading, c.stockFeedIcon,
                    f.lowStock ? ListRowView.TONE_WARNING : ListRowView.TONE_SECONDARY);
            c.stockFeedStock.setText(StockQtyFormatter.format(f.stock));
            c.stockFeedRemaining.setText(StockTexts.remaining(f));
            int remainColor = f.lowStock && f.remainingDays != null
                    ? MaterialColors.getColor(c.getRoot(), com.zhimu.slmp.base.R.attr.colorOnWarningContainer)
                    : f.remainingDays == null
                    ? MaterialColors.getColor(c.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant)
                    : MaterialColors.getColor(c.getRoot(), com.google.android.material.R.attr.colorOnSurface);
            c.stockFeedRemaining.setTextColor(remainColor);
            c.stockFeedAmount.setText(StockQtyFormatter.qty(f.stockAmount == null ? null
                    : f.stockAmount.setScale(0, java.math.RoundingMode.HALF_UP), "元"));
            c.stockFeedUsage.setText(StockTexts.feedUsage(f));
            c.stockFeedCard.setOnClickListener(v -> vm.onFeedClick(f));
            c.stockFeedFlows.setOnClickListener(v -> vm.onFeedFlows(f));
            binding.stockFeedList.addView(c.getRoot());
        }
    }

    private void renderFlows(@Nullable List<StockFlowItemVO> list) {
        binding.stockFlowList.removeAllViews();
        boolean empty = list == null || list.isEmpty();
        binding.stockFlowEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (StockFlowItemVO f : list) {
            StockItemRowBinding row = StockUi.inflateRow(binding.stockFlowList);
            bindFlow(row, f, false);
            row.stockRowCard.setOnClickListener(v -> vm.onFlowClick(f));
            binding.stockFlowList.addView(row.getRoot());
        }
    }

    /** 流水行：图标底 surface-container-highest，主文字“饲喂领用 · 育肥全价料”，右侧“−100 千克”，作废行“已作废” */
    public static void bindFlow(@NonNull StockItemRowBinding row, @NonNull StockFlowItemVO f, boolean lotStyle) {
        boolean voided = StockTexts.flowVoided(f);
        StockUi.bindRow(row, StockTexts.flowIcon(f), ListRowView.TONE_NEUTRAL,
                StockTexts.flowTitle(f, !lotStyle), StockTexts.flowSubtitle(f, lotStyle),
                StockQtyFormatter.signed(f.qty, f.direction),
                voided ? (f.statusText != null ? f.statusText : "已作废") : null, StatusTagView.NEUTRAL, 0);
        row.stockRowValue.setTextColor(MaterialColors.getColor(row.getRoot(),
                voided ? com.google.android.material.R.attr.colorOnSurfaceVariant
                        : com.google.android.material.R.attr.colorOnSurface));
        if (voided) {
            row.stockRowValue.setPaintFlags(row.stockRowValue.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            row.stockRowValue.setPaintFlags(row.stockRowValue.getPaintFlags()
                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }
}
