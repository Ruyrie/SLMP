package com.zhimu.slmp.ai.ui.voice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.ai.R;

import java.io.File;

/**
 * K01 语音输入时“按住 说话”按钮的界面控制（参照 AI_PDIA 输入栏的交互与结构；技术文档 2.6.4 的 HoldToTalkButton 行为）：
 * 按钮的触摸 / 点按、按钮文字与状态、{@link VoiceRecorder} 录音、{@link VoicePanel} 录音浮层，状态判断交给 {@link VoiceGesture}。
 * <ul>
 *     <li>按下：生成中或识别中不录音；没有麦克风权限时申请权限、本次不录音；否则振动一下立即录音，按钮“松开 识别”；</li>
 *     <li>上滑超过 80 dp 进入取消区“松开 取消”；50 秒起“还能说 N 秒”；60 秒自动结束并识别；</li>
 *     <li>松开：取消区内丢弃；不足 1 秒提示“说话时间太短”；否则“识别中…”，WAV 交给宿主识别；</li>
 *     <li>ACTION_CANCEL、页面 onStop、按钮不可见（切到养殖课堂、切换底部导航、切回文字）时取消录音并释放麦克风；</li>
 *     <li>开启 TalkBack 时改为点按开始、再次点按结束并识别，并播报状态。</li>
 * </ul>
 * 只在主线程使用。
 */
public final class VoiceInputController implements DefaultLifecycleObserver {

    /** 宿主（AiChatFragment，转交 AiChatViewModel） */
    public interface Host {
        /** 是否允许开始录音（生成中、识别中为 false） */
        boolean canStartVoice();

        boolean hasMicPermission();

        /** 申请麦克风权限（本次不录音） */
        void requestMicPermission();

        /** 录音完成 */
        void onRecorded(@NonNull File wav, long durationMs);

        void onTooShort();

        /** 麦克风被占用或无法录音 */
        void onUnavailable();

        /** 正在识别 */
        @NonNull
        LiveData<Boolean> recognizing();
    }

    /** 录音文件目录（cacheDir 下） */
    static final String VOICE_DIR = "voice";
    private static final long TICK_MS = 100;

    private final TextView talk;
    private final LifecycleOwner owner;
    private final Host host;
    private final VoicePanel panel;
    private final VoiceGesture gesture;
    @Nullable
    private final AccessibilityManager accessibility;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::tick;
    private final View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View v) {
            render();
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull View v) {
            cancelRecording();
            panel.release();
        }
    };

    @Nullable
    private VoiceRecorder recorder;
    private int session;
    private boolean finishing;
    private boolean recognizing;
    private boolean released;
    @StringRes
    private int actionLabel;

    private VoiceInputController(@NonNull TextView talk, @NonNull View anchor, @NonNull LifecycleOwner owner,
                                 @NonNull Host host) {
        Context context = talk.getContext();
        this.talk = talk;
        this.owner = owner;
        this.host = host;
        this.panel = new VoicePanel(anchor);
        this.gesture = new VoiceGesture(VoiceLevelView.dp(context, VoiceGesture.CANCEL_DISTANCE_DP));
        this.accessibility = context.getSystemService(AccessibilityManager.class);
    }

    /**
     * @param talk   胶囊中的“按住 说话”按钮
     * @param anchor 整个输入栏，浮层显示在它上方 16 dp
     */
    @NonNull
    public static VoiceInputController attach(@NonNull TextView talk, @NonNull View anchor,
                                              @NonNull LifecycleOwner owner, @NonNull Host host) {
        VoiceInputController c = new VoiceInputController(talk, anchor, owner, host);
        c.setup();
        return c;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setup() {
        talk.setOnTouchListener(this::onTouch);
        talk.setOnClickListener(v -> onTalkClick());
        talk.addOnAttachStateChangeListener(attachListener);
        ViewCompat.setAccessibilityDelegate(talk, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        owner.getLifecycle().addObserver(this);
        host.recognizing().observe(owner, value -> {
            recognizing = Boolean.TRUE.equals(value);
            render();
        });
        render();
    }

    /** 页面停止：取消录音（切到后台、来电、锁屏） */
    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        cancelRecording();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        release();
    }

    /** 宿主隐藏（切到养殖课堂、切换底部导航）时调用：立即停止并丢弃 */
    public void cancel() {
        cancelRecording();
    }

    public void release() {
        if (released) {
            return;
        }
        cancelRecording();
        released = true;
        handler.removeCallbacks(ticker);
        talk.setOnTouchListener(null);
        talk.setOnClickListener(null);
        talk.removeOnAttachStateChangeListener(attachListener);
        owner.getLifecycle().removeObserver(this);
        panel.release();
    }

    /** 生成中“按住 说话”文字 38% 透明度、按住不录音 */
    public void setAvailable(boolean available) {
        float alpha = available || gesture.isRecording() ? 1f : 0.38f;
        if (talk.getAlpha() != alpha) {
            talk.setAlpha(alpha);
        }
    }

    /** 识别结果已填入：开启 TalkBack 时播报“已填入识别结果” */
    public void announceFilled(@NonNull View source) {
        announce(source, R.string.ai_voice_a11y_done);
    }

    // ===== 触摸与点按 =====

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(@NonNull View v, @NonNull MotionEvent ev) {
        if (isTouchExploration()) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.drawableHotspotChanged(ev.getX(), ev.getY());
                v.setPressed(true);
                ViewParent parent = v.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                if (gesture.isRecording()) {
                    handle(gesture.release(now()));
                } else {
                    begin(ev.getRawY(), false);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isHolding() && gesture.move(ev.getRawY())) {
                    render();
                }
                return true;
            case MotionEvent.ACTION_UP:
                v.setPressed(false);
                if (isHolding()) {
                    handle(gesture.release(now()));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                v.setPressed(false);
                if (isHolding()) {
                    cancelRecording();
                }
                return true;
            default:
                return true;
        }
    }

    private void onTalkClick() {
        if (gesture.isRecording()) {
            handle(gesture.release(now()));
            return;
        }
        if (begin(0f, true)) {
            announce(talk, R.string.ai_voice_a11y_started);
        }
    }

    private boolean isHolding() {
        return gesture.isRecording() && !gesture.isToggle();
    }

    // ===== 录音 =====

    private boolean begin(float downY, boolean toggle) {
        if (released || finishing || recognizing || !host.canStartVoice()) {
            return false;
        }
        if (!host.hasMicPermission()) {
            host.requestMicPermission();
            return false;
        }
        talk.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        File dir = new File(talk.getContext().getCacheDir(), VOICE_DIR);
        File file = new File(dir, "voice_" + System.currentTimeMillis() + ".wav");
        gesture.start(now(), downY, toggle);
        final int id = ++session;
        recorder = VoiceRecorder.start(file, new VoiceRecorder.Listener() {
            @Override
            public void onLevel(float level) {
                if (id == session && gesture.isRecording()) {
                    panel.pushLevel(level);
                }
            }

            @Override
            public void onFinished(@NonNull File wav) {
                onRecorded(id, wav);
            }

            @Override
            public void onError() {
                onRecordError(id);
            }
        });
        panel.reset();
        render();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
        return true;
    }

    private void tick() {
        if (!gesture.isRecording()) {
            return;
        }
        if (!talk.isShown()) {
            cancelRecording();
            return;
        }
        VoiceGesture.Result result = gesture.tick(now());
        if (result != VoiceGesture.Result.NONE) {
            handle(result);
            return;
        }
        render();
        handler.postDelayed(ticker, TICK_MS);
    }

    private void handle(@NonNull VoiceGesture.Result result) {
        switch (result) {
            case CANCEL:
                discard();
                announce(talk, R.string.ai_voice_a11y_cancelled);
                break;
            case TOO_SHORT:
                discard();
                host.onTooShort();
                break;
            case RECOGNIZE:
                handler.removeCallbacks(ticker);
                if (recorder == null) {
                    render();
                    break;
                }
                finishing = true;
                recorder.stop();
                render();
                announce(talk, R.string.ai_voice_a11y_recognizing);
                break;
            default:
                break;
        }
    }

    private void onRecorded(int id, @NonNull File wav) {
        if (id == session) {
            finishing = false;
            recorder = null;
        }
        long durationMs = WavWriter.durationMs(Math.max(0, wav.length() - WavWriter.HEADER_SIZE));
        host.onRecorded(wav, durationMs);
        if (!released) {
            render();
        }
    }

    private void onRecordError(int id) {
        if (released || id != session) {
            return;
        }
        gesture.cancel();
        finishing = false;
        recorder = null;
        handler.removeCallbacks(ticker);
        render();
        host.onUnavailable();
    }

    private void cancelRecording() {
        if (gesture.isRecording()) {
            gesture.cancel();
            discard();
        }
    }

    private void discard() {
        handler.removeCallbacks(ticker);
        VoiceRecorder current = recorder;
        recorder = null;
        if (current != null) {
            current.cancel();
        }
        if (!released) {
            render();
        }
    }

    // ===== 界面 =====

    private void render() {
        if (released) {
            return;
        }
        Context context = talk.getContext();
        boolean busy = finishing || recognizing;
        if (gesture.isRecording()) {
            long now = now();
            panel.showRecording(VoiceGesture.formatTimer(gesture.elapsedMs(now)), hintText(context, now),
                    gesture.isCancelArmed());
        } else if (busy) {
            panel.showRecognizing(context.getString(R.string.ai_voice_recognizing));
        } else {
            panel.hide();
        }
        String text = context.getString(labelText(gesture.label(busy)));
        if (!TextUtils.equals(talk.getText(), text)) {
            talk.setText(text);
        }
        boolean cancel = gesture.isCancelArmed();
        if (talk.isActivated() != cancel) {
            talk.setActivated(cancel);
        }
        boolean enabled = gesture.isRecording() || !busy;
        if (talk.isEnabled() != enabled) {
            talk.setEnabled(enabled);
        }
        int label = gesture.isRecording() ? R.string.ai_voice_action_stop : R.string.ai_voice_action_start;
        if (label != actionLabel) {
            actionLabel = label;
            ViewCompat.replaceAccessibilityAction(talk, AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(label), null);
        }
    }

    @StringRes
    private static int labelText(@NonNull VoiceGesture.Label label) {
        switch (label) {
            case RELEASE_TO_RECOGNIZE:
                return R.string.ai_voice_talk_release;
            case RELEASE_TO_CANCEL:
                return R.string.ai_voice_hint_cancel;
            case TAP_TO_STOP:
                return R.string.ai_voice_action_stop;
            case RECOGNIZING:
                return R.string.ai_voice_recognizing;
            case HOLD_TO_TALK:
            default:
                return R.string.ai_voice_talk;
        }
    }

    @NonNull
    private String hintText(@NonNull Context context, long now) {
        switch (gesture.hint(now)) {
            case RELEASE_TO_CANCEL:
                return context.getString(R.string.ai_voice_hint_cancel);
            case COUNTDOWN:
                return context.getString(R.string.ai_voice_countdown, gesture.remainingSeconds(now));
            case TAP_TO_STOP:
                return context.getString(R.string.ai_voice_hint_toggle);
            case RELEASE_TO_RECOGNIZE:
            default:
                return context.getString(R.string.ai_voice_hint);
        }
    }

    private boolean isTouchExploration() {
        return accessibility != null && accessibility.isEnabled() && accessibility.isTouchExplorationEnabled();
    }

    @SuppressWarnings("deprecation")
    private void announce(@NonNull View source, @StringRes int text) {
        if (isTouchExploration()) {
            source.announceForAccessibility(source.getContext().getString(text));
        }
    }

    private static long now() {
        return SystemClock.uptimeMillis();
    }
}
