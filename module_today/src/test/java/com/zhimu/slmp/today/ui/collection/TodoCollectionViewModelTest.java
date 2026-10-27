package com.zhimu.slmp.today.ui.collection;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.TodoPageVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.LinkNavigator;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TodoCollectionViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public RxTestRule rx = new RxTestRule();
    private TodoRepository repository;
    private TodoCollectionViewModel vm;

    @Before public void setup() {
        repository = mock(TodoRepository.class);
        TodayServiceImpl service = mock(TodayServiceImpl.class);
        when(service.todoChanges()).thenReturn(Observable.never());
        vm = new TodoCollectionViewModel(repository, mock(LinkNavigator.class), service);
        vm.init("WARNING", "ALL");
    }

    @Test public void warningCollectionUsesCurrentFarmAndPaginatesWithoutMixingOtherTypes() {
        when(repository.getToday("WARNING", 1, 20)).thenReturn(Single.just(page(1, true)));
        when(repository.getToday("WARNING", 2, 20)).thenReturn(Single.just(page(2, false)));
        vm.refresh();
        vm.loadNext();
        assertFalse(vm.showsFarm());
        assertEquals(2, vm.getItems().getValue().size());
        assertFalse(vm.hasMore());
        verify(repository).getToday("WARNING", 1, 20);
        verify(repository).getToday("WARNING", 2, 20);
        verifyNoMoreInteractions(repository);
    }

    @Test public void failedNextPageRetainsRowsAndRetryRequestsSamePage() {
        when(repository.getToday("WARNING", 1, 20)).thenReturn(Single.just(page(1, true)));
        when(repository.getToday("WARNING", 2, 20)).thenReturn(
                Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")), Single.just(page(2, false)));
        vm.refresh(); vm.loadNext();
        assertEquals(1, vm.getItems().getValue().size());
        assertEquals(UiState.Status.ERROR, vm.getListState().getValue().getStatus());
        vm.retry();
        assertEquals(2, vm.getItems().getValue().size());
        verify(repository, times(2)).getToday("WARNING", 2, 20);
    }

    @Test public void refreshCancelsOldRequestSoStaleWarningsCannotReappear() {
        SingleSubject<TodoPageVO> old = SingleSubject.create();
        when(repository.getToday("WARNING", 1, 20)).thenReturn(old, Single.just(new TodoPageVO()));
        vm.refresh(); vm.refresh();
        old.onSuccess(page(1, false));
        assertTrue(vm.getItems().getValue().isEmpty());
        assertEquals(Integer.valueOf(0), vm.getTotal().getValue());
    }

    private static TodoPageVO page(long id, boolean more) {
        return TestData.parse("{'list':[" + TestData.todo(id, "WARNING", "WARNING")
                + "],'total':2,'page':" + id + ",'size':20,'has_more':" + more + "}", TodoPageVO.class);
    }
}
