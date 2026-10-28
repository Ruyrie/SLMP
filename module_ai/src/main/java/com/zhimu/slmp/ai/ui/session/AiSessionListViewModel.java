package com.zhimu.slmp.ai.ui.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.ai.data.AiRepository;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * K02 历史会话（技术文档 2.4.2）：API-K01 分页，无关键词时按 date_group 插入分组标题；搜索 300 ms 防抖、≤50 字。
 */
public class AiSessionListViewModel extends BaseViewModel {

    private final AiRepository repository;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final LiveData<PagingData<SessionRow>> sessions;
    private final SingleLiveEvent<Long> openSession = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> newChat = new SingleLiveEvent<>();
    /** 删除成功后通知页面更新返回结果（本次已删除的全部会话 ID） */
    private final SingleLiveEvent<long[]> deletedIds = new SingleLiveEvent<>();
    private final List<Long> deleted = new ArrayList<>();
    private final PublishSubject<String> input = PublishSubject.create();
    @Nullable
    private PagingSource<Integer, AiSessionItemVO> source;

    @Inject
    public AiSessionListViewModel(@NonNull AiRepository repository) {
        this.repository = repository;
        Executor executor = command -> Schedulers.computation().scheduleDirect(command);
        PagingConfig config = new PagingConfig(BaseListViewModel.PAGE_SIZE, BaseListViewModel.PREFETCH_DISTANCE,
                false, BaseListViewModel.PAGE_SIZE);
        sessions = Transformations.switchMap(query, q -> {
            Pager<Integer, AiSessionItemVO> pager = new Pager<>(config, () -> {
                source = repository.sessionSource(q);
                return source;
            });
            boolean group = q == null || q.isEmpty();
            LiveData<PagingData<SessionRow>> rows = Transformations.map(PagingLiveData.getLiveData(pager), data -> {
                PagingData<SessionRow> mapped = PagingDataTransforms.map(data, executor, SessionRow::of);
                return group ? withHeaders(mapped, executor) : mapped;
            });
            return PagingLiveData.cachedIn(rows, ViewModelKt.getViewModelScope(this));
        });
        disposables.add(input.debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyKeyword));
    }

    @NonNull
    static PagingData<SessionRow> withHeaders(@NonNull PagingData<SessionRow> rows, @NonNull Executor executor) {
        return PagingDataTransforms.insertSeparators(rows, executor, (before, after) -> {
            if (after == null || after.isHeader()) {
                return null;
            }
            if (before == null || !Objects.equals(before.group(), after.group())) {
                String text = after.item.getDateGroupText();
                return SessionRow.header(text == null ? "" : text);
            }
            return null;
        });
    }

    public LiveData<PagingData<SessionRow>> getSessions() {
        return sessions;
    }

    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Boolean> getSearching() {
        return searching;
    }

    public SingleLiveEvent<Long> getOpenSession() {
        return openSession;
    }

    public SingleLiveEvent<Boolean> getNewChat() {
        return newChat;
    }

    public SingleLiveEvent<long[]> getDeletedIds() {
        return deletedIds;
    }

    /** 当前是否在搜索（空状态文案用） */
    public boolean hasKeyword() {
        String q = query.getValue();
        return q != null && !q.isEmpty();
    }

    public void setSearching(boolean on) {
        searching.setValue(on);
        if (!on) {
            keyword.setValue("");
            input.onNext("");
            applyKeyword("");
        }
    }

    public void onKeywordChanged(@Nullable String text) {
        keyword.setValue(text == null ? "" : text);
        input.onNext(text == null ? "" : text.trim());
    }

    private void applyKeyword(@NonNull String k) {
        if (k.length() > AiText.MAX_SESSION_KEYWORD) {
            showMessage("搜索内容不能超过 50 字");
            return;
        }
        if (!Objects.equals(query.getValue(), k)) {
            query.setValue(k);
        }
    }

    public void onSessionClick(@NonNull AiSessionItemVO item) {
        openSession.setValue(item.getId());
    }

    public void onNewChat() {
        newChat.setValue(true);
    }

    /** 长按菜单“重命名”：1–40 字，成功后刷新列表 */
    public void rename(long sessionId, @Nullable String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            showMessage("标题不能为空");
            return;
        }
        if (t.length() > AiText.MAX_SESSION_TITLE) {
            showMessage("标题不能超过 " + AiText.MAX_SESSION_TITLE + " 字");
            return;
        }
        launch(repository.renameSession(sessionId, t), vo -> {
            showMessage("已重命名");
            refresh();
        });
    }

    /** 长按菜单“删除”（二次确认后）：成功后刷新列表 */
    public void delete(long sessionId) {
        launch(repository.deleteSession(sessionId), () -> {
            if (!deleted.contains(sessionId)) {
                deleted.add(sessionId);
            }
            long[] ids = new long[deleted.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = deleted.get(i);
            }
            deletedIds.setValue(ids);
            showMessage("已删除会话");
            refresh();
        });
    }

    public void refresh() {
        if (source != null) {
            source.invalidate();
        }
    }

    @Override
    public void retry() {
        refresh();
    }
}
