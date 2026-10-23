package com.zhimu.slmp.ai.ui.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayArgs;
import com.zhimu.slmp.ai.model.vo.WatchHistoryVO;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;

import javax.inject.Inject;

/**
 * K04 观看记录（技术文档 2.4.4 末段）：API-K21 分页，按最近观看时间排序；每行“看到 m:ss”或“已学完”；点击进入 K05（HISTORY）。
 */
public class WatchHistoryViewModel extends BaseListViewModel<String, WatchHistoryVO> {

    private final CourseRepository repository;
    private final SingleLiveEvent<PlayArgs> openPlay = new SingleLiveEvent<>();

    @Inject
    public WatchHistoryViewModel(@NonNull CourseRepository repository) {
        super("");
        this.repository = repository;
    }

    @NonNull
    @Override
    protected PagingSource<Integer, WatchHistoryVO> createPagingSource(@Nullable String filter) {
        return repository.historySource();
    }

    public SingleLiveEvent<PlayArgs> getOpenPlay() {
        return openPlay;
    }

    public void onItemClick(@NonNull WatchHistoryVO item) {
        openPlay.setValue(new PlayArgs(item.getId(), AiCodes.ENTRY_HISTORY));
    }
}
