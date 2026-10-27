package com.zhimu.slmp.ai.ui.play;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.ai.AiTestRules;
import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayCommand;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.model.request.CourseProgressRequest;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CoursePlayViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public AiTestRules rules = new AiTestRules();
    private CourseRepository repository;
    private CoursePlayViewModel vm;
    private static final String URL = "http://127.0.0.1:9000/zmt-course/hls/240108/720p/index.m3u8?X-Amz-Signature=test";

    @Before public void setup() {
        repository = mock(CourseRepository.class);
        when(repository.progressChanges()).thenReturn(Observable.never());
        NetworkStatus network = mock(NetworkStatus.class);
        when(network.type()).thenReturn(AiCodes.NET_WIFI);
        when(network.isConnected()).thenReturn(true);
        vm = new CoursePlayViewModel(repository, network, mock(AppSettings.class));
    }

    private CoursePlayVO course(long id, String url) {
        return GsonFactory.get().fromJson("{\"id\":" + id + ",\"title\":\"真实课程\",\"duration_sec\":120,"
                + "\"cover_url\":\"http://127.0.0.1:9000/cover.png\",\"sources\":["
                + "{\"quality\":\"P720\",\"hls_url\":\"" + url + "\"},"
                + "{\"quality\":\"P480\",\"hls_url\":\"http://127.0.0.1:9000/480p/index.m3u8\"}]}", CoursePlayVO.class);
    }

    @Test public void playCommandKeepsTheRealHlsUrlAndSignature() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        assertEquals(URL, vm.getPlayCommand().getValue().getHlsUrl());
        assertEquals("http://127.0.0.1:9000/cover.png", vm.getPlay().getValue().getCoverUrl());
    }

    @Test public void foregroundRevalidationClearsTheCachedCommandAndKeepsPosition() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        SingleSubject<CoursePlayVO> pending = SingleSubject.create();
        when(repository.getCoursePlay(6305)).thenReturn(pending);
        vm.revalidate(12500, true);
        assertNull(vm.getPlayCommand().getValue());
        assertNull(vm.getCurrentSource().getValue());
        assertNull(vm.getPlay().getValue());
        pending.onSuccess(course(6305, URL + "2"));
        PlayCommand command = vm.getPlayCommand().getValue();
        assertEquals(URL + "2", command.getHlsUrl());
        assertEquals(12500, command.getStartMs());
        assertTrue(command.isPlayWhenReady());
    }

    @Test public void pausedPlaybackStaysPausedAfterRevalidation() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        vm.revalidate(4500, false);
        assertEquals(4500, vm.getPlayCommand().getValue().getStartMs());
        assertFalse(vm.getPlayCommand().getValue().isPlayWhenReady());
    }

    @Test public void offShelfOnResumeRemovesThePlayableSource() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        when(repository.getCoursePlay(6305)).thenReturn(Single.error(new ApiException(ErrorCodes.RECORD_DELETED, "这个视频已下架")));
        vm.revalidate(4500, true);
        assertNull(vm.getPlayCommand().getValue());
        assertNull(vm.getCurrentSource().getValue());
        assertNull(vm.getPlay().getValue());
        assertEquals(UiState.Status.EMPTY, vm.getUiState().getValue().getStatus());
        assertEquals("这个视频已下架", vm.getUiState().getValue().getTitle());
    }

    @Test public void aPreviousCourseResponseCannotReplaceTheNextCourse() {
        SingleSubject<CoursePlayVO> old = SingleSubject.create();
        when(repository.getCoursePlay(6305)).thenReturn(old);
        when(repository.getCoursePlay(6306)).thenReturn(Single.just(course(6306, URL + "-next")));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        vm.switchCourse(6306);
        old.onSuccess(course(6305, URL));
        assertEquals(6306, vm.getPlay().getValue().getId());
        assertEquals(URL + "-next", vm.getPlayCommand().getValue().getHlsUrl());
    }

    @Test public void qualitySelectionSurvivesARefreshedSource() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        vm.setQuality(AiCodes.QUALITY_480);
        vm.revalidate(4500, true);
        assertEquals(AiCodes.QUALITY_480, vm.getPlayCommand().getValue().getQuality());
    }

    @Test public void noPlayableVariantShowsAnErrorWithoutStartingOldMedia() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(
                GsonFactory.get().fromJson("{\"id\":6305,\"title\":\"等待媒体\",\"sources\":[]}", CoursePlayVO.class)));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        assertNull(vm.getPlayCommand().getValue());
        assertEquals(PlayerState.ERROR, vm.getPlayerState().getValue());
    }

    @Test public void endingReportsTheActualPositionInsteadOfTheCatalogDuration() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        when(repository.reportProgress(anyLong(), any())).thenReturn(Single.just(new ProgressResultVO()));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        vm.onPlayerStateChanged(PlayerState.PLAYING);
        vm.onPlaybackTick(10_500, 10_500, false, 1f);
        vm.onPlayerStateChanged(PlayerState.ENDED);
        ArgumentCaptor<CourseProgressRequest> request = ArgumentCaptor.forClass(CourseProgressRequest.class);
        verify(repository).reportProgressDetached(eq(6305L), request.capture());
        CourseProgressRequest end = request.getAllValues().stream()
                .filter(item -> AiCodes.EVENT_END.equals(item.getEvent())).findFirst().orElseThrow();
        assertEquals(10, end.getPositionSec());
    }

    @Test public void leavingReportsTheActualPlayerPositionBetweenTickerUpdates() {
        when(repository.getCoursePlay(6305)).thenReturn(Single.just(course(6305, URL)));
        when(repository.reportProgress(anyLong(), any())).thenReturn(Single.just(new ProgressResultVO()));
        vm.load(6305, AiCodes.ENTRY_COURSE);
        vm.onPlayerStateChanged(PlayerState.PLAYING);
        vm.onLeave(1900);
        ArgumentCaptor<CourseProgressRequest> request = ArgumentCaptor.forClass(CourseProgressRequest.class);
        verify(repository).reportProgressDetached(eq(6305L), request.capture());
        assertEquals(1, request.getValue().getPositionSec());
        assertEquals(AiCodes.EVENT_LEAVE, request.getValue().getEvent());
    }
}
