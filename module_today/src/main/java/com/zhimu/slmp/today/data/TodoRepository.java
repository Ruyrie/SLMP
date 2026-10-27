package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;
import com.zhimu.slmp.today.model.request.TodoCompleteRequest;
import com.zhimu.slmp.today.model.request.TodoIgnoreRequest;
import com.zhimu.slmp.today.model.vo.TodoCalendarResult;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.model.vo.TodoPageVO;
import com.zhimu.slmp.today.model.vo.TodoWeeklyResult;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 待办查询与处理（API-T02–T09）。分页方法返回未缓存的 Flowable，由 ViewModel cachedIn 到自己的范围；
 * invalidateToday / invalidateDay 使最近一次创建的数据源失效（下拉刷新、登记回流后静默刷新）。
 */
@FeatureScope
public class TodoRepository {

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_HOME = "HOME";
    public static final String FILTER_WARNING = "WARNING";
    public static final String FILTER_OVERDUE = "OVERDUE";
    public static final String STATUS_ALL = "ALL";
    public static final int EXPAND_SIZE = 100;

    private final TodayApi api;
    @Nullable
    private volatile WeakReference<PagingSource<?, ?>> todaySource;
    @Nullable
    private volatile WeakReference<PagingSource<?, ?>> daySource;

    @Inject
    public TodoRepository(@NonNull TodayApi api) {
        this.api = api;
    }

    @NonNull
    static PagingConfig config() {
        return new PagingConfig(PagedSource.PAGE_SIZE, 5, false, PagedSource.PAGE_SIZE);
    }

    /** API-T02 今天和已逾期 */
    @NonNull
    public Flowable<PagingData<TodoItemVO>> todayPager(@NonNull String filter, @Nullable Consumer<TodoPageVO> onFirstPage) {
        return PagingRx.getFlowable(new Pager<>(config(), () -> {
            TodayTodoPagingSource s = new TodayTodoPagingSource(api, filter, onFirstPage);
            todaySource = new WeakReference<>(s);
            return s;
        }));
    }

    public Single<TodoPageVO> getToday(String filter, int page, int size) {
        return api.getTodayTodos(filter, page, size).compose(RxResult.unwrap());
    }

    public void invalidateToday() {
        invalidate(todaySource);
    }

    /** API-T03 日期汇总 */
    @NonNull
    public Single<TodoCalendarResult> getCalendar(@NonNull String startDate, @NonNull String endDate,
                                                  @Nullable String scope, @Nullable String category) {
        return api.getCalendar(startDate, endDate, scope, category).compose(RxResult.unwrap());
    }

    /** API-T04 某天待办（分页） */
    @NonNull
    public Flowable<PagingData<TodoItemVO>> dayPager(@NonNull String date, @Nullable String scope,
                                                     @Nullable String category, @Nullable String status,
                                                     @Nullable Consumer<PageVO<TodoItemVO>> onFirstPage) {
        return PagingRx.getFlowable(new Pager<>(config(), () -> {
            DayTodoPagingSource s = new DayTodoPagingSource(api, date, scope, category, status, onFirstPage);
            daySource = new WeakReference<>(s);
            return s;
        }));
    }

    public void invalidateDay() {
        invalidate(daySource);
    }

    /** API-T04 某天待办（一次取 size 条，周视图“展开其余 N 项”） */
    @NonNull
    public Single<PageVO<TodoItemVO>> getDayTodos(@NonNull String date, @Nullable String scope,
                                                  @Nullable String category, int size) {
        return api.getDayTodos(date, scope, category, STATUS_ALL, 1, size).compose(RxResult.unwrap());
    }

    /** API-T05 周视图分组 */
    @NonNull
    public Single<TodoWeeklyResult> getWeekly(@NonNull String weekStart, @Nullable String scope,
                                              @Nullable String category, int perDay) {
        return api.getWeekly(weekStart, scope, category, perDay).compose(RxResult.unwrap());
    }

    /** API-T06 已逾期分组 */
    @NonNull
    public Single<PageVO<TodoItemVO>> getOverdue(@Nullable String scope, int page, int size) {
        return api.getOverdue(scope, page, size).compose(RxResult.unwrap());
    }

    /** API-T07 待办详情 */
    @NonNull
    public Single<TodoDetailVO> getDetail(long todoId) {
        return api.getTodoDetail(todoId).compose(RxResult.unwrap());
    }

    /** API-T08 手动标记完成 */
    @NonNull
    public Single<TodoDetailVO> complete(long todoId, @NonNull String idemKey, @NonNull TodoCompleteRequest body) {
        return api.completeTodo(todoId, idemKey, body).compose(RxResult.unwrap());
    }

    /** API-T09 忽略 */
    @NonNull
    public Single<TodoDetailVO> ignore(long todoId, @NonNull String idemKey, @NonNull TodoIgnoreRequest body) {
        return api.ignoreTodo(todoId, idemKey, body).compose(RxResult.unwrap());
    }

    private static void invalidate(@Nullable WeakReference<PagingSource<?, ?>> ref) {
        PagingSource<?, ?> s = ref == null ? null : ref.get();
        if (s != null) {
            s.invalidate();
        }
    }
}
