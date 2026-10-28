package com.zhimu.slmp.ai.ui.chat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiFragmentChatBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.CourseContext;
import com.zhimu.slmp.ai.model.InputMode;
import com.zhimu.slmp.ai.model.StreamPhase;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.ui.voice.VoiceInputController;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.camera.CameraContract;
import com.zhimu.slmp.media.camera.CameraResult;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.picker.PickImagesContract;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.ui.viewer.ViewerItem;
import com.zhimu.slmp.media.upload.UploadItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * K01 AI 问答（宿主 AssistantFragment）：关联批次 Chip 与面板、疫病横幅、对话区（流式追加、自动跟随与“回到底部”、
 * 加载更早保持阅读位置）、输入栏（参照 AI_PDIA：拍照 · 输入框 · 麦克风 · “+” · 发送 / 停止；语音输入“按住 说话”）。
 */
public class AiChatFragment extends BaseFragment {

    private static final String ACTION_MIC_SETTINGS = "ai_mic_settings";

    private AiFragmentChatBinding binding;
    private AiChatViewModel vm;
    private AiMessageAdapter adapter;
    private AttachThumbAdapter attachAdapter;
    private LinearLayoutManager layoutManager;
    @Nullable
    private VoiceInputController voice;
    private boolean following = true;
    private boolean ignoreTextChange;
    /** 加载更早前的阅读位置 */
    private long anchorId = Long.MIN_VALUE;
    private int anchorOffset;
    /** 申请麦克风权限的来源：true 为按住说话时（授权后提示），false 为点麦克风（授权后进入语音输入） */
    private boolean micFromTalk;
    @Nullable
    private Bundle pendingEntry;

    private ActivityResultLauncher<String> micPermission;
    private ActivityResultLauncher<CameraArgs> camera;
    private ActivityResultLauncher<Integer> picker;
    private ActivityResultLauncher<Intent> historyLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        micPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onMicResult);
        camera = registerForActivityResult(new CameraContract(), this::onCameraResult);
        picker = registerForActivityResult(new PickImagesContract(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                vm.addImages(uris);
            }
        });
        historyLauncher = Router.registerForResult(this, result -> {
            Intent data = result.getData();
            if (result.getResultCode() != android.app.Activity.RESULT_OK || data == null) {
                return;
            }
            long id = data.getLongExtra(RouterPath.EXTRA_SESSION_ID, 0);
            if (id > 0) {
                vm.loadSession(id);
            } else if (data.getBooleanExtra(RouterPath.EXTRA_NEW_SESSION, false)) {
                vm.newSession(null, null);
            } else {
                long[] deleted = data.getLongArrayExtra(com.zhimu.slmp.ai.AiExtras.RESULT_DELETED_SESSION_IDS);
                if (deleted != null) {
                    vm.onSessionsDeleted(deleted);
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AiFragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(AiInjector.factory(), AiChatViewModel.class);
        observeBase(vm);
        applyImeInsets();
        setupList();
        setupInput();
        setupObservers();
        if (pendingEntry != null) {
            Bundle b = pendingEntry;
            pendingEntry = null;
            handleEntry(b);
        }
    }

    // ===== 外部进入 =====

    /** AssistantFragment 转交的进入参数：T01 推荐问题、K05 课程背景、K02 新对话 */
    public void handleEntry(@NonNull Bundle args) {
        if (vm == null) {
            pendingEntry = args;
            return;
        }
        String question = args.getString(RouterPath.EXTRA_QUESTION);
        long courseId = args.getLong(RouterPath.EXTRA_COURSE_ID, 0);
        String courseTitle = args.getString(RouterPath.EXTRA_COURSE_TITLE);
        boolean newSession = args.getBoolean(RouterPath.EXTRA_NEW_SESSION, false);
        long sessionId = args.getLong(RouterPath.EXTRA_SESSION_ID, 0);
        if (sessionId > 0) {
            vm.loadSession(sessionId);
            return;
        }
        CourseContext course = courseId > 0 && courseTitle != null ? new CourseContext(courseId, courseTitle) : null;
        if (newSession || course != null || question != null) {
            vm.newSession(course, question);
        }
    }

    /** 离开 K01（切到养殖课堂、切换底部导航）：中断生成、取消录音 */
    public void onLeave() {
        if (vm != null) {
            vm.interruptForLeave();
        }
        if (voice != null) {
            voice.cancel();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            onLeave();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (requireActivity().isFinishing()) {
            onLeave();
        }
    }

    public void onHistoryClick() {
        vm.requestHistory();
    }

    public void onNewChatClick() {
        vm.startNewChat();
    }

    // ===== 列表 =====

    private void setupList() {
        RecyclerView list = binding.aiChatList;
        layoutManager = new LinearLayoutManager(requireContext());
        list.setLayoutManager(layoutManager);
        list.setItemAnimator(null);
        adapter = new AiMessageAdapter(new AiMessageAdapter.Listener() {
            @Override
            public void onCitation(@NonNull CitationBriefVO citation) {
                vm.onCitationClick(citation);
            }

            @Override
            public void onRegenerate(long roundId) {
                following = true;
                vm.regenerate(roundId);
            }

            @Override
            public void onRetrySend(long roundId) {
                following = true;
                vm.retrySend(roundId);
            }

            @Override
            public void onRate(long roundId, @NonNull String rating) {
                vm.rate(roundId, rating);
            }

            @Override
            public void onCopy(long roundId) {
                vm.copyAnswer(roundId);
            }

            @Override
            public void onToggleProcess(long roundId) {
                vm.toggleProcess(roundId);
            }

            @Override
            public void onImageClick(@NonNull ChatItem item, int index) {
                openImages(item, index);
            }
        });
        list.setAdapter(adapter);
        int threshold = Math.round(48 * getResources().getDisplayMetrics().density);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int distance = distanceToBottom();
                boolean away = distance > threshold;
                if (dy < 0 && away) {
                    following = false;
                } else if (!away) {
                    following = true;
                }
                vm.onScrolledAway(away);
                if (dy < 0 && !rv.canScrollVertically(-1)) {
                    requestEarlier();
                }
            }
        });
        // 对话区变矮（软键盘弹出、输入栏变高）时，停在底部的保持贴底，最新一条露在输入栏上方
        list.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top < oldBottom - oldTop && following) {
                v.post(this::followBottom);
            }
        });
        ClickThrottle.setOnClickListener(binding.aiChatBackBottom, v -> {
            following = true;
            int last = adapter.getItemCount() - 1;
            if (last >= 0) {
                binding.aiChatList.smoothScrollToPosition(last);
            }
            binding.aiChatList.postDelayed(this::followBottom, 320);
        });
        binding.aiChatState.setOnRetryListener(v -> vm.retry());
    }

    private void requestEarlier() {
        if (!Boolean.TRUE.equals(vm.getHasMoreBefore().getValue())
                || Boolean.TRUE.equals(vm.getLoadingEarlier().getValue())) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first >= 0 && first < adapter.getItemCount()) {
            anchorId = adapter.getItemId(first);
            View v = layoutManager.findViewByPosition(first);
            anchorOffset = v == null ? 0 : v.getTop();
        }
        vm.loadEarlier();
    }

    private int distanceToBottom() {
        RecyclerView rv = binding.aiChatList;
        return rv.computeVerticalScrollRange() - rv.computeVerticalScrollOffset() - rv.computeVerticalScrollExtent();
    }

    /** 无动画对齐到底部（流式每 50 ms 更新时调用，避免平滑滚动卡顿） */
    private void followBottom() {
        if (binding == null) {
            return;
        }
        int d = distanceToBottom();
        if (d > 0) {
            binding.aiChatList.scrollBy(0, d);
        }
    }

    private void openImages(@NonNull ChatItem item, int index) {
        List<ViewerItem> items = new ArrayList<>();
        for (String s : item.localImages) {
            items.add(ViewerItem.local(s, getString(R.string.ai_mode_chat)));
        }
        if (!item.remoteImages.isEmpty()) {
            MediaViewer.openFiles(requireContext(), item.remoteImages, index, getString(R.string.ai_mode_chat));
            return;
        }
        MediaViewer.open(requireContext(), items, index);
    }

    // ===== 输入栏 =====

    private void setupInput() {
        binding.aiChatInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AiText.MAX_QUESTION)});
        binding.aiChatInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) {
                    return;
                }
                String v = s.toString();
                if (!Objects.equals(vm.getInputText().getValue(), v)) {
                    vm.getInputText().setValue(v);
                }
            }
        });
        attachAdapter = new AttachThumbAdapter(new AttachThumbAdapter.Listener() {
            @Override
            public void onRemove(@NonNull UploadItem item) {
                vm.removeImage(item.getId());
            }

            @Override
            public void onRetry(@NonNull UploadItem item) {
                vm.retryUpload(item.getId());
            }

            @Override
            public void onPreview(@NonNull UploadItem item, int index) {
                List<UploadItem> all = vm.getAttachments().getValue();
                if (all == null) {
                    return;
                }
                List<ViewerItem> items = new ArrayList<>();
                for (UploadItem u : all) {
                    items.add(ViewerItem.from(u, getString(R.string.ai_mode_chat)));
                }
                MediaViewer.open(requireContext(), items, Math.max(0, index));
            }
        });
        binding.aiChatAttachList.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        binding.aiChatAttachList.setAdapter(attachAdapter);

        ClickThrottle.setOnClickListener(binding.aiChatCamera, v -> {
            if (vm.checkCanAttach()) {
                camera.launch(CameraArgs.photo(MediaScene.AI_IMAGE, Math.min(vm.remainingImages(), AiText.MAX_IMAGES))
                        .title(getString(R.string.ai_chat_camera)));
            }
        });
        ClickThrottle.setOnClickListener(binding.aiChatAlbum, v -> {
            if (vm.checkCanAttach()) {
                picker.launch(vm.remainingImages());
            }
        });
        binding.aiChatMic.setOnClickListener(v -> {
            if (!Boolean.TRUE.equals(vm.getVoiceEnabled().getValue())) {
                return;
            }
            if (hasMicPermission()) {
                enterVoice();
            } else {
                micFromTalk = false;
                micPermission.launch(Manifest.permission.RECORD_AUDIO);
            }
        });
        binding.aiChatKeyboard.setOnClickListener(v -> {
            vm.setInputMode(InputMode.TEXT);
            focusInput();
        });
        ClickThrottle.setOnClickListener(binding.aiChatAction, v -> {
            if (vm.getStreamPhase().getValue() != StreamPhase.IDLE) {
                vm.stop();
            } else {
                following = true;
                vm.send();
            }
        });
        ClickThrottle.setOnClickListener(binding.aiChatBatchChip, v -> LinkBatchSheet.show(getChildFragmentManager()));
        binding.aiChatCourseClose.setOnClickListener(v -> vm.clearCourseContext());
        binding.aiChatEpidemic.setNotice(getString(R.string.ai_chat_epidemic_title),
                getString(R.string.ai_chat_epidemic_text));
        binding.aiChatEpidemic.setIcon(com.zhimu.slmp.base.R.drawable.ic_emergency);
        binding.aiChatEpidemic.setAction(getString(R.string.ai_chat_epidemic_action), v -> vm.onInspectReportClick());

        voice = VoiceInputController.attach(binding.aiChatTalk, binding.aiChatInputArea, getViewLifecycleOwner(),
                new VoiceInputController.Host() {
                    @Override
                    public boolean canStartVoice() {
                        return Boolean.TRUE.equals(vm.getVoiceEnabled().getValue());
                    }

                    @Override
                    public boolean hasMicPermission() {
                        return AiChatFragment.this.hasMicPermission();
                    }

                    @Override
                    public void requestMicPermission() {
                        micFromTalk = true;
                        micPermission.launch(Manifest.permission.RECORD_AUDIO);
                    }

                    @Override
                    public void onRecorded(@NonNull File wav, long durationMs) {
                        vm.recognizeSpeech(wav, durationMs);
                    }

                    @Override
                    public void onTooShort() {
                        vm.onVoiceTooShort();
                    }

                    @Override
                    public void onUnavailable() {
                        vm.onVoiceUnavailable();
                    }

                    @NonNull
                    @Override
                    public LiveData<Boolean> recognizing() {
                        return vm.getVoiceRecognizing();
                    }
                });
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onMicResult(boolean granted) {
        if (granted) {
            if (micFromTalk) {
                showMessage(AiText.MSG_MIC_GRANTED);
            } else {
                enterVoice();
            }
            return;
        }
        // 麦克风被拒（2026-10-22 用户确认）：引导去系统设置，不录音，留在文字输入（照片仍可选）
        if (!micFromTalk) {
            vm.setInputMode(InputMode.TEXT);
        }
        showMessage(UiMessage.withAction(AiText.MSG_MIC_DENIED, getString(R.string.ai_chat_go_settings),
                ACTION_MIC_SETTINGS));
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (ACTION_MIC_SETTINGS.equals(actionId)) {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null));
            startActivity(i);
        }
    }

    private void enterVoice() {
        vm.setInputMode(InputMode.VOICE);
        hideKeyboard();
    }

    private void onCameraResult(@Nullable CameraResult result) {
        if (result != null && result.getType() == CameraResult.TYPE_PHOTOS) {
            vm.addCapturedFiles(result.getPhotoPaths());
        }
    }

    private void focusInput() {
        binding.aiChatInput.requestFocus();
        Editable e = binding.aiChatInput.getText();
        binding.aiChatInput.setSelection(e == null ? 0 : e.length());
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.aiChatInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        binding.aiChatInput.clearFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.aiChatInput.getWindowToken(), 0);
        }
    }

    /** 输入栏各按钮的显隐（UI 文档 2.2 节“输入栏”与“流式回答”表） */
    private void renderInputBar() {
        if (binding == null) {
            return;
        }
        boolean voiceMode = vm.getInputMode().getValue() == InputMode.VOICE;
        String text = vm.getInputText().getValue();
        boolean hasText = text != null && !text.trim().isEmpty();
        List<UploadItem> attachments = vm.getAttachments().getValue();
        boolean hasAttach = attachments != null && !attachments.isEmpty();
        boolean generating = vm.getStreamPhase().getValue() != StreamPhase.IDLE;
        boolean canAttach = Boolean.TRUE.equals(vm.getCanAttach().getValue());
        boolean voiceOk = Boolean.TRUE.equals(vm.getVoiceEnabled().getValue());

        binding.aiChatInput.setVisibility(voiceMode ? View.GONE : View.VISIBLE);
        binding.aiChatTalk.setVisibility(voiceMode ? View.VISIBLE : View.GONE);
        binding.aiChatKeyboard.setVisibility(voiceMode ? View.VISIBLE : View.GONE);
        boolean compact = !voiceMode && hasText;
        binding.aiChatCamera.setVisibility(voiceMode || compact ? View.GONE : View.VISIBLE);
        binding.aiChatAlbum.setVisibility(voiceMode || compact ? View.GONE : View.VISIBLE);
        binding.aiChatMic.setVisibility(voiceMode || compact ? View.GONE : View.VISIBLE);
        binding.aiChatCamera.setAlpha(canAttach ? 1f : 0.38f);
        binding.aiChatAlbum.setAlpha(canAttach ? 1f : 0.38f);
        binding.aiChatMic.setAlpha(voiceOk ? 1f : 0.38f);
        if (voice != null) {
            voice.setAvailable(voiceOk);
        }
        boolean showAction = generating || (!voiceMode && (hasText || hasAttach));
        binding.aiChatAction.setVisibility(showAction ? View.VISIBLE : View.GONE);
        if (generating) {
            binding.aiChatAction.setImageResource(R.drawable.ai_ic_stop_filled);
            binding.aiChatAction.setContentDescription(getString(R.string.ai_chat_stop));
            binding.aiChatAction.setEnabled(true);
        } else {
            binding.aiChatAction.setImageResource(com.zhimu.slmp.base.R.drawable.ic_arrow_upward);
            binding.aiChatAction.setContentDescription(getString(R.string.ai_chat_send));
            binding.aiChatAction.setEnabled(Boolean.TRUE.equals(vm.getCanSend().getValue()));
        }
        int hint;
        if (hasAttach) {
            hint = R.string.ai_chat_hint_images;
        } else {
            List<ChatItem> items = vm.getChatItems().getValue();
            hint = items == null || items.isEmpty() ? R.string.ai_chat_hint_new : R.string.ai_chat_hint_follow;
        }
        binding.aiChatInput.setHint(hint);
        binding.aiChatPill.setBackgroundResource(hasAttach ? R.drawable.ai_bg_composer_tall : R.drawable.ai_bg_composer);
        binding.aiChatAttachList.setVisibility(hasAttach ? View.VISIBLE : View.GONE);
    }

    // ===== 观察 =====

    private void setupObservers() {
        androidx.lifecycle.LifecycleOwner owner = getViewLifecycleOwner();
        vm.getUiState().observe(owner, state -> {
            binding.aiChatState.render(state);
            updateEmpty();
        });
        vm.getChatItems().observe(owner, items -> {
            boolean wasEmpty = adapter.getItemCount() == 0;
            adapter.submitList(items, () -> {
                if (binding == null) {
                    return;
                }
                if (anchorId != Long.MIN_VALUE) {
                    int pos = adapter.positionOfId(anchorId);
                    anchorId = Long.MIN_VALUE;
                    if (pos >= 0) {
                        layoutManager.scrollToPositionWithOffset(pos, anchorOffset);
                        return;
                    }
                }
                if (following || wasEmpty) {
                    binding.aiChatList.post(this::followBottom);
                }
            });
            updateEmpty();
            renderInputBar();
        });
        vm.getStreamingText().observe(owner, text -> {
            adapter.setStreaming(vm.getStreamingMessageId().getValue(), text == null ? "" : text);
            if (following) {
                binding.aiChatList.post(this::followBottom);
            }
        });
        vm.getScrollToBottom().observe(owner, v -> {
            following = true;
            binding.aiChatList.post(this::followBottom);
        });
        vm.getShowBackToBottom().observe(owner, show -> fade(binding.aiChatBackBottom, Boolean.TRUE.equals(show)));
        vm.getLoadingEarlier().observe(owner, l ->
                binding.aiChatLoadingEarlier.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.GONE));
        vm.getBatchChipText().observe(owner, t -> binding.aiChatBatchChip.setText(t));
        setChipIcons();
        vm.getShowEpidemicBanner().observe(owner, show ->
                binding.aiChatEpidemic.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE));
        vm.getContextCourse().observe(owner, c -> {
            binding.aiChatCourseRow.setVisibility(c == null ? View.GONE : View.VISIBLE);
            if (c != null) {
                binding.aiChatCourseText.setText(getString(R.string.ai_chat_context_course, c.getTitle()));
            }
        });
        vm.getInputText().observe(owner, t -> {
            String current = binding.aiChatInput.getText() == null ? "" : binding.aiChatInput.getText().toString();
            String v = t == null ? "" : t;
            if (!current.equals(v)) {
                ignoreTextChange = true;
                binding.aiChatInput.setText(v);
                binding.aiChatInput.setSelection(v.length());
                ignoreTextChange = false;
            }
            renderInputBar();
        });
        vm.getInputCountText().observe(owner, t -> {
            binding.aiChatCount.setVisibility(t == null ? View.GONE : View.VISIBLE);
            binding.aiChatCount.setText(t);
        });
        vm.getAttachments().observe(owner, list -> {
            boolean added = attachAdapter.submit(list, vm.getAttachHint().getValue());
            if (added && list != null && !list.isEmpty()) {
                binding.aiChatAttachList.scrollToPosition(attachAdapter.getItemCount() - 1);
            }
            renderInputBar();
        });
        vm.getAttachHint().observe(owner, h -> attachAdapter.submit(vm.getAttachments().getValue(), h));
        vm.getCanSend().observe(owner, v -> renderInputBar());
        vm.getCanAttach().observe(owner, v -> renderInputBar());
        vm.getStreamPhase().observe(owner, p -> renderInputBar());
        vm.getInputMode().observe(owner, m -> renderInputBar());
        vm.getVoiceEnabled().observe(owner, v -> renderInputBar());
        vm.getUploadMessage().observe(owner, this::showMessage);

        vm.getRestoreInput().observe(owner, v -> {
            if (vm.getInputMode().getValue() != InputMode.VOICE) {
                focusInput();
            }
        });
        vm.getFillRecognized().observe(owner, text -> {
            focusInput();
            if (voice != null) {
                voice.announceFilled(binding.aiChatInput);
            }
        });
        vm.getCopyText().observe(owner, text -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.ai_mode_chat), text));
            }
        });
        vm.getOpenKnowledge().observe(owner, c -> {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_KNOWLEDGE_ID, c.getKnowledgeId());
            b.putLong(RouterPath.EXTRA_CITATION_ID, c.getId());
            Router.open(requireContext(), RouterPath.K03, b);
        });
        vm.getOpenInspectReport().observe(owner, arr -> {
            Bundle b = new Bundle();
            LinkedBatchVO batch = arr == null || arr.length == 0 ? null : arr[0];
            if (batch != null) {
                b.putLong(RouterPath.EXTRA_BATCH_ID, batch.getBatchId());
                if (batch.getPenId() > 0) {
                    b.putLong(RouterPath.EXTRA_PEN_ID, batch.getPenId());
                }
            }
            Router.open(requireContext(), RouterPath.I05, b);
        });
        vm.getOpenHistory().observe(owner, v -> Router.openForResult(requireContext(), historyLauncher,
                RouterPath.K02, null));
    }

    private void setChipIcons() {
        android.graphics.drawable.Drawable link = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                requireContext(), com.zhimu.slmp.base.R.drawable.ic_link);
        android.graphics.drawable.Drawable drop = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                requireContext(), com.zhimu.slmp.base.R.drawable.ic_arrow_drop_down);
        int size = Math.round(18 * getResources().getDisplayMetrics().density);
        int primary = com.google.android.material.color.MaterialColors.getColor(binding.aiChatBatchChip,
                androidx.appcompat.R.attr.colorPrimary);
        if (link != null) {
            link = link.mutate();
            link.setBounds(0, 0, size, size);
            link.setTint(primary);
        }
        if (drop != null) {
            drop = drop.mutate();
            drop.setBounds(0, 0, size, size);
            drop.setTint(primary);
        }
        binding.aiChatBatchChip.setCompoundDrawablesRelative(link, null, drop, null);
        binding.aiChatBatchChip.setContentDescription(getString(R.string.ai_chat_batch_chip));
    }

    private void updateEmpty() {
        if (binding == null) {
            return;
        }
        UiState s = vm.getUiState().getValue();
        List<ChatItem> items = vm.getChatItems().getValue();
        boolean empty = (s == null || s.is(UiState.Status.CONTENT)) && (items == null || items.isEmpty());
        binding.aiChatEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private static void fade(@NonNull View v, boolean show) {
        if (show == (v.getVisibility() == View.VISIBLE)) {
            return;
        }
        v.animate().cancel();
        if (show) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(200).start();
        } else {
            v.animate().alpha(0f).setDuration(150).withEndAction(() -> v.setVisibility(View.GONE)).start();
        }
    }

    /**
     * 软键盘弹出时整条输入栏贴在键盘上方。宿主已避让的部分不会再下发到这里：主页一级页容器按键盘高度加底部内边距
     * 并从下发的 insets 中扣除，单独运行时 BaseActivity 在根视图上处理并消费；这里只补宿主没处理、仍下发到本页的
     * 软键盘高度，不再按位置推算，避免重复计算。对话区因此变矮时的贴底见 setupList。
     */
    private void applyImeInsets() {
        View root = binding.getRoot();
        final int base = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int pad = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            if (v.getPaddingBottom() != base + pad) {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + pad);
            }
            return insets;
        });
    }

    @Override
    public void onDestroyView() {
        if (voice != null) {
            voice.release();
            voice = null;
        }
        binding = null;
        super.onDestroyView();
    }
}
