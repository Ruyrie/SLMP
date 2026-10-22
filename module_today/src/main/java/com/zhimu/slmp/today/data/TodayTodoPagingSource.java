package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.model.vo.TodoPageVO;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * 首页“今天和已逾期”分页（API-T02，filter 固定 ALL，每页 20 条）；第 1 页到达时回调 total。
 */
public class TodayTodoPagingSource extends PagedSource<TodoItemVO> {

    public TodayTodoPagingSource(@NonNull TodayApi api, @NonNull String filter,
                                 @Nullable Consumer<TodoPageVO> onFirstPage) {
        super((page, size) -> api.getTodayTodos(filter, page, size)
                        .compose(RxResult.unwrap())
                        .map(vo -> (PageVO<TodoItemVO>) vo),
                onFirstPage == null ? null : vo -> onFirstPage.accept((TodoPageVO) vo));
    }
}
