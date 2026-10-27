package com.zhimu.slmp.herd.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.herd.ui.archive.BatchArchiveViewModel;
import com.zhimu.slmp.herd.ui.death.BatchDeathViewModel;
import com.zhimu.slmp.herd.ui.entry.BatchEntrySuccessViewModel;
import com.zhimu.slmp.herd.ui.entry.BatchEntryViewModel;
import com.zhimu.slmp.herd.ui.mypens.MyPensViewModel;
import com.zhimu.slmp.herd.ui.pendetail.PenDetailViewModel;
import com.zhimu.slmp.herd.ui.penedit.PenEditViewModel;
import com.zhimu.slmp.herd.ui.production.ProductionViewModel;
import com.zhimu.slmp.herd.ui.scan.BatchScanViewModel;
import com.zhimu.slmp.herd.ui.trace.BatchTraceViewModel;
import com.zhimu.slmp.herd.ui.stock.StockOverviewViewModel;
import com.zhimu.slmp.herd.ui.transfer.BatchTransferViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块04 栏舍与存栏组件的 ViewModel 多绑定（L01–L11）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class HerdViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(ProductionViewModel.class)
    abstract ViewModel bindProduction(ProductionViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MyPensViewModel.class)
    abstract ViewModel bindMyPens(MyPensViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(StockOverviewViewModel.class)
    abstract ViewModel bindStockOverview(StockOverviewViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(PenDetailViewModel.class)
    abstract ViewModel bindPenDetail(PenDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(PenEditViewModel.class)
    abstract ViewModel bindPenEdit(PenEditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchArchiveViewModel.class)
    abstract ViewModel bindBatchArchive(BatchArchiveViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchScanViewModel.class)
    abstract ViewModel bindBatchScan(BatchScanViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchTraceViewModel.class)
    abstract ViewModel bindBatchTrace(BatchTraceViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchEntryViewModel.class)
    abstract ViewModel bindBatchEntry(BatchEntryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchEntrySuccessViewModel.class)
    abstract ViewModel bindBatchEntrySuccess(BatchEntrySuccessViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchTransferViewModel.class)
    abstract ViewModel bindBatchTransfer(BatchTransferViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BatchDeathViewModel.class)
    abstract ViewModel bindBatchDeath(BatchDeathViewModel vm);
}
