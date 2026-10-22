package com.zhimu.slmp.farm.ui.member;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import java.util.Collections;

import io.reactivex.rxjava3.core.Single;

/**
 * 不发请求的空数据源：F07 筛选为“邀请”时成员段不查，筛选为“场长 / 员工”时邀请段不查。
 */
public class EmptyPagingSource<T> extends RxPagingSource<Integer, T> {

    @NonNull
    @Override
    public Single<LoadResult<Integer, T>> loadSingle(@NonNull LoadParams<Integer> params) {
        return Single.just(new LoadResult.Page<>(Collections.emptyList(), null, null));
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, T> state) {
        return null;
    }
}
