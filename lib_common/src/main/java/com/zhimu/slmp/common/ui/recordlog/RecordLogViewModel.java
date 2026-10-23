package com.zhimu.slmp.common.ui.recordlog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * 查看修改记录（API-U14，{@link RecordLogSheet}）：参数 RouterPath.EXTRA_BIZ_TYPE、EXTRA_BIZ_ID，
 * 列表按服务端返回顺序（时间倒序）显示；加载中为骨架屏，空为“还没有修改记录”，失败为“修改记录加载失败”+“重新加载”。
 */
public class RecordLogViewModel extends BaseViewModel {

    private final BaseDataRepository repository;
    private final MutableLiveData<List<RecordLogVO>> logs = new MutableLiveData<>(Collections.emptyList());
    @Nullable
    private String bizType;
    private long bizId;

    @Inject
    public RecordLogViewModel(@NonNull BaseDataRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        bizType = handle.get(RouterPath.EXTRA_BIZ_TYPE);
        Object id = handle.get(RouterPath.EXTRA_BIZ_ID);
        bizId = id instanceof Number ? ((Number) id).longValue() : 0L;
        retry();
    }

    /** 首次加载与“重新加载” */
    @Override
    public void retry() {
        if (bizType == null || bizType.isEmpty() || bizId <= 0) {
            // 记录还没有保存（没有 biz_id）时不请求，直接显示空状态
            logs.setValue(Collections.emptyList());
            setUiState(UiState.empty(null, null));
            return;
        }
        load(repository.getRecordLogs(bizType, bizId), list -> logs.setValue(
                list == null ? Collections.emptyList() : list));
    }

    @NonNull
    public LiveData<List<RecordLogVO>> getLogs() {
        return logs;
    }
}
