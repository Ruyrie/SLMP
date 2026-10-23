package com.zhimu.slmp.stock.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.stock.ui.drugarchive.DrugArchiveViewModel;
import com.zhimu.slmp.stock.ui.druginbound.DrugInboundViewModel;
import com.zhimu.slmp.stock.ui.druglot.DrugLotDetailViewModel;
import com.zhimu.slmp.stock.ui.drugoutbound.DrugOutboundViewModel;
import com.zhimu.slmp.stock.ui.otherout.FeedOtherOutViewModel;
import com.zhimu.slmp.stock.ui.drugscrap.DrugScrapViewModel;
import com.zhimu.slmp.stock.ui.drugstock.DrugStockViewModel;
import com.zhimu.slmp.stock.ui.feedarchive.FeedArchiveViewModel;
import com.zhimu.slmp.stock.ui.feedinbound.FeedInboundViewModel;
import com.zhimu.slmp.stock.ui.feedstock.FeedStockViewModel;
import com.zhimu.slmp.stock.ui.flow.StockFlowListViewModel;
import com.zhimu.slmp.stock.ui.record.StockRecordViewModel;
import com.zhimu.slmp.stock.ui.stocktake.StocktakeHistoryViewModel;
import com.zhimu.slmp.stock.ui.stocktake.StocktakeViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块09 饲料与兽药疫苗库存组件的 ViewModel 多绑定。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class StockViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(StockRecordViewModel.class)
    abstract ViewModel bindRecord(StockRecordViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedStockViewModel.class)
    abstract ViewModel bindFeedStock(FeedStockViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(StockFlowListViewModel.class)
    abstract ViewModel bindFlowList(StockFlowListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedArchiveViewModel.class)
    abstract ViewModel bindFeedArchive(FeedArchiveViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedInboundViewModel.class)
    abstract ViewModel bindFeedInbound(FeedInboundViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(StocktakeViewModel.class)
    abstract ViewModel bindStocktake(StocktakeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(StocktakeHistoryViewModel.class)
    abstract ViewModel bindStocktakeHistory(StocktakeHistoryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugStockViewModel.class)
    abstract ViewModel bindDrugStock(DrugStockViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugLotDetailViewModel.class)
    abstract ViewModel bindDrugLot(DrugLotDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugScrapViewModel.class)
    abstract ViewModel bindDrugScrap(DrugScrapViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugInboundViewModel.class)
    abstract ViewModel bindDrugInbound(DrugInboundViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugArchiveViewModel.class)
    abstract ViewModel bindDrugArchive(DrugArchiveViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedOtherOutViewModel.class)
    abstract ViewModel bindFeedOtherOut(FeedOtherOutViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DrugOutboundViewModel.class)
    abstract ViewModel bindDrugOutbound(DrugOutboundViewModel vm);
}
