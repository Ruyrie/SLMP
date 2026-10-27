package com.zhimu.slmp.feed.ui.growth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.dialog.TipPopup;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityListBinding;
import com.zhimu.slmp.feed.databinding.FeedViewGrowthHeaderBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.request.GrowthCurveQuery;
import com.zhimu.slmp.feed.model.vo.WeighBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordItemVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.feed.ui.common.SingleViewAdapter;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.widget.FeedStatCard;
import com.zhimu.slmp.feed.widget.GrowthChartModel;
import com.zhimu.slmp.feed.widget.GrowthCurveView;

import java.util.List;

/**
 * G06 生长曲线（L01“称重”先选批次、G05 show_chart、L06 时间线称重记录进入）。顶部栏 scale 进入本批次 G05（已结束批次不显示），
 * 返回后刷新；点曲线数据点弹出提示气泡；点称重记录按 action 进入 G05 修改或弹出“提交修改申请”菜单。
 */
public class GrowthCurveActivity extends BaseActivity {

    private static final String REQ_BATCH = "feed_growth_batch";
    private static final int MENU_REQUEST_CHANGE = 1;

    private FeedActivityListBinding binding;
    private FeedViewGrowthHeaderBinding header;
    private GrowthCurveViewModel vm;
    private WeighRecordPagingAdapter records;
    private float tipX;
    private float tipY;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode() == Activity.RESULT_OK));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FeedActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FeedInjector.factory(), GrowthCurveViewModel.class);
        observeBase(vm);

        binding.feedToolbar.setTitle(R.string.feed_growth_title);
        binding.feedToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedToolbar.inflateMenu(R.menu.feed_menu_growth);
        TopAppBarHelper.tintMenu(binding.feedToolbar);
        binding.feedToolbar.getMenu().findItem(R.id.feed_menu_weigh).setVisible(false);
        binding.feedToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_weigh) {
                vm.onWeighClick();
                return true;
            }
            return false;
        });
        StateLayout state = binding.feedState;
        state.setErrorTitle(getString(R.string.feed_growth_error_title));
        state.setNoPermissionTitle(getString(R.string.feed_no_pen_permission_title));
        state.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, this::renderState);
        vm.getRefreshing().observe(this, r -> binding.feedRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.feedRefresh.setOnRefreshListener(() -> vm.refresh());

        setupList();

        vm.getHeaderText().observe(this, t -> header.feedGrowthBatchNo.setText(t));
        vm.getHeaderSummary().observe(this, t -> header.feedGrowthSummary.setText(t));
        vm.getStatusTag().observe(this, this::renderStatusTag);
        vm.getStdNotice().observe(this, n -> header.feedGrowthStdNotice.setVisibility(Boolean.TRUE.equals(n)
                ? View.VISIBLE : View.GONE));
        vm.getCanRegister().observe(this, c -> renderState(vm.getUiState().getValue()));
        vm.getMetricCards().observe(this, this::renderMetrics);
        vm.getChartModel().observe(this, m -> {
            TipPopup.dismiss();
            header.feedGrowthChart.setData(m);
            String notice = m == null ? "" : m.comparisonNotice();
            header.feedGrowthCompareNotice.setText(notice);
            header.feedGrowthCompareNotice.setVisibility(notice.isEmpty() ? View.GONE : View.VISIBLE);
            renderCompareChips();
        });
        vm.getLegendItems().observe(this, this::renderLegend);
        vm.getCompareIds().observe(this, ids -> renderCompareChips());
        vm.getCurve().observe(this, c -> renderCompareChips());
        vm.getPagingData().observe(this, data -> records.submitData(getLifecycle(), data));
        vm.getShowPointTip().observe(this, this::showTip);
        vm.getShowCompareSheet().observe(this, v -> GrowthCompareSheet.newInstance(
                getString(R.string.feed_growth_compare_title)).showSafely(getSupportFragmentManager()));
        vm.getOpenWeigh().observe(this, args -> FeedNav.open(this, childLauncher, RouterPath.G05, args));
        vm.getOpenChangeRequest().observe(this, args -> Router.open(this, RouterPath.U01, args));
        vm.getShowBatchSheet().observe(this, v -> showBatchSheet());
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            if (value != null) {
                vm.init(Long.parseLong(value));
            }
        });

        header.feedGrowthChart.setOnPointClickListener(new GrowthCurveView.OnPointClickListener() {
            @Override
            public void onPointClick(int series, int index, float x, float y) {
                tipX = x;
                tipY = y;
                vm.onPointClick(header.feedGrowthChart.tipOf(series, index));
            }

            @Override
            public void onBlankClick() {
                TipPopup.dismiss();
            }
        });
        header.feedGrowthAddCompare.setOnClickListener(v -> vm.openCompareSheet());
    }

    private void setupList() {
        RecyclerView list = binding.feedList;
        list.setLayoutManager(new LinearLayoutManager(this));
        header = FeedViewGrowthHeaderBinding.inflate(getLayoutInflater(), list, false);
        records = new WeighRecordPagingAdapter(this::onRecordClick);
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(records::retry);
        list.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()), records, footer));
        list.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
                return adapterPosition >= 1 && adapterPosition <= records.getItemCount();
            }
        });
        records.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    list.post(() -> records.notifyItemChanged(positionStart - 1));
                }
            }
        });
        TopAppBarHelper.bindLiftOnScroll(binding.feedAppBar, list);
    }

    private void onRecordClick(@NonNull WeighRecordItemVO item, @NonNull View anchor) {
        if (WeighRecordItemVO.ACTION_REQUEST_CHANGE.equals(item.getAction())) {
            MoreMenuHelper.with(anchor)
                    .addReview(MENU_REQUEST_CHANGE, getString(R.string.feed_growth_record_menu),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note)
                    .setOnItemClickListener(id -> {
                        if (id == MENU_REQUEST_CHANGE) {
                            vm.requestChange(item);
                        }
                    })
                    .show();
        } else {
            vm.onRecordClick(item);
        }
    }

    /** 空状态“登记称重”（已结束批次不显示按钮）；顶部栏 scale 只在在养批次显示 */
    private void renderState(@Nullable UiState state) {
        boolean active = Boolean.TRUE.equals(vm.getCanRegister().getValue());
        binding.feedState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_scale, getString(R.string.feed_growth_empty_title),
                getString(R.string.feed_growth_empty_message),
                active ? getString(R.string.feed_growth_empty_action) : null,
                active ? com.zhimu.slmp.base.R.drawable.ic_scale : 0, StateLayout.EMPTY_ACTION_TONAL,
                active ? v -> vm.onWeighClick() : null);
        binding.feedState.render(state);
        boolean shown = state == null || state.is(UiState.Status.CONTENT) || state.is(UiState.Status.EMPTY);
        binding.feedToolbar.getMenu().findItem(R.id.feed_menu_weigh).setVisible(shown && active);
    }

    private void renderStatusTag(@Nullable String text) {
        StatusTagView tag = header.feedGrowthStatus;
        if (text == null) {
            tag.setVisibility(View.GONE);
            return;
        }
        if ("已结束".equals(text)) {
            tag.setStatus(text, StatusTagView.NEUTRAL);
            tag.setTagIcon(0);
        } else {
            tag.setStatus(text, StatusTagView.WARNING);
            tag.setTagIcon(text.contains("偏快") ? com.zhimu.slmp.base.R.drawable.ic_trending_up
                    : com.zhimu.slmp.base.R.drawable.ic_trending_down);
        }
        tag.setVisibility(View.VISIBLE);
    }

    private void renderMetrics(@Nullable List<FeedStatCard> cards) {
        FeedStatCard[] arr = new FeedStatCard[3];
        if (cards != null) {
            for (int i = 0; i < Math.min(3, cards.size()); i++) {
                arr[i] = cards.get(i);
            }
        }
        header.feedGrowthStatAvg.bind(arr[0]);
        header.feedGrowthStatDev.bind(arr[1]);
        header.feedGrowthStatGain.bind(arr[2]);
    }

    private void renderLegend(@Nullable List<GrowthLegendItem> items) {
        header.feedGrowthLegend.removeAllViews();
        if (items == null) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (GrowthLegendItem item : items) {
            View v = inflater.inflate(R.layout.feed_item_legend, header.feedGrowthLegend, false);
            ImageView mark = v.findViewById(R.id.feed_legend_mark);
            int res;
            switch (item.kind) {
                case GrowthLegendItem.STD:
                    res = R.drawable.feed_legend_dash;
                    break;
                case GrowthLegendItem.COMPARE:
                    res = R.drawable.feed_legend_dot;
                    break;
                case GrowthLegendItem.BAND:
                    res = R.drawable.feed_bg_legend_band;
                    break;
                default:
                    res = R.drawable.feed_legend_solid;
                    break;
            }
            mark.setImageResource(res);
            ((TextView) v.findViewById(R.id.feed_legend_text)).setText(item.text);
            header.feedGrowthLegend.addView(v);
        }
    }

    /** 对比 Chip：已添加的批次（close 移除）+“添加批次”（满 3 个时隐藏） */
    private void renderCompareChips() {
        header.feedGrowthCompareChips.removeAllViews();
        GrowthChartModel chart = vm.getChartModel().getValue();
        List<Long> ids = vm.getCompareIds().getValue();
        int count = 0;
        if (chart != null) {
            for (GrowthChartModel.Series s : chart.compare) {
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.feed_item_compare_chip,
                        header.feedGrowthCompareChips, false);
                chip.setText(s.batchNo + (s.points.isEmpty() ? "（暂无数据）" : ""));
                chip.setCloseIconContentDescription("移除对比批次 " + s.batchNo);
                long id = s.batchId;
                chip.setOnCloseIconClickListener(v -> vm.removeCompare(id));
                header.feedGrowthCompareChips.addView(chip);
                count++;
            }
        }
        int size = Math.max(count, ids == null ? 0 : ids.size());
        header.feedGrowthCompareChips.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        header.feedGrowthAddCompare.setVisibility(size >= GrowthCurveQuery.MAX_COMPARE ? View.GONE : View.VISIBLE);
    }

    private void showTip(@NonNull String text) {
        View anchor = header.feedGrowthTipAnchor;
        anchor.setTranslationX(tipX);
        anchor.setTranslationY(tipY - getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_1));
        anchor.post(() -> {
            if (!isFinishing()) {
                TipPopup.show(anchor, text);
            }
        });
    }

    private void showBatchSheet() {
        List<WeighBatchOptionVO> list = vm.getBatchOptions().getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.feed_batch_sheet_title));
        for (WeighBatchOptionVO o : list) {
            OptionSheetDialogFragment.Option option = new OptionSheetDialogFragment.Option(
                    String.valueOf(o.getBatchId()), FeedFormat.orEmpty(o.getBatchNo()),
                    FeedFormat.join(o.getPenNames(), o.getBreedName(), o.isActive() ? "在养" : "已结束"));
            if (!o.isActive()) {
                option.tag(getString(R.string.feed_tag_ended), StatusTagView.NEUTRAL);
            }
            b.option(option);
        }
        b.build().showSafely(getSupportFragmentManager());
    }
}
