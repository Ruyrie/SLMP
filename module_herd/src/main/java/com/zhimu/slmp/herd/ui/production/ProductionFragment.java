package com.zhimu.slmp.herd.ui.production;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.StatCardView;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdFragmentProductionBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.ProductionBadges;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.HerdNav;

import java.util.ArrayList;
import java.util.List;

/**
 * L01 生产页：所有者、场长底部导航“生产”（TabRegistry.PRODUCTION）。顶部状态栏 insets 由 MainActivity 处理。
 * 三张指标卡：存栏（→ L03 栏舍）、在养批次 + 栏舍占用（→ 批次档案）、生产提醒 N 项（滚动到提醒列表，0 项不可点）。
 */
public class ProductionFragment extends BaseFragment {

    /** 功能入口 */
    private static final class Entry {
        final String code;
        final int label;
        @DrawableRes
        final int icon;

        Entry(String code, int label, @DrawableRes int icon) {
            this.code = code;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final String CODE_DRUG = "D05";
    private static final String CODE_FEED_STOCK = "M01";

    private HerdFragmentProductionBinding binding;
    private ProductionViewModel vm;
    private boolean resumedOnce;
    private final List<View> drugDots = new ArrayList<>();
    private final List<View> feedDots = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = HerdFragmentProductionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(HerdInjector.factory(), ProductionViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);

        binding.herdTopBar.bind(requireActivity(), getViewLifecycleOwner());

        binding.herdState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(getViewLifecycleOwner(), binding.herdState::render);
        vm.getRefreshing().observe(getViewLifecycleOwner(),
                r -> binding.herdRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.herdRefresh.setOnRefreshListener(vm::refresh);
        vm.getCurrentFarmId().observe(getViewLifecycleOwner(), vm::onFarmChanged);

        ReminderAdapter adapter = new ReminderAdapter(vm::onReminderClick);
        binding.herdReminders.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.herdReminders.addItemDecoration(new GroupedListDecoration(requireContext()));
        binding.herdReminders.setAdapter(adapter);
        vm.getReminders().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            binding.herdReminders.setVisibility(list == null || list.isEmpty() ? View.GONE : View.VISIBLE);
        });

        ClickThrottle.setOnClickListener(binding.herdCardStock, v -> vm.onMetricClick(ProductionViewModel.METRIC_STOCK));
        ClickThrottle.setOnClickListener(binding.herdCardBatch, v -> vm.onMetricClick(ProductionViewModel.METRIC_BATCH));
        allowSubtitleWrap(binding.herdCardBatch);
        binding.herdAuditNotice.setAction(getString(R.string.herd_production_audit_action),
                v -> vm.onAuditResultClick());
        binding.herdNoPenNotice.setAction(getString(R.string.herd_production_add_pen), v -> vm.onAddPenClick());
        // 顶部栏随内容滚动变色。
        binding.herdScroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, oldX, oldY) ->
                binding.herdTopBar.setScrolled(v.canScrollVertically(-1)));

        buildEntries();
        vm.getOverview().observe(getViewLifecycleOwner(), this::renderOverview);
        vm.getBatchValueText().observe(getViewLifecycleOwner(), text -> binding.herdCardBatch.setValue(text,
                ProductionViewModel.PLACEHOLDER.equals(text) ? null : getString(R.string.herd_production_batch_unit)));
        vm.getPenOccupancyText().observe(getViewLifecycleOwner(), text -> binding.herdCardBatch.setTrend(
                text == null ? null : getString(R.string.herd_production_pen_occupancy, text),
                StatCardView.TREND_NEUTRAL, StatCardView.DIRECTION_NONE));
        vm.getReminderCount().observe(getViewLifecycleOwner(), this::renderReminderCard);
        vm.getScrollToReminders().observe(getViewLifecycleOwner(), v -> scrollToReminders());
        vm.getBadges().observe(getViewLifecycleOwner(), this::renderBadges);
        vm.getNavEvent().observe(getViewLifecycleOwner(), t -> HerdNav.open(requireActivity(), null, t));
    }

    private void renderOverview(@Nullable ProductionOverviewVO o) {
        if (o == null) {
            return;
        }
        String unit = HerdFormat.unit(o.getUnitText());
        binding.herdCardStock.setValue(HerdFormat.number(o.getStockTotal()), unit);
    }

    /** 生产提醒 N 项：N &gt; 0 时整卡可点（滚动到提醒列表），N = 0 时显示“0 项”且不可点 */
    private void renderReminderCard(@Nullable Integer count) {
        int n = count == null ? 0 : count;
        binding.herdCardReminder.setValue(HerdFormat.number(n), getString(R.string.herd_unit_item));
        ClickThrottle.setOnClickListener(binding.herdCardReminder, n > 0
                ? v -> vm.onMetricClick(ProductionViewModel.METRIC_REMINDER) : null);
    }

    /** 把生产提醒列表滚到可见区域顶部（列表在指标卡下方，偏移按其到滚动内容根的累计 top 计算） */
    private void scrollToReminders() {
        View target = binding.herdReminders;
        if (target.getVisibility() != View.VISIBLE) {
            return;
        }
        int y = 0;
        View v = target;
        while (v != null && v.getParent() != binding.herdScroll) {
            y += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        int margin = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_2);
        binding.herdScroll.smoothScrollTo(0, Math.max(0, y - margin));
    }

    /** “栏舍占用 a/b”在窄屏上允许折成两行，避免数字被省略号截掉（StatCardView 默认单行） */
    private static void allowSubtitleWrap(@NonNull StatCardView card) {
        TextView subtitle = card.findViewById(com.zhimu.slmp.base.R.id.base_stat_trend_text);
        if (subtitle != null) {
            subtitle.setSingleLine(false);
            subtitle.setMaxLines(2);
        }
    }

    private void renderBadges(@Nullable ProductionBadges badges) {
        boolean withdrawal = badges != null && badges.isWithdrawal();
        boolean feed = badges != null && badges.isFeedLowStock();
        for (View d : drugDots) {
            d.setVisibility(withdrawal ? View.VISIBLE : View.GONE);
        }
        for (View d : feedDots) {
            d.setVisibility(feed ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (resumedOnce && vm != null) {
            vm.refresh();
        }
        resumedOnce = true;
    }

    /** 生产 / 防疫 / 物资三组功能入口（02 号规范：一行 5 个，不足 5 个左对齐） */
    private void buildEntries() {
        addGroup(R.string.herd_entry_group_production, new Entry[]{
                new Entry("L03", R.string.herd_entry_stock, com.zhimu.slmp.base.R.drawable.ic_fence),
                new Entry(ProductionViewModel.ENTRY_BATCH_ARCHIVE, R.string.herd_entry_batch,
                        com.zhimu.slmp.base.R.drawable.ic_folder_open),
                new Entry("B01", R.string.herd_entry_breeding,com.zhimu.slmp.base.R.drawable.ic_pets),
                new Entry("G01", R.string.herd_entry_feed, com.zhimu.slmp.base.R.drawable.ic_rice_bowl),
                new Entry("G05", R.string.herd_entry_weigh, com.zhimu.slmp.base.R.drawable.ic_scale)});
        addGroup(R.string.herd_entry_group_health, new Entry[]{
                new Entry("I01", R.string.herd_entry_inspect, com.zhimu.slmp.base.R.drawable.ic_fact_check),
                new Entry("I05", R.string.herd_entry_abnormal, com.zhimu.slmp.base.R.drawable.ic_report),
                new Entry("D01", R.string.herd_entry_immune, com.zhimu.slmp.base.R.drawable.ic_vaccines),
                new Entry(CODE_DRUG, R.string.herd_entry_drug, com.zhimu.slmp.base.R.drawable.ic_medication),
                new Entry("D06", R.string.herd_entry_disinfect, com.zhimu.slmp.base.R.drawable.ic_sanitizer)});
        addGroup(R.string.herd_entry_group_supply, new Entry[]{
                new Entry(CODE_FEED_STOCK, R.string.herd_entry_feed_stock,
                        com.zhimu.slmp.base.R.drawable.ic_inventory_2),
                new Entry("M05", R.string.herd_entry_drug_stock,
                        com.zhimu.slmp.base.R.drawable.ic_medical_services)});
    }

    private void addGroup(int title, @NonNull Entry[] entries) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View card = inflater.inflate(R.layout.herd_view_entry_group, binding.herdEntryGroups, false);
        ((TextView) card.findViewById(R.id.herd_entry_group_title)).setText(title);
        LinearLayout row = card.findViewById(R.id.herd_entry_group_row);
        for (int i = 0; i < 5; i++) {
            View cell = inflater.inflate(R.layout.herd_item_entry, row, false);
            if (i < entries.length) {
                Entry e = entries[i];
                ((ImageView) cell.findViewById(R.id.herd_entry_icon)).setImageResource(e.icon);
                ((TextView) cell.findViewById(R.id.herd_entry_label)).setText(e.label);
                cell.setContentDescription(getString(e.label));
                ClickThrottle.setOnClickListener(cell, v -> vm.onEntryClick(e.code));
                View dot = cell.findViewById(R.id.herd_entry_dot);
                if (CODE_DRUG.equals(e.code)) {
                    drugDots.add(dot);
                } else if (CODE_FEED_STOCK.equals(e.code)) {
                    feedDots.add(dot);
                }
            } else {
                cell.setVisibility(View.INVISIBLE);
            }
            row.addView(cell);
        }
        binding.herdEntryGroups.addView(card);
    }

    @Override
    public void onDestroyView() {
        resumedOnce = false;
        drugDots.clear();
        feedDots.clear();
        binding = null;
        super.onDestroyView();
    }
}
