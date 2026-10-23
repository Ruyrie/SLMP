package com.zhimu.slmp.stock.ui.record;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityRecordBinding;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;
import com.zhimu.slmp.stock.ui.common.ChangeLogActivity;
import com.zhimu.slmp.stock.ui.common.RecordMenu;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;

/**
 * 单据只读页（报损、报废、盘点）：参数 StockExtras.EXTRA_RECORD_KIND（LOSS / STOCKTAKE）、EXTRA_RECORD_ID。
 * 本组件内页面（没有页面编号），由流水与盘点记录显式打开。
 */
public class StockRecordActivity extends BaseActivity implements RecordMenu.Callback {

    private StockActivityRecordBinding binding;
    private StockRecordViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), StockRecordViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, "");
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_more);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            View anchor = binding.stockTopBar.baseToolbar.findViewById(R.id.stock_menu_more);
            RecordMenu.show(anchor != null ? anchor : binding.stockTopBar.baseToolbar, vm.getActions().getValue(),
                    false, this);
            return true;
        });
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.stockState::render);
        vm.getTitle().observe(this, t -> binding.stockTopBar.baseToolbar.setTitle(t));
        vm.getRows().observe(this, rows -> StockUi.renderKv(binding.stockRecordRows, rows));
        vm.getActions().observe(this, a -> binding.stockTopBar.baseToolbar.getMenu()
                .findItem(R.id.stock_menu_more).setVisible(RecordMenu.hasItems(a, false)));
        vm.getStocktake().observe(this, this::renderItems);
        vm.getLoss().observe(this, l -> binding.stockRecordNotice.setText(l != null && "DRUG_SCRAP".equals(l.lossType)
                ? getString(R.string.stock_scrap_rule) : getString(R.string.stock_record_rule)));
        vm.getOpenChangeRequest().observe(this, id -> Router.open(this, RouterPath.U01,
                StockNav.changeArgs(vm.bizType(), id)));
        vm.getOpenRequest().observe(this, id -> Router.open(this, RouterPath.U02, StockNav.requestArgs(id)));
        vm.getOpenChangeLog().observe(this, id -> startActivity(ChangeLogActivity.intent(this, vm.bizType(), id)));
        binding.stockRecordNotice.setText(getString(R.string.stock_record_rule));
    }

    @Override
    public void onRecordMenu(int id) {
        if (id == RecordMenu.REQUEST) {
            vm.onRequestChange();
        } else if (id == RecordMenu.VIEW_REQUEST) {
            vm.onViewRequest();
        } else if (id == RecordMenu.CHANGE_LOG) {
            vm.onChangeLog();
        }
    }

    private void renderItems(@Nullable StocktakeVO s) {
        binding.stockRecordItems.removeAllViews();
        boolean has = s != null && s.items != null && !s.items.isEmpty();
        binding.stockRecordItemsTitle.getRoot().setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) {
            return;
        }
        StockUi.bindSection(binding.stockRecordItemsTitle, getString(R.string.stock_record_items), null, null, null);
        binding.stockRecordNotice.setText(getString(R.string.stock_stocktake_rule));
        for (StocktakeVO.Item i : s.items) {
            StockItemRowBinding row = StockUi.inflateRow(binding.stockRecordItems);
            String[] t = StockRecordViewModel.itemTexts(i);
            int icon = StockEnums.RESULT_GAIN.equals(i.result) ? com.zhimu.slmp.base.R.drawable.ic_trending_up
                    : StockEnums.RESULT_LOSS.equals(i.result) ? com.zhimu.slmp.base.R.drawable.ic_trending_down
                    : com.zhimu.slmp.base.R.drawable.ic_check;
            StockUi.bindRow(row, icon, ListRowView.TONE_NEUTRAL, t[0], t[1], t[2], null, StatusTagView.NEUTRAL, 0);
            row.stockRowCard.setClickable(false);
            binding.stockRecordItems.addView(row.getRoot());
        }
    }
}
