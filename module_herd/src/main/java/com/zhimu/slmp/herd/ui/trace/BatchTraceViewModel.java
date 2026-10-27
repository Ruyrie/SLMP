package com.zhimu.slmp.herd.ui.trace;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;

import javax.inject.Inject;

/** The public page only reads the token endpoint. It never switches the user's current farm. */
public final class BatchTraceViewModel extends BaseViewModel {
    private final BatchRepository repository;
    private final MutableLiveData<BatchTraceVO> trace = new MutableLiveData<>();
    private String token;

    @Inject public BatchTraceViewModel(@NonNull BatchRepository repository) {
        this.repository = repository;
    }

    @Override protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        token = handle.get(HerdExtras.EXTRA_TRACE_TOKEN);
        retry();
    }

    @Override public void retry() {
        if (token == null || token.trim().isEmpty()) {
            setUiState(UiState.error("二维码内容不完整，请重新扫码", null, 0));
            return;
        }
        load(repository.getPublicTrace(token), trace::setValue);
    }

    public LiveData<BatchTraceVO> getTrace() { return trace; }
}
