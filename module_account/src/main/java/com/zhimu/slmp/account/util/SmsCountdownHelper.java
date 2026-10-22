package com.zhimu.slmp.account.util;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 短信验证码倒计时（2.6.4 节）：发送成功后按 data.cooldown_seconds 启动 Observable.interval（computation 线程），
 * 每秒递减，归零后尾部变为“重新获取”；42901 时按 data.cooldown_seconds 倒计时；40103 过期时置 0。
 * ViewModel 销毁时调用 {@link #dispose()}。
 */
public class SmsCountdownHelper {

    /** 默认冷却秒数（服务端未返回时） */
    public static final int DEFAULT_COOLDOWN = 60;

    private final MutableLiveData<Integer> countdown = new MutableLiveData<>(0);
    private final MutableLiveData<String> sentHint = new MutableLiveData<>(null);
    @Nullable
    private Disposable timer;

    /** 剩余秒数，0 为可获取 */
    @NonNull
    public LiveData<Integer> getCountdown() {
        return countdown;
    }

    /** “验证码已发送至 138****6620”，未发送为 null */
    @NonNull
    public LiveData<String> getSentHint() {
        return sentHint;
    }

    public boolean isCounting() {
        Integer v = countdown.getValue();
        return v != null && v > 0;
    }

    /** 发送成功 */
    public void onSent(@NonNull SmsCodeSendVO vo) {
        String masked = vo.getPhoneMasked();
        set(sentHint, masked == null ? "验证码已发送" : "验证码已发送至 " + masked);
        start(vo.getCooldownSeconds() > 0 ? vo.getCooldownSeconds() : DEFAULT_COOLDOWN);
    }

    /** 开始倒计时（42901 时也调用） */
    public void start(int seconds) {
        stop();
        if (seconds <= 0) {
            set(countdown, 0);
            return;
        }
        set(countdown, seconds);
        timer = Observable.interval(1, TimeUnit.SECONDS, Schedulers.computation())
                .take(seconds)
                .subscribe(i -> set(countdown, (int) (seconds - i - 1)), e -> set(countdown, 0));
    }

    /** 验证码过期：尾部立即变为“重新获取” */
    public void reset() {
        stop();
        set(countdown, 0);
    }

    public void dispose() {
        stop();
    }

    private void stop() {
        if (timer != null) {
            timer.dispose();
            timer = null;
        }
    }

    private static <T> void set(@NonNull MutableLiveData<T> live, @Nullable T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            live.setValue(value);
        } else {
            live.postValue(value);
        }
    }
}
