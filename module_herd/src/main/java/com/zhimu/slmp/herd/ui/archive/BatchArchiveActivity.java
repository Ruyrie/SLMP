package com.zhimu.slmp.herd.ui.archive;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchArchiveBinding;
import com.zhimu.slmp.herd.databinding.HerdViewArchiveHeaderBinding;
import com.zhimu.slmp.herd.databinding.HerdViewMetricBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchPenItem;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.HerdNav;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;
import com.zhimu.slmp.herd.ui.common.SingleViewAdapter;

import java.util.Objects;

import kotlin.Unit;

/**
 * L06 批次档案（全部角色）：养殖户版（更多菜单、引种单价）、员工版（无菜单、无单价、FAB“登记死淘”）、跨场扫码打开（养殖场标签）。
 */
public class BatchArchiveActivity extends BaseActivity {

    private static final String REQ_DELETE = "herd_delete_batch";
    /** 记录筛选：null 为全部（TimelineFilter） */
    private static final String[][] FILTERS = {
            {null, "全部", null},
            {TimelineItemVO.TYPE_FEED, "投喂", "还没有投喂记录"},
            {TimelineItemVO.TYPE_WEIGH, "称重", "还没有称重记录"},
            {TimelineItemVO.TYPE_IMMUNE, "免疫", "还没有免疫记录"},
            {TimelineItemVO.TYPE_DRUG, "用药", "还没有用药记录"},
            {TimelineItemVO.TYPE_INSPECT, "巡检", "还没有巡检记录"}};

    private HerdActivityBatchArchiveBinding binding;
    private HerdViewArchiveHeaderBinding header;
    private BatchArchiveViewModel vm;
    private TimelinePagingAdapter timeline;
    private FooterLoadStateAdapter footer;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode()));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HerdActivityBatchArchiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // 以 RecyclerView 为 parent inflate 之前必须先设置 LayoutManager
        binding.herdList.setLayoutManager(new LinearLayoutManager(this));
        header = HerdViewArchiveHeaderBinding.inflate(getLayoutInflater(), binding.herdList, false);
        vm = obtainViewModel(HerdInjector.factory(), BatchArchiveViewModel.class);
        observeBase(vm);

        binding.herdToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.herdToolbar.inflateMenu(R.menu.herd_menu_archive);
        TopAppBarHelper.tintMenu(binding.herdToolbar);
        binding.herdToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.herd_menu_qr) {
                vm.onQrClick();
                return true;
            }
            if (item.getItemId() == R.id.herd_menu_more) {
                vm.onMoreClick();
                return true;
            }
            return false;
        });

        timeline = new TimelinePagingAdapter(new TimelinePagingAdapter.Listener() {
            @Override
            public void onRecordClick(@NonNull TimelineItemVO item) {
                vm.onRecordClick(item);
            }

            @Override
            public void onRecordLongClick(@NonNull View anchor, @NonNull TimelineItemVO item) {
                recordAnchor = anchor;
                vm.onRecordLongClick(item);
            }
        });
        footer = new FooterLoadStateAdapter(timeline::retry);
        timeline.addLoadStateListener(states -> {
            boolean done = states.getRefresh() instanceof LoadState.NotLoading;
            boolean empty = done && timeline.getItemCount() == 0;
            footer.setHasItems(timeline.getItemCount() > 0);
            footer.setLoadState(states.getAppend());
            header.herdTimelineEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            header.herdTimelineEmpty.setText(emptyText(vm.getTimelineType().getValue()));
            return Unit.INSTANCE;
        });
        ConcatAdapter concat = new ConcatAdapter(new SingleViewAdapter(header.getRoot()), timeline, footer);
        binding.herdList.setAdapter(concat);
        binding.herdList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                binding.herdAppBar.setLifted(rv.canScrollVertically(-1));
                if (dy > 4) {
                    binding.herdFab.shrink();
                } else if (dy < -4) {
                    binding.herdFab.extend();
                }
            }
        });
        buildFilters();

        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, this::onUiState);
        vm.getRefreshing().observe(this, r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(vm::refresh);
        vm.getDetail().observe(this, this::render);
        vm.getShowMore().observe(this, show -> {
            MenuItem more = binding.herdToolbar.getMenu().findItem(R.id.herd_menu_more);
            if (more != null) {
                more.setVisible(Boolean.TRUE.equals(show));
            }
        });
        vm.getShowFab().observe(this, show -> binding.herdFab.setVisibility(Boolean.TRUE.equals(show)
                ? View.VISIBLE : View.GONE));
        vm.getCrossFarmName().observe(this, name -> {
            header.herdFarmTag.setVisibility(name == null ? View.GONE : View.VISIBLE);
            header.herdFarmTag.setText(name);
        });
        vm.getWithdrawalTitle().observe(this, title -> renderWithdrawal());
        vm.getWithdrawalText().observe(this, text -> renderWithdrawal());
        vm.getPendingCount().observe(this, count -> {
            int n = count == null ? 0 : count;
            header.herdPendingNotice.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            header.herdPendingNotice.setText(getString(R.string.herd_archive_pending, n));
        });
        header.herdPendingNotice.setAction(getString(R.string.herd_archive_pending_action), v -> vm.onPendingClick());
        vm.getTimelinePaging().observe(this, data -> timeline.submitData(getLifecycle(), data));
        vm.getExpandedIds().observe(this, ids -> timeline.setExpanded(ids));
        vm.getDeleting().observe(this, d -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(d)));

        ClickThrottle.setOnClickListener(header.herdQrButton, v -> vm.onQrClick());
        ClickThrottle.setOnClickListener(binding.herdFab, v -> vm.onFabClick());
        vm.getMenuEvent().observe(this, items -> {
            View anchor = binding.herdToolbar.findViewById(R.id.herd_menu_more);
            if (anchor != null) {
                MenuItemSpec.show(anchor, items, vm::onMenuSelected);
            }
        });
        vm.getRecordMenuEvent().observe(this, menu -> {
            View anchor = recordAnchor != null ? recordAnchor : binding.herdList;
            MenuItemSpec.show(anchor, menu.items, id -> vm.onRecordMenuSelected(menu.item, id));
        });
        vm.getQrEvent().observe(this, d -> BatchQrBottomSheet.newInstance(d).showSafely(getSupportFragmentManager()));
        vm.getDeleteConfirmEvent().observe(this, no -> ConfirmDialogFragment.builder(REQ_DELETE)
                .title(getString(R.string.herd_archive_delete_title, no))
                .message(getString(R.string.herd_archive_delete_message))
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .positive(getString(R.string.herd_archive_delete_action))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.confirmDelete();
            }
        });
        vm.getNavEvent().observe(this, t -> HerdNav.open(this, childLauncher, t));
        vm.getFinishEvent().observe(this, msg -> HerdNav.finishWithMessage(this, msg));
    }

    @Nullable
    private View recordAnchor;

    /** 休药横幅：“休药中，剩 10 天。”为粗体结论；不在休药期时整条不显示 */
    private void renderWithdrawal() {
        String title = vm.getWithdrawalTitle().getValue();
        header.herdWithdrawalNotice.setVisibility(title == null ? View.GONE : View.VISIBLE);
        header.herdWithdrawalNotice.setNotice(title, vm.getWithdrawalText().getValue());
    }

    /** 41001 批次已删除 → T08 关联记录已失效 */
    private void onUiState(@Nullable UiState state) {
        if (state != null && state.is(UiState.Status.ERROR)
                && state.getErrorCode() == com.zhimu.slmp.base.error.ErrorCodes.RECORD_DELETED) {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_MESSAGE, state.getMessage());
            if (Router.open(this, RouterPath.T08, extras)) {
                finish();
                return;
            }
        }
        binding.herdState.render(state);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (BatchArchiveViewModel.ACTION_SWITCH_BACK.equals(actionId)) {
            vm.switchBack();
        }
    }

    private void buildFilters() {
        header.herdFilterChips.removeAllViews();
        for (String[] f : FILTERS) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.herd_item_choice_chip, header.herdFilterChips,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(f[1]);
            chip.setTag(f[0]);
            chip.setChecked(Objects.equals(f[0], vm.getTimelineType().getValue()));
            chip.setOnClickListener(v -> {
                if (!Objects.equals(f[0], vm.getTimelineType().getValue())) {
                    vm.setTimelineType(f[0]);
                }
            });
            header.herdFilterChips.addView(chip);
        }
    }

    @Nullable
    private static String emptyText(@Nullable String type) {
        for (String[] f : FILTERS) {
            if (Objects.equals(f[0], type)) {
                return f[2];
            }
        }
        return null;
    }

    private void render(@Nullable BatchDetailVO d) {
        if (d == null) {
            return;
        }
        String unit = HerdFormat.unit(d.getUnitText());
        binding.herdToolbar.setTitle("批次 " + d.getBatchNo());
        header.herdBreedTitle.setText(d.getBreedTitle());
        header.herdBreedingTag.setVisibility(d.isBreeding() ? View.VISIBLE : View.GONE);
        if (d.isActive()) {
            header.herdStatusTag.setStatus("在养", StatusTagView.PRIMARY);
        } else {
            header.herdStatusTag.setStatus("已结束", StatusTagView.NEUTRAL);
        }
        header.herdSourceTag.setText(d.isPurchased() ? "外购" : "自繁");
        StringBuilder pens = new StringBuilder();
        boolean multi = d.getPens().size() > 1;
        for (BatchPenItem p : d.getPens()) {
            if (pens.length() > 0) {
                pens.append(HerdFormat.DOT);
            }
            pens.append(p.getPenName());
            if (multi) {
                pens.append(" ").append(HerdFormat.qty(p.getStockCount(), unit));
            }
        }
        header.herdPensText.setText(pens);
        bindMetric(header.herdMetricDayAge, "日龄", String.valueOf(d.getDayAge()), "天");
        bindMetric(header.herdMetricStage, "生长阶段", d.getStageName() == null ? "" : d.getStageName(), "");
        bindMetric(header.herdMetricStock, "存栏", HerdFormat.number(d.getCurrentCount()), unit);
        bindMetric(header.herdMetricMortality, "死亡率", HerdFormat.percentNumber(d.getMortalityRate()),
                d.getMortalityRate() == null ? "" : "%");

        GridLayout grid = header.herdEntryGrid;
        grid.removeAllViews();
        addEntryInfo(grid, "入栏数量", HerdFormat.qty(d.getEntryCount(), unit));
        addEntryInfo(grid, "累计死淘", HerdFormat.qty(d.getDeathCount(), unit));
        addEntryInfo(grid, "入栏日期", HerdFormat.date(d.getEntryDate()));
        addEntryInfo(grid, "入栏日龄", d.getEntryAgeDays() + " 天");
        if (d.getIntroUnitPrice() != null) {
            // 管理视图：员工请求时不返回，整行不渲染
            addEntryInfo(grid, "引种单价", HerdFormat.unitPrice(d.getIntroUnitPrice(), unit));
        }
    }

    private static void bindMetric(@NonNull HerdViewMetricBinding m, String label, String value, String unit) {
        m.herdMetricLabel.setText(label);
        m.herdMetricValue.setText(value);
        m.herdMetricUnit.setText(unit);
    }

    private void addEntryInfo(@NonNull GridLayout grid, @NonNull String key, @NonNull String value) {
        TextView tv = new TextView(this);
        tv.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
        tv.setTextColor(MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOnSurface));
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(key);
        sb.setSpan(new android.text.style.ForegroundColorSpan(MaterialColors.getColor(tv,
                        com.google.android.material.R.attr.colorOnSurfaceVariant)), 0, key.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ").append(value);
        tv.setText(sb);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_8) - 4;
        grid.addView(tv, lp);
    }
}
