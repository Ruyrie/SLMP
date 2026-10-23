package com.zhimu.slmp.ai.ui.topic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayArgs;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * K07 专题详情（技术文档 2.4.5）：API-K17；“已学 2 / 6”、“上次看到第 2 节 1:24”（START“还没有开始学习”，REVIEW“全部课程已学完”）、
 * 底部按钮“开始学习 / 继续学习 / 开始复习”；进度上报后重取；41001 显示“这个专题已下架”。
 */
public class TopicDetailViewModel extends BaseViewModel {

    /** 网页版专题地址（WU-K03；TopicDetailVO 没有 share_url，待确认正式域名） */
    public static final String WEB_TOPIC_URL = "https://web.zhimutong.example.com/course/topics/";

    private final CourseRepository repository;
    private final MutableLiveData<TopicDetailVO> topic = new MutableLiveData<>(null);
    private final MutableLiveData<String> progressText = new MutableLiveData<>(null);
    private final MutableLiveData<String> resumeText = new MutableLiveData<>(null);
    private final MutableLiveData<String> actionText = new MutableLiveData<>(null);
    private final SingleLiveEvent<PlayArgs> openPlay = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> shareText = new SingleLiveEvent<>();
    private long topicId;

    @Inject
    public TopicDetailViewModel(@NonNull CourseRepository repository) {
        this.repository = repository;
        disposables.add(repository.progressChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(id -> onProgressChanged()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(AiExtras.EXTRA_TOPIC_ID);
        if (id != null && id > 0) {
            load(id);
        }
    }

    public void load(long id) {
        topicId = id;
        load(repository.getTopic(id), this::apply);
    }

    @Override
    public void retry() {
        if (topicId > 0) {
            resetContentLoaded();
            load(topicId);
        }
    }

    public void onProgressChanged() {
        if (topicId > 0 && topic.getValue() != null) {
            launch(repository.getTopic(topicId), this::apply, e -> {
            });
        }
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.RECORD_DELETED) {
            setUiState(UiState.empty(e.getMessage() == null ? "这个专题已下架" : e.getMessage(), null));
            return;
        }
        super.applyErrorState(e);
    }

    void apply(@NonNull TopicDetailVO t) {
        topic.setValue(t);
        progressText.setValue(AiText.learned(t.getLearnedCount(), t.getCourseCount()));
        String action = t.getAction();
        if (TopicDetailVO.ACTION_REVIEW.equals(action)) {
            resumeText.setValue("全部课程已学完");
            actionText.setValue("开始复习");
        } else if (TopicDetailVO.ACTION_START.equals(action) || t.getResume() == null) {
            resumeText.setValue("还没有开始学习");
            actionText.setValue("开始学习");
        } else {
            TopicDetailVO.Resume r = t.getResume();
            resumeText.setValue("上次看到第 " + r.getSectionNo() + " 节 " + AiText.duration(r.getPositionSec()));
            actionText.setValue("继续学习");
        }
    }

    /** 底部按钮：续学位置的课程，没有则第 1 节 */
    public void continueLearning() {
        TopicDetailVO t = topic.getValue();
        if (t == null) {
            return;
        }
        Long id = null;
        if (t.getResume() != null && !TopicDetailVO.ACTION_REVIEW.equals(t.getAction())) {
            id = t.getResume().getCourseId();
        } else if (!t.getCourses().isEmpty()) {
            id = t.getCourses().get(0).getId();
        }
        if (id != null) {
            openPlay.setValue(new PlayArgs(id, AiCodes.ENTRY_TOPIC));
        }
    }

    public void openCourse(@NonNull CourseCardVO c) {
        openPlay.setValue(new PlayArgs(c.getId(), AiCodes.ENTRY_TOPIC));
    }

    public void share() {
        TopicDetailVO t = topic.getValue();
        if (t != null) {
            shareText.setValue(t.getName() + "\n" + WEB_TOPIC_URL + t.getId());
        }
    }

    public LiveData<TopicDetailVO> getTopic() {
        return topic;
    }

    public LiveData<String> getProgressText() {
        return progressText;
    }

    public LiveData<String> getResumeText() {
        return resumeText;
    }

    public LiveData<String> getActionText() {
        return actionText;
    }

    public SingleLiveEvent<PlayArgs> getOpenPlay() {
        return openPlay;
    }

    public SingleLiveEvent<String> getShareText() {
        return shareText;
    }

    @Nullable
    public TopicDetailVO current() {
        return topic.getValue();
    }
}
