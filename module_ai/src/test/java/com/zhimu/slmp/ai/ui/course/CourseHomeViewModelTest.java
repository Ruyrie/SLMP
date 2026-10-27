package com.zhimu.slmp.ai.ui.course;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.ai.AiTestRules;
import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCategoryVO;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CourseHomeViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public AiTestRules rules = new AiTestRules();
    private CourseRepository repository;
    private CourseHomeViewModel vm;

    @Before public void setup() {
        repository = mock(CourseRepository.class);
        when(repository.progressChanges()).thenReturn(Observable.never());
        when(repository.getCategories()).thenReturn(Single.just(Collections.emptyList()));
        when(repository.getTopics(anyString(), anyInt(), anyInt())).thenReturn(Single.just(
                new PageVO<TopicVO>(Collections.emptyList(), 0, 1, 20, false, null)));
        when(repository.getContinue()).thenReturn(Maybe.empty());
        vm = new CourseHomeViewModel(repository);
    }

    @Test public void returningToTheClassroomReloadsPublishedDataAndRemovesAnOldContinueCard() {
        ContinueWatchingVO old = new ContinueWatchingVO();
        old.setCourseId(6305);
        when(repository.getContinue()).thenReturn(Maybe.just(old));
        vm.onVisible();
        assertNotNull(vm.getContinueWatching().getValue());
        when(repository.getContinue()).thenReturn(Maybe.empty());
        vm.onVisible();
        assertNull(vm.getContinueWatching().getValue());
        verify(repository, times(2)).getCategories();
        verify(repository, times(2)).getContinue();
        verify(repository, times(2)).getTopics(anyString(), anyInt(), anyInt());
    }

    @Test public void repeatedVisibilityDoesNotDuplicateAnInFlightInitialLoad() {
        SingleSubject<List<CourseCategoryVO>> pending = SingleSubject.create();
        when(repository.getCategories()).thenReturn(pending);
        vm.onVisible();
        vm.onVisible();
        verify(repository, times(1)).getCategories();
        pending.onSuccess(Collections.emptyList());
    }
}
