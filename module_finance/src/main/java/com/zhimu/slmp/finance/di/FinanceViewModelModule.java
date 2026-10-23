package com.zhimu.slmp.finance.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.finance.ui.advice.SaleAdviceViewModel;
import com.zhimu.slmp.finance.ui.category.ExpenseCategoryViewModel;
import com.zhimu.slmp.finance.ui.home.FinanceHomeViewModel;
import com.zhimu.slmp.finance.ui.market.MarketPriceViewModel;
import com.zhimu.slmp.finance.ui.receivable.ReceivableListViewModel;
import com.zhimu.slmp.finance.ui.record.FinanceRecordEditViewModel;
import com.zhimu.slmp.finance.ui.recordlist.FinanceRecordListViewModel;
import com.zhimu.slmp.finance.ui.report.BusinessReportViewModel;
import com.zhimu.slmp.finance.ui.salechange.SaleChangeRequestViewModel;
import com.zhimu.slmp.finance.ui.saledetail.SaleOrderDetailViewModel;
import com.zhimu.slmp.finance.ui.saleedit.SaleOrderEditViewModel;
import com.zhimu.slmp.finance.ui.salelist.SaleOrderListViewModel;
import com.zhimu.slmp.finance.ui.settlement.BatchSettlementViewModel;
import com.zhimu.slmp.finance.ui.summary.FinanceSummaryViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块10 收支、出栏、行情与报表组件的 ViewModel 多绑定（E01–E14）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class FinanceViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(FinanceHomeViewModel.class)
    abstract ViewModel bindHome(FinanceHomeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FinanceSummaryViewModel.class)
    abstract ViewModel bindSummary(FinanceSummaryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FinanceRecordEditViewModel.class)
    abstract ViewModel bindRecordEdit(FinanceRecordEditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FinanceRecordListViewModel.class)
    abstract ViewModel bindRecordList(FinanceRecordListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ExpenseCategoryViewModel.class)
    abstract ViewModel bindCategory(ExpenseCategoryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(SaleOrderEditViewModel.class)
    abstract ViewModel bindSaleEdit(SaleOrderEditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(SaleOrderDetailViewModel.class)
    abstract ViewModel bindSaleDetail(SaleOrderDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(SaleChangeRequestViewModel.class)
    abstract ViewModel bindSaleChange(SaleChangeRequestViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ReceivableListViewModel.class)
    abstract ViewModel bindReceivable(ReceivableListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchSettlementViewModel.class)
    abstract ViewModel bindSettlement(BatchSettlementViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MarketPriceViewModel.class)
    abstract ViewModel bindMarket(MarketPriceViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(SaleAdviceViewModel.class)
    abstract ViewModel bindAdvice(SaleAdviceViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BusinessReportViewModel.class)
    abstract ViewModel bindReport(BusinessReportViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(SaleOrderListViewModel.class)
    abstract ViewModel bindSaleList(SaleOrderListViewModel vm);
}
