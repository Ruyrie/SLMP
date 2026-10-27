package com.zhimu.slmp.herd.ui.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import io.reactivex.rxjava3.core.Single;

public class BatchTraceViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();

    @Test public void publicPage_usesOnlyTokenEndpointAndCanRetry() {
        BatchRepository repository = mock(BatchRepository.class);
        BatchTraceVO trace = TestRules.parse("{'batch_no':'ZM2610-031','farm_name':'公开示范场',"
                + "'events':[{'type':'MEDICATION','date':'2026-10-20','name':'用药记录'}]}", BatchTraceVO.class);
        when(repository.getPublicTrace("publicQr123")).thenReturn(Single.error(ApiException.network(null)),
                Single.just(trace));
        BatchTraceViewModel vm = new BatchTraceViewModel(repository);
        vm.attachSavedState(new SavedStateHandle(Collections.singletonMap(HerdExtras.EXTRA_TRACE_TOKEN, "publicQr123")));
        assertEquals(UiState.Status.ERROR, vm.getUiState().getValue().getStatus());
        assertNull(vm.getTrace().getValue());
        vm.retry();
        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
        assertEquals("ZM2610-031", vm.getTrace().getValue().getBatchNo());
        verify(repository, times(2)).getPublicTrace("publicQr123");
        verify(repository, never()).getBatchDetail(anyLong());
        verify(repository, never()).lookupByNo(anyString());
    }

    @Test public void missingToken_showsErrorWithoutQueryingPrivateArchive() {
        BatchRepository repository = mock(BatchRepository.class);
        BatchTraceViewModel vm = new BatchTraceViewModel(repository);
        vm.attachSavedState(new SavedStateHandle());
        assertEquals(UiState.Status.ERROR, vm.getUiState().getValue().getStatus());
        verify(repository, never()).getPublicTrace(anyString());
        verify(repository, never()).getBatchDetail(anyLong());
    }
}
