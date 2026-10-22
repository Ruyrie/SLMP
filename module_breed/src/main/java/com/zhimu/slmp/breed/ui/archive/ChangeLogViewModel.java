package com.zhimu.slmp.breed.ui.archive;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * 查看修改记录（B06 更多菜单 / 记录菜单，待定事项 4：页面归属未定，暂为本组件内页面）：API-U14，按时间倒序。
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
        Object id = handle.get(RouterPath.EXTRA_BIZ_ID);
        bizId = id instanceof Long ? (Long) id : 0L;
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
