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
        ClickThrottle.setOnClickListener(binding.herdCardTodo, v -> vm.onMetricClick(ProductionViewModel.METRIC_TODO));
        ClickThrottle.setOnClickListener(binding.herdCardWarning,
                v -> vm.onMetricClick(ProductionViewModel.METRIC_WARNING));
        ClickThrottle.setOnClickListener(binding.herdRemindersAll, v -> vm.onViewAllReminders());
        binding.herdAuditNotice.setAction(getString(R.string.herd_production_audit_action),
                v -> vm.onAuditResultClick());
        binding.herdNoPenNotice.setAction(getString(R.string.herd_production_add_pen), v -> vm.onAddPenClick());
        ClickThrottle.setOnClickListener(binding.herdFab, v -> vm.onFabClick());
        // 顶部栏滚动变色 + 扩展型 FAB 上滑收起、下滑展开
        binding.herdScroll.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, oldX, oldY) -> {
            binding.herdTopBar.setScrolled(v.canScrollVertically(-1));
            if (y > oldY + 4) {
                binding.herdFab.shrink();
            } else if (y < oldY - 4) {
                binding.herdFab.extend();
            }
        });

        buildEntries();
        vm.getOverview().observe(getViewLifecycleOwner(), this::renderOverview);
        vm.getBadges().observe(getViewLifecycleOwner(), this::renderBadges);
        vm.getNavEvent().observe(getViewLifecycleOwner(), t -> HerdNav.open(requireActivity(), null, t));
    }

    private void renderOverview(@Nullable ProductionOverviewVO o) {
        if (o == null) {
            return;
        }
        String unit = HerdFormat.unit(o.getUnitText());
        binding.herdCardStock.setValue(HerdFormat.number(o.getStockTotal()), unit);
        binding.herdCardTodo.setValue(HerdFormat.number(o.getTodayTodoCount()), getString(R.string.herd_unit_item));
        binding.herdCardWarning.setValue(HerdFormat.number(o.getReminderTotal()), getString(R.string.herd_unit_item));
        int shown = o.getReminders().size();
        boolean more = o.getReminderTotal() > shown && shown > 0;
        binding.herdRemindersAll.setVisibility(more ? View.VISIBLE : View.GONE);
        binding.herdRemindersAll.setText(getString(R.string.herd_production_view_all_reminders, o.getReminderTotal()));
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
        drugDots.clear();
        feedDots.clear();
        binding = null;
        super.onDestroyView();
    }
}
