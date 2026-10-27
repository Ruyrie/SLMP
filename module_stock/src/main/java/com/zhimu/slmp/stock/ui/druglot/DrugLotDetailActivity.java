package com.zhimu.slmp.stock.ui.druglot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityDrugLotBinding;
import com.zhimu.slmp.stock.databinding.StockViewLotHeaderBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.stock.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.common.SingleViewAdapter;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.ui.flow.FlowPagingAdapter;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

/**
 * M06 批号详情。M07、M11、M08、M09 以 RESULT_OK 返回时重取详情与流水，并向 M05 回传 RESULT_OK。
 */
public class DrugLotDetailActivity extends BaseActivity {

    private static final int MENU_EDIT = 1;
    private static final int MENU_LOG = 2;
    private static final int MENU_REQUEST = 3;
    private static final int MENU_ARCHIVE = 4;

    private StockActivityDrugLotBinding binding;
    private StockViewLotHeaderBinding header;
    private DrugLotDetailViewModel vm;
    private FlowPagingAdapter flows;
    private ActivityResultLauncher<Intent> childLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityDrugLotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), DrugLotDetailViewModel.class);
        observeBase(vm);
        childLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK);
                vm.reload();
            }
        });

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_lot_title));
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_more);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            showMenu();
            return true;
        });

        binding.stockList.setLayoutManager(new LinearLayoutManager(this));
        header = StockViewLotHeaderBinding.inflate(getLayoutInflater(), binding.stockList, false);
        StockUi.bindSection(header.stockLotInfoTitle, getString(R.string.stock_lot_info), null, null, null);
        StockUi.bindSection(header.stockLotFlowTitle, getString(R.string.stock_lot_flows),
                getString(R.string.stock_lot_flows_note), null, null);
        flows = new FlowPagingAdapter(true, vm::onFlowClick);
        binding.stockList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()),
                flows.withLoadStateFooter(new FooterLoadStateAdapter(flows::retry))));
        binding.stockList.addItemDecoration(new RowPagingAdapter.Decoration(this, flows, 1));
        vm.getPagingData().observe(this, d -> flows.submitData(getLifecycle(), d));

        binding.stockRefresh.setOnChildScrollUpCallback((p, c) -> binding.stockList.canScrollVertically(-1));
        binding.stockRefresh.setOnRefreshListener(vm::reload);
        vm.getRefreshing().observe(this, r -> binding.stockRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> {
            binding.stockState.render(s);
            renderBar();
        });
        vm.getDetail().observe(this, this::renderDetail);
        vm.getBanner().observe(this, b -> {
            header.stockLotBanner.setVisibility(b == null ? View.GONE : View.VISIBLE);
            if (b != null) {
                header.stockLotBanner.setLevel(b.level);
                header.stockLotBanner.setIcon(b.icon);
                header.stockLotBanner.setNotice(b.title, b.text);
            }
        });
        vm.getInfoRows().observe(this, rows -> renderInfo());
        vm.getShowScrap().observe(this, v -> renderBar());
        vm.getShowOut().observe(this, v -> renderBar());

        binding.stockActionBar.setOnPrimaryClickListener(v -> {
            if (Boolean.TRUE.equals(vm.getShowOut().getValue())) {
                vm.onOutbound();
            } else {
                vm.onScrap();
            }
        });
        binding.stockActionBar.setOnSecondaryClickListener(v -> vm.onScrap());

        vm.getOpenScrap().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M07,
                StockNav.idArgs(RouterPath.EXTRA_LOT_ID, id)));
        vm.getOpenOutbound().observe(this, id -> {
            DrugLotDetailVO d = vm.getDetail().getValue();
            StockNav.open(this, childLauncher, RouterPath.M11, StockNav.outboundArgs(d == null ? null : d.drugId, id));
        });
        vm.getOpenArchive().observe(this, id -> StockNav.open(this, childLauncher, RouterPath.M09,
                StockNav.idArgs(RouterPath.EXTRA_DRUG_ID, id)));
        vm.getOpenMessage().observe(this, id -> Router.open(this, RouterPath.T06,
                StockNav.idArgs(RouterPath.EXTRA_MESSAGE_ID, id)));
        vm.getOpenInboundEdit().observe(this, id -> {
            Bundle b = StockNav.idArgs(RouterPath.EXTRA_RECORD_ID, id);
            b.putString(RouterPath.EXTRA_MODE, "EDIT");
            StockNav.open(this, childLauncher, RouterPath.M08, b);
        });
        vm.getOpenChangeRequest().observe(this, id -> Router.open(this, RouterPath.U01,
                StockNav.changeArgs(StockEnums.BIZ_INBOUND, id)));
        vm.getOpenChangeLog().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                StockEnums.BIZ_INBOUND, id, null));
        vm.getOpenRecord().observe(this, f -> StockNav.openFlow(this, childLauncher, f));
        vm.getRecordGone().observe(this, msg -> {
            Bundle b = new Bundle();
            b.putString(RouterPath.EXTRA_MESSAGE, msg);
            if (Router.open(this, RouterPath.T08, b)) {
                finish();
            }
        });
    }

    private void renderDetail(@Nullable DrugLotDetailVO d) {
        if (d == null) {
            return;
        }
        header.stockLotName.setText(d.drugName);
        header.stockLotSubtitle.setText(DrugLotDetailViewModel.subtitle(d));
        header.stockLotIcon.setImageResource(StockTexts.categoryIcon(d.category));
        StockUi.tone(header.stockLotLeading, header.stockLotIcon, StockTexts.lotTone(d.lotStatus));
        String tag = StockTexts.lotTag(d);
        header.stockLotTag.setVisibility(tag == null ? View.GONE : View.VISIBLE);
        header.stockLotTag.setStatus(tag, StockTexts.lotTagType(d.lotStatus));
        header.stockLotTag.setTagIcon(StockTexts.lotTagIcon(d.lotStatus));
        header.stockLotStock.setText(StockQtyFormatter.shortText(d.stock));
        header.stockLotAmount.setText(DrugLotDetailViewModel.amountText(d) + " 元");
        renderInfo();
        renderBar();
    }

    private void renderInfo() {
        StockUi.renderKv(header.stockLotInfo, vm.getInfoRows().getValue());
        DrugLotDetailVO d = vm.getDetail().getValue();
        if (d != null && StockEnums.LOT_RECALLED.equals(d.lotStatus) && d.recallMessageId != null) {
            KeyValueRowView row = new KeyValueRowView(this);
            row.setKey(getString(R.string.stock_lot_recall_notice));
            row.setValue(getString(R.string.stock_lot_view_message));
            row.setValueAlign(KeyValueRowView.ALIGN_END);
            row.setShowChevron(true);
            row.getValueView().setTextColor(MaterialColors.getColor(row,
                    android.R.attr.colorPrimary));
            row.setOnClickListener(v -> vm.onMessage());
            header.stockLotInfo.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void renderBar() {
        UiState s = vm.getUiState().getValue();
        boolean content = s != null && s.is(UiState.Status.CONTENT);
        boolean scrap = Boolean.TRUE.equals(vm.getShowScrap().getValue());
        boolean out = Boolean.TRUE.equals(vm.getShowOut().getValue());
        binding.stockActionBar.setVisibility(content && (scrap || out) ? View.VISIBLE : View.GONE);
        if (out) {
            binding.stockActionBar.setSecondary(scrap ? getString(R.string.stock_scrap_action) : null,
                    scrap ? com.zhimu.slmp.base.R.drawable.ic_delete : 0);
            binding.stockActionBar.setPrimary(getString(R.string.stock_lot_out), R.drawable.stock_ic_output);
        } else {
            binding.stockActionBar.setSecondary(null, 0);
            binding.stockActionBar.setPrimary(getString(R.string.stock_scrap_action),
                    com.zhimu.slmp.base.R.drawable.ic_delete);
        }
        DrugLotDetailVO d = vm.getDetail().getValue();
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_more).setVisible(content && d != null);
    }

    private void showMenu() {
        View anchor = binding.stockTopBar.baseToolbar.findViewById(R.id.stock_menu_more);
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor != null ? anchor : binding.stockTopBar.baseToolbar);
        DrugLotDetailVO d = vm.getDetail().getValue();
        if (d != null && d.firstInbound != null) {
            if (vm.canEditInbound()) {
                b.add(MENU_EDIT, getString(R.string.stock_lot_edit_inbound), com.zhimu.slmp.base.R.drawable.ic_edit);
                b.add(MENU_LOG, getString(R.string.stock_menu_change_log), com.zhimu.slmp.base.R.drawable.ic_history);
            } else {
                b.addReview(MENU_REQUEST, getString(R.string.stock_menu_request_change),
                        com.zhimu.slmp.base.R.drawable.ic_edit_note);
            }
        }
        b.add(MENU_ARCHIVE, getString(R.string.stock_drug_archive_title), R.drawable.stock_ic_medical_information);
        b.setOnItemClickListener(id -> {
            if (id == MENU_EDIT) {
                vm.onEditInbound();
            } else if (id == MENU_LOG) {
                vm.onChangeLog();
            } else if (id == MENU_REQUEST) {
                vm.onRequestChange();
            } else {
                vm.onArchive();
            }
        }).show();
    }
}
