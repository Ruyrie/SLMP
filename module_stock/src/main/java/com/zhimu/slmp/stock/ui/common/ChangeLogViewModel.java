package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * 修改记录（M02、M09 history，单据只读菜单“查看修改记录”）：API-U14，biz_type 为 FEED、DRUG、INBOUND、OUTBOUND、
 * STOCKTAKE、LOSS、STOCK_SETTING，按时间倒序。
 */
public class ChangeLogViewModel extends BaseViewModel {

    private final BaseDataRepository baseData;
    private final MutableLiveData<List<RecordLogVO>> logs = new MutableLiveData<>(Collections.emptyList());
    private String bizType;
    private long bizId;

    @Inject
    public ChangeLogViewModel(@NonNull BaseDataRepository baseData) {
        this.baseData = baseData;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        bizType = handle.get(RouterPath.EXTRA_BIZ_TYPE);
        Long id = StockExtras.readId(handle.get(RouterPath.EXTRA_BIZ_ID));
        bizId = id == null ? 0L : id;
        retry();
    }

    @Override
    public void retry() {
        if (bizType == null) {
            return;
        }
        load(baseData.getRecordLogs(bizType, bizId), logs::setValue);
    }

    public LiveData<List<RecordLogVO>> getLogs() {
        return logs;
    }
}
