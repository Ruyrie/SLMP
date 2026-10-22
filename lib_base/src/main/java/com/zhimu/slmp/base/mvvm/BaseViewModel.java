package com.zhimu.slmp.base.mvvm;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.event.GlobalEvents;

import java.util.Collection;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * ViewModel 基类：订阅统一加入 disposables 并在 onCleared 清空；一次性提示 message；页面状态 uiState；
 * launch（io 执行、主线程回调）与 load（首次加载自动切换 LOADING / CONTENT / EMPTY / ERROR / NO_PERMISSION）；
 * handleError 把 ApiException 转为提示或状态。
 * <p>
 * SavedStateHandle 由 ViewModelFactory 在构造之后交给 attachSavedState，构造方法中还不能读取，
 * 需要读取页面参数时重写 onSavedStateAttached。
 */
public abstract class BaseViewModel extends ViewModel {

    protected final CompositeDisposable disposables = new CompositeDisposable();

    private final SingleLiveEvent<UiMessage> message = new SingleLiveEvent<>();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.content());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);

    @Nullable
    private SavedStateHandle savedState;
    /** 是否已经成功加载过内容；之后的 load 视为刷新，失败只提示不切换错误页 */
    private boolean contentLoaded;

    // ===== 对外 LiveData =====

    public SingleLiveEvent<UiMessage> getMessage() {
        return message;
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    /** 刷新中（已有内容时再次 load），绑定下拉刷新指示 */
    public LiveData<Boolean> getRefreshing() {
        return refreshing;
    }

    // ===== SavedStateHandle =====

    /** 由 ViewModelFactory 调用；handle 中带有 Intent extras / Fragment arguments */
    @MainThread
    public final void attachSavedState(@NonNull SavedStateHandle handle) {
        if (savedState != null) {
            return;
        }
        savedState = handle;
        onSavedStateAttached(handle);
    }

    /** 拿到 SavedStateHandle 后回调一次，可在这里读取页面参数并开始首次加载 */
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
    }

    /** 未经 ViewModelFactory 创建（例如单元测试）时返回一个空的 handle */
    @NonNull
    public SavedStateHandle getSavedState() {
        if (savedState == null) {
            savedState = new SavedStateHandle();
        }
        return savedState;
    }

    // ===== 请求执行 =====

    /** io 线程执行、主线程回调；失败交给 handleError */
    protected <T> Disposable launch(@NonNull Single<T> source, @NonNull Consumer<? super T> onSuccess) {
        return launch(source, onSuccess, this::handleError);
    }

    /** io 线程执行、主线程回调；onError 收到的一律是 ApiException */
    protected <T> Disposable launch(@NonNull Single<T> source, @NonNull Consumer<? super T> onSuccess,
                                    @NonNull Consumer<ApiException> onError) {
        Disposable d = source.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, t -> onError.accept(ApiException.from(t)));
        disposables.add(d);
        return d;
    }

    protected Disposable launch(@NonNull Completable source, @NonNull Action onComplete) {
        return launch(source, onComplete, this::handleError);
    }

    protected Disposable launch(@NonNull Completable source, @NonNull Action onComplete,
                                @NonNull Consumer<ApiException> onError) {
        Disposable d = source.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, t -> onError.accept(ApiException.from(t)));
        disposables.add(d);
        return d;
    }

    /**
     * 整页加载：首次加载显示骨架屏，成功后按数据是否为空切换 CONTENT / EMPTY，失败切换 ERROR 或 NO_PERMISSION；
     * 已有内容时视为刷新（refreshing 为 true），失败只用 Snackbar 提示。
     */
    protected <T> Disposable load(@NonNull Single<T> source, @NonNull Consumer<? super T> onSuccess) {
        final boolean refresh = contentLoaded;
        if (refresh) {
            setOrPost(refreshing, true);
        } else {
            setOrPost(uiState, UiState.loading());
        }
        return launch(source, data -> {
            refreshing.setValue(false);
            contentLoaded = true;
            onSuccess.accept(data);
            uiState.setValue(isEmptyData(data) ? UiState.empty(null, null) : UiState.content());
        }, e -> {
            refreshing.setValue(false);
            if (refresh) {
                handleError(e);
            } else {
                applyErrorState(e);
            }
        });
    }

    /** 判断加载结果是否为空：null、空集合、空 Map、EmptyAware.isEmptyContent()；可重写 */
    protected boolean isEmptyData(@Nullable Object data) {
        if (data == null) {
            return true;
        }
        if (data instanceof Collection) {
            return ((Collection<?>) data).isEmpty();
        }
        if (data instanceof Map) {
            return ((Map<?, ?>) data).isEmpty();
        }
        if (data instanceof EmptyAware) {
            return ((EmptyAware) data).isEmptyContent();
        }
        return false;
    }

    /** “重新加载”按钮与数据已变化时调用；有整页加载的页面重写为重新请求 */
    public void retry() {
    }

    /** 需要整页重新加载（例如切换养殖场后），下一次 load 重新显示骨架屏 */
    protected void resetContentLoaded() {
        contentLoaded = false;
    }

    // ===== 错误处理 =====

    /** 首次加载失败：切换页面状态 */
    protected void applyErrorState(@NonNull ApiException e) {
        int code = e.getCode();
        if (ErrorCodes.isAuthHandled(code)) {
            return;
        }
        if (code == ErrorCodes.FORCED_STEP_REQUIRED) {
            GlobalEvents.postForcedStep(e.dataString("next_step"));
            return;
        }
        if (ErrorCodes.isNoPermission(code)) {
            uiState.setValue(UiState.noPermission(e.getMessage(), code));
        } else {
            uiState.setValue(UiState.error(e.getMessage(), e.getTraceId(), code));
        }
    }

    /**
     * 操作失败（提交、刷新等）：网络错误提示“网络连接失败，请检查网络后重试”；40302/40303/40304 交给 onNoPermission（默认整页切换为无权查看）；
     * 40902/40903 提示后回调 onDataChanged；40001 先交给 onFieldErrors；40101/40104/40901 不提示；其余显示服务端 message。
     */
    @CallSuper
    protected void handleError(@NonNull Throwable t) {
        ApiException e = ApiException.from(t);
        int code = e.getCode();
        if (ErrorCodes.isAuthHandled(code) || code == ErrorCodes.DUPLICATE_SUBMIT) {
            return;
        }
        if (code == ErrorCodes.FORCED_STEP_REQUIRED) {
            GlobalEvents.postForcedStep(e.dataString("next_step"));
            return;
        }
        if (code == ErrorCodes.PARAM_INVALID && e.hasFieldErrors() && onFieldErrors(e.getFieldErrors())) {
            return;
        }
        if (ErrorCodes.isNoPermission(code)) {
            onNoPermission(e);
            return;
        }
        if (e.isNetwork()) {
            showMessage(ApiException.MSG_NETWORK);
            return;
        }
        showMessage(e.getMessage());
        if (ErrorCodes.isDataChanged(code)) {
            onDataChanged(e);
        }
    }

    /**
     * 40302 / 40303 / 40304：默认整页切换为 U03 无权查看；在底部面板或对话框中操作的页面重写为提示（showMessage）并关闭弹窗。
     */
    protected void onNoPermission(@NonNull ApiException e) {
        setUiState(UiState.noPermission(e.getMessage(), e.getCode()));
    }

    /** 40001 的字段错误；返回 true 表示已写入输入框，不再弹 Snackbar */
    protected boolean onFieldErrors(@NonNull Map<String, String> fieldErrors) {
        return false;
    }

    /** 40902 / 40903 数据已变化：默认重新加载当前页面 */
    protected void onDataChanged(@NonNull ApiException e) {
        retry();
    }

    // ===== 提示 =====

    protected void showMessage(@Nullable String text) {
        if (text != null && !text.isEmpty()) {
            setOrPost(message, UiMessage.of(text));
        }
    }

    protected void showMessage(@NonNull UiMessage msg) {
        setOrPost(message, msg);
    }

    protected void setUiState(@NonNull UiState state) {
        setOrPost(uiState, state);
    }

    private static <T> void setOrPost(MutableLiveData<T> live, T value) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            live.setValue(value);
        } else {
            live.postValue(value);
        }
    }

    @CallSuper
    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
