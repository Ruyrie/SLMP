package com.zhimu.slmp.ai.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.request.CourseProgressRequest;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.CourseCategoryVO;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.ai.model.vo.WatchHistoryVO;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * 养殖课堂数据（API-K14–K21）。每次进度上报成功后在 {@link #progressChanges()} 发布课程 ID（K04 更新覆盖表并重取继续观看，
 * K07 重取专题）；LEAVE、END 用 {@link #reportProgressDetached}（Repository 自身订阅，页面销毁不取消）。
 */
@FeatureScope
public class CourseRepository {

    private static final String TAG = "ZmtCourse";
    public static final String TOPIC_SORT_DEFAULT = "DEFAULT";
    public static final String COURSE_SORT_LATEST = "LATEST";

    private final AiApi api;
    private final PublishSubject<ProgressResultVO> progressResults = PublishSubject.create();
    private final PublishSubject<Long> changes = PublishSubject.create();

    @Inject
    public CourseRepository(@NonNull AiApi api) {
        this.api = api;
    }

    @NonNull
    public Single<List<CourseCategoryVO>> getCategories() {
        return api.categories().compose(RxResult.unwrap());
    }

    /** API-K15：data 为 null 时为空 */
    @NonNull
    public Maybe<ContinueWatchingVO> getContinue() {
        return api.continueWatching()
                .flatMapMaybe(r -> {
                    if (!r.isSuccess()) {
                        return Maybe.<ContinueWatchingVO>error(RxResult.fromResponse(r));
                    }
                    return r.getData() == null ? Maybe.<ContinueWatchingVO>empty() : Maybe.just(r.getData());
                })
                .onErrorResumeNext(t -> Maybe.error(RxResult.toApiException(t)));
    }

    @NonNull
    public Single<PageVO<TopicVO>> getTopics(@NonNull String sort, int page, int size) {
        return api.topics(sort, page, size).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<TopicDetailVO> getTopic(long topicId) {
        return api.topic(topicId).compose(RxResult.unwrap());
    }

    /** API-K18 分页：categoryId 为 null 为“全部”，keyword 去空白后为空不传 */
    @NonNull
    public PagedSource<CourseCardVO> courseSource(@Nullable Long categoryId, @Nullable String keyword) {
        String k = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return new PagedSource<>((page, size) ->
                api.courses(categoryId, null, k, COURSE_SORT_LATEST, page, size).compose(RxResult.unwrap()));
    }

    @NonNull
    public Single<CoursePlayVO> getCoursePlay(long courseId) {
        return api.coursePlay(courseId).compose(RxResult.unwrap());
    }

    /** API-K20（页面订阅，随页面取消） */
    @NonNull
    public Single<ProgressResultVO> reportProgress(long courseId, @NonNull CourseProgressRequest request) {
        return api.reportProgress(courseId, request).compose(RxResult.unwrap())
                .doOnSuccess(r -> publish(courseId, r));
    }

    /** API-K20（LEAVE、END）：应用级订阅，失败只记日志 */
    @NonNull
    public Completable reportProgressDetached(long courseId, @NonNull CourseProgressRequest request) {
        Completable c = api.reportProgress(courseId, request).compose(RxResult.unwrap())
                .doOnSuccess(r -> publish(courseId, r))
                .ignoreElement()
                .cache();
        //noinspection ResultOfMethodCallIgnored 应用级订阅，页面销毁后仍完成上报
        c.subscribeOn(Schedulers.io())
                .subscribe(() -> {
                }, e -> Log.w(TAG, "进度上报失败 course=" + courseId + " event=" + request.getEvent()));
        return c;
    }

    @NonNull
    public PagedSource<WatchHistoryVO> historySource() {
        return new PagedSource<>((page, size) -> api.history(page, size).compose(RxResult.unwrap()));
    }

    /** 进度变化的课程 ID（主线程外发布，订阅方自行切换线程） */
    @NonNull
    public Observable<Long> progressChanges() {
        return changes;
    }

    /** 进度上报结果（课程 ID 在结果中没有，配合 progressChanges 使用） */
    @NonNull
    public Observable<ProgressResultVO> progressResults() {
        return progressResults;
    }

    private void publish(long courseId, @NonNull ProgressResultVO r) {
        lastResults.put(courseId, r);
        progressResults.onNext(r);
        changes.onNext(courseId);
    }

    private final java.util.Map<Long, ProgressResultVO> lastResults = new java.util.concurrent.ConcurrentHashMap<>();

    /** 最近一次上报结果（K04 覆盖表按课程 ID 取） */
    @Nullable
    public ProgressResultVO lastResult(long courseId) {
        return lastResults.get(courseId);
    }
}
