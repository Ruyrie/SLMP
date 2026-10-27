package com.zhimu.slmp.today.ui.collection;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.LinkNavigator;
import com.zhimu.slmp.today.util.NavTarget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import javax.inject.Inject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.SerialDisposable;

/** 日期无关的预警合集、可分页的逾期待办合集。 */
public class TodoCollectionViewModel extends BaseViewModel {
    private final TodoRepository repository;
    private final LinkNavigator navigator;
    private final SerialDisposable request = new SerialDisposable();
    private final MutableLiveData<List<TodoItemVO>> items = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> total = new MutableLiveData<>(0);
    private final MutableLiveData<UiState> listState = new MutableLiveData<>(UiState.loading());
    private final SingleLiveEvent<NavTarget> navigation = new SingleLiveEvent<>();
    private String filter = TodoRepository.FILTER_WARNING;
    private String scope = "CURRENT";
    private int nextPage = 1;
    private boolean loading;
    private boolean hasMore;
    private boolean initialized;
    private long loadedFarmId = -1;

    @Inject
    public TodoCollectionViewModel(TodoRepository repository, LinkNavigator navigator, TodayServiceImpl service) {
        this.repository = repository;
        this.navigator = navigator;
        disposables.add(request);
        disposables.add(service.todoChanges().observeOn(AndroidSchedulers.mainThread())
                .subscribe(change -> { if (initialized) refresh(); }, this::handleError));
    }

    public void init(String filter, String scope) {
        this.filter = TodoRepository.FILTER_OVERDUE.equals(filter) ? filter : TodoRepository.FILTER_WARNING;
        this.scope = isOverdue() && "ALL".equals(scope) ? "ALL" : "CURRENT";
        initialized = true;
    }

    public boolean isOverdue() { return TodoRepository.FILTER_OVERDUE.equals(filter); }
    public boolean showsFarm() { return "ALL".equals(scope); }
    public LiveData<List<TodoItemVO>> getItems() { return items; }
    public LiveData<Integer> getTotal() { return total; }
    public LiveData<UiState> getListState() { return listState; }
    public SingleLiveEvent<NavTarget> getNavigation() { return navigation; }
    public boolean hasMore() { return hasMore; }

    public void refreshForFarm(long farmId) {
        if (farmId != loadedFarmId) {
            loadedFarmId = farmId;
            items.setValue(Collections.emptyList());
            total.setValue(0);
        }
        refresh();
    }

    public void refresh() {
        request.set(io.reactivex.rxjava3.disposables.Disposable.disposed());
        loading = false;
        nextPage = 1;
        hasMore = true;
        loadNext();
    }

    public void loadNext() {
        if (!initialized || loading || !hasMore) return;
        loading = true;
        listState.setValue(UiState.loading());
        final int page = nextPage;
        Single<PageVO<TodoItemVO>> source = isOverdue() ? repository.getOverdue(scope, page, 20)
                : repository.getToday(TodoRepository.FILTER_WARNING, page, 20).map(value -> value);
        request.set(launch(source, result -> {
            LinkedHashMap<Long, TodoItemVO> merged = new LinkedHashMap<>();
            if (page > 1 && items.getValue() != null) {
                for (TodoItemVO item : items.getValue()) merged.put(item.getTodoId(), item);
            }
            for (TodoItemVO item : result.getList()) merged.put(item.getTodoId(), item);
            loading = false;
            nextPage = page + 1;
            hasMore = result.isHasMore() && !result.getList().isEmpty();
            total.setValue(result.getTotal());
            items.setValue(new ArrayList<>(merged.values()));
            listState.setValue(merged.isEmpty() ? UiState.empty(null, null) : UiState.content());
        }, error -> {
            loading = false;
            listState.setValue(UiState.error(error.getMessage(), error.getTraceId(), error.getCode()));
        }));
    }

    @Override public void retry() { loadNext(); }

    public void open(@NonNull TodoItemVO item) { launch(navigator.forTodo(item), navigation::setValue); }
    public void openDetail(@NonNull TodoItemVO item) { navigation.setValue(NavTarget.todoDetail(item.getTodoId())); }
}
