package com.zhimu.slmp.ai.ui.course;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayArgs;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.CourseCategoryVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.network.model.PageVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * K04 养殖课堂（技术文档 2.4.4）：并行请求 K14 分类、K15 继续观看、K16 专题（DEFAULT、每页 20，待定事项 4），
 * 建立 K18 课程分页（分类 Chip、搜索 300 ms 防抖、≤30 字）；进度上报后按课程 ID 覆盖学习状态并重取继续观看。
 */
public class CourseHomeViewModel extends BaseViewModel {

    /** 课程分页条件 */
    static final class Filter {
        @Nullable
        final Long categoryId;
        @NonNull
        final String keyword;

        Filter(@Nullable Long categoryId, @NonNull String keyword) {
            this.categoryId = categoryId;
            this.keyword = keyword;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Filter)) {
                return false;
            }
            Filter f = (Filter) o;
            return Objects.equals(categoryId, f.categoryId) && keyword.equals(f.keyword);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId, keyword);
        }
    }

    private static final class Head {
        final List<CourseCategoryVO> categories;
        final PageVO<TopicVO> topics;
        final Optional<ContinueWatchingVO> cont;

        Head(List<CourseCategoryVO> categories, PageVO<TopicVO> topics, Optional<ContinueWatchingVO> cont) {
            this.categories = categories;
            this.topics = topics;
            this.cont = cont;
        }
    }

    private final CourseRepository repository;
    private final MutableLiveData<ContinueWatchingVO> continueWatching = new MutableLiveData<>(null);
    private final MutableLiveData<List<TopicVO>> topics = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> topicsExpanded = new MutableLiveData<>(false);
    private final MutableLiveData<List<CourseCategoryVO>> categories = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> selectedCategoryId = new MutableLiveData<>(null);
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final MutableLiveData<Filter> filter = new MutableLiveData<>(new Filter(null, ""));
    private final LiveData<PagingData<CourseCardVO>> courses;
    private final MutableLiveData<Map<Long, ProgressResultVO>> progressOverrides = new MutableLiveData<>(new HashMap<>());
    private final SingleLiveEvent<PlayArgs> openPlay = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openTopic = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> refreshCourses = new SingleLiveEvent<>();
    private final PublishSubject<String> input = PublishSubject.create();
    @Nullable
    private PagingSource<Integer, CourseCardVO> source;
    private boolean started;

    @Inject
    public CourseHomeViewModel(@NonNull CourseRepository repository) {
        this.repository = repository;
        PagingConfig config = new PagingConfig(BaseListViewModel.PAGE_SIZE, BaseListViewModel.PREFETCH_DISTANCE,
                false, BaseListViewModel.PAGE_SIZE);
        courses = Transformations.switchMap(filter, f -> {
            Pager<Integer, CourseCardVO> pager = new Pager<>(config, () -> {
                source = repository.courseSource(f.categoryId, f.keyword);
                return source;
            });
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });
        disposables.add(input.debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyKeyword));
        disposables.add(repository.progressChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onProgressChanged));
    }

    /** 首次显示时加载 */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        loadHead();
    }

    private void loadHead() {
        Single<Optional<ContinueWatchingVO>> cont = repository.getContinue().map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        load(Single.zip(repository.getCategories(), repository.getTopics(CourseRepository.TOPIC_SORT_DEFAULT, 1,
                BaseListViewModel.PAGE_SIZE), cont, Head::new), h -> {
            List<CourseCategoryVO> list = new ArrayList<>();
            CourseCategoryVO all = new CourseCategoryVO();
            all.setId(0);
            all.setName("全部");
            list.add(all);
            list.addAll(h.categories);
            categories.setValue(list);
            topics.setValue(h.topics.getList());
            continueWatching.setValue(h.cont.orElse(null));
        });
    }

    /** 下拉刷新：继续观看、专题进度和视频网格 */
    public void refresh() {
        loadHead();
        refreshCourses.setValue(true);
        if (source != null) {
            source.invalidate();
        }
    }

    @Override
    public void retry() {
        resetContentLoaded();
        refresh();
    }

    public void selectCategory(@Nullable Long categoryId) {
        Long id = categoryId == null || categoryId == 0 ? null : categoryId;
        selectedCategoryId.setValue(id);
        Filter f = filter.getValue();
        filter.setValue(new Filter(id, f == null ? "" : f.keyword));
    }

    public void setSearching(boolean on) {
        searching.setValue(on);
        if (!on) {
            keyword.setValue("");
            applyKeyword("");
        }
    }

    public void onKeywordChanged(@Nullable String text) {
        keyword.setValue(text == null ? "" : text);
        input.onNext(text == null ? "" : text.trim());
    }

    private void applyKeyword(@NonNull String k) {
        if (k.length() > AiText.MAX_COURSE_KEYWORD) {
            showMessage("搜索内容不能超过 30 字");
            return;
        }
        Filter f = filter.getValue();
        Filter next = new Filter(f == null ? null : f.categoryId, k);
        if (!next.equals(f)) {
            filter.setValue(next);
        }
    }

    public void toggleTopics() {
        topicsExpanded.setValue(!Boolean.TRUE.equals(topicsExpanded.getValue()));
    }

    /** 进度上报后：覆盖该课程的学习状态，并重取继续观看 */
    public void onProgressChanged(long courseId) {
        ProgressResultVO r = repository.lastResult(courseId);
        if (r != null) {
            Map<Long, ProgressResultVO> map = new HashMap<>(progressOverrides.getValue() == null
                    ? Collections.emptyMap() : progressOverrides.getValue());
            map.put(courseId, r);
            progressOverrides.setValue(map);
        }
        if (started) {
            launch(repository.getContinue().map(Optional::of).defaultIfEmpty(Optional.empty()),
                    c -> continueWatching.setValue(c.orElse(null)), e -> {
                    });
            launch(repository.getTopics(CourseRepository.TOPIC_SORT_DEFAULT, 1, BaseListViewModel.PAGE_SIZE),
                    p -> topics.setValue(p.getList()), e -> {
                    });
        }
    }

    public void onContinueClick() {
        ContinueWatchingVO c = continueWatching.getValue();
        if (c != null) {
            openPlay.setValue(new PlayArgs(c.getCourseId(), AiCodes.ENTRY_CONTINUE));
        }
    }

    public void onCourseClick(@NonNull CourseCardVO c) {
        openPlay.setValue(new PlayArgs(c.getId(), AiCodes.ENTRY_COURSE));
    }

    public void onTopicClick(@NonNull TopicVO t) {
        openTopic.setValue(t.getId());
    }

    /** 课程卡显示的学习状态（覆盖表优先） */
    @NonNull
    public String cardSub(@NonNull CourseCardVO c) {
        ProgressResultVO r = overrideOf(c.getId());
        if (r == null) {
            return AiText.courseCardSub(c);
        }
        return AiText.nz(c.getCategoryName()) + " · " + AiText.learnState(r.getLearnStatus(), r.getPositionSec());
    }

    @Nullable
    public ProgressResultVO overrideOf(long courseId) {
        Map<Long, ProgressResultVO> map = progressOverrides.getValue();
        return map == null ? null : map.get(courseId);
    }

    /** 网格为空时的文案 */
    @NonNull
    public String emptyText() {
        Filter f = filter.getValue();
        if (f != null && !f.keyword.isEmpty()) {
            return "没有找到相关课程，换个关键词试试";
        }
        if (f != null && f.categoryId != null) {
            return "这个分类还没有课程，先看看其他分类。";
        }
        return "课程还在准备中，上架后会出现在这里。";
    }

    // ===== getter =====

    public LiveData<ContinueWatchingVO> getContinueWatching() {
        return continueWatching;
    }

    public LiveData<List<TopicVO>> getTopics() {
        return topics;
    }

    public LiveData<Boolean> getTopicsExpanded() {
        return topicsExpanded;
    }

    public LiveData<List<CourseCategoryVO>> getCategories() {
        return categories;
    }

    public LiveData<Long> getSelectedCategoryId() {
        return selectedCategoryId;
    }

    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Boolean> getSearching() {
        return searching;
    }

    public LiveData<PagingData<CourseCardVO>> getCourses() {
        return courses;
    }

    public LiveData<Map<Long, ProgressResultVO>> getProgressOverrides() {
        return progressOverrides;
    }

    public SingleLiveEvent<PlayArgs> getOpenPlay() {
        return openPlay;
    }

    public SingleLiveEvent<Long> getOpenTopic() {
        return openTopic;
    }

    public SingleLiveEvent<Boolean> getRefreshCourses() {
        return refreshCourses;
    }
}
