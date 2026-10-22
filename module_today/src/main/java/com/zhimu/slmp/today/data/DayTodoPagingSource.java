package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;
import com.zhimu.slmp.today.model.vo.TodoItemVO;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * 日历某天待办分页（API-T04）：月视图当日列表与日视图时间轴；第 1 页到达时回调 total（含已完成和已忽略）。
 */
public class DayTodoPagingSource extends PagedSource<TodoItemVO> {

    public DayTodoPagingSource(@NonNull TodayApi api, @NonNull String date, @Nullable String scope,
                               @Nullable String category, @Nullable String status,
                               @Nullable Consumer<PageVO<TodoItemVO>> onFirstPage) {
        super((page, size) -> api.getDayTodos(date, scope, category, status, page, size)
                .compose(RxResult.unwrap()), onFirstPage);
    }
}
