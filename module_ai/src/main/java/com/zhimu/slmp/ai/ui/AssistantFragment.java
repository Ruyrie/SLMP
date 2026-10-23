package com.zhimu.slmp.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiFragmentAssistantBinding;
import com.zhimu.slmp.ai.ui.chat.AiChatFragment;
import com.zhimu.slmp.ai.ui.course.CourseHomeFragment;
import com.zhimu.slmp.ai.ui.history.WatchHistoryActivity;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 底部导航“助手”一级页（K01、K04 的宿主）：顶部栏 + 连接按钮组“AI 问答 / 养殖课堂”；右侧图标随模式替换
 * （history + add_comment ↔ search + history）。冷启动显示 AI 问答，同一次使用中切回时保留上次的模式；
 * 切到养殖课堂或整个标签被隐藏时中断 K01 的生成与录音。外部进入参数经 MainTabHost 标签参数（FragmentResult）或 arguments 传入。
 */
public class AssistantFragment extends BaseFragment {

    private static final String TAG_CHAT = "ai_child_chat";
    private static final String TAG_COURSE = "ai_child_course";
    private static final String STATE_MODE = "ai_assistant_mode";
    /** 同一次使用中保留上次停留的模式（进程内） */
    private static String lastMode = AiExtras.MODE_CHAT;

    private AiFragmentAssistantBinding binding;
    private AiChatFragment chat;
    private CourseHomeFragment course;
    @NonNull
    private String mode = AiExtras.MODE_CHAT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AiFragmentAssistantBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.aiTopBar.bind(requireActivity(), getViewLifecycleOwner());
        FragmentManager fm = getChildFragmentManager();
        chat = (AiChatFragment) fm.findFragmentByTag(TAG_CHAT);
        course = (CourseHomeFragment) fm.findFragmentByTag(TAG_COURSE);
        if (chat == null || course == null) {
            chat = chat == null ? new AiChatFragment() : chat;
            course = course == null ? new CourseHomeFragment() : course;
            fm.beginTransaction()
                    .add(R.id.ai_assistant_container, chat, TAG_CHAT)
                    .add(R.id.ai_assistant_container, course, TAG_COURSE)
                    .hide(course)
                    .commitNow();
        }
        String initial = savedInstanceState != null ? savedInstanceState.getString(STATE_MODE, lastMode) : lastMode;
        setMode(initial, false);
        binding.aiModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                setMode(checkedId == R.id.ai_mode_course ? AiExtras.MODE_COURSE : AiExtras.MODE_CHAT, true);
            }
        });
        binding.aiModeAction1.setOnClickListener(v -> {
            if (AiExtras.MODE_CHAT.equals(mode)) {
                chat.onHistoryClick();
            } else {
                course.toggleSearch();
            }
        });
        binding.aiModeAction2.setOnClickListener(v -> {
            if (AiExtras.MODE_CHAT.equals(mode)) {
                chat.onNewChatClick();
            } else {
                startActivity(new Intent(requireContext(), WatchHistoryActivity.class));
            }
        });
        getParentFragmentManager().setFragmentResultListener(MainTabHost.argsResultKey(TabRegistry.ASSISTANT),
                getViewLifecycleOwner(), (key, args) -> handleArgs(args));
        Bundle args = getArguments();
        if (savedInstanceState == null && args != null && !args.isEmpty()) {
            handleArgs(args);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MODE, mode);
    }

    /** 外部进入：K01 / K04 action、T01 推荐问题、K05“问 AI” */
    public void handleArgs(@NonNull Bundle args) {
        String m = args.getString(RouterPath.EXTRA_ASSISTANT_MODE, AiExtras.MODE_CHAT);
        setMode(m, false);
        if (AiExtras.MODE_CHAT.equals(m) && chat != null) {
            chat.handleEntry(args);
        }
    }

    private void setMode(@NonNull String m, boolean fromUser) {
        if (binding == null) {
            return;
        }
        boolean toCourse = AiExtras.MODE_COURSE.equals(m);
        boolean changed = !m.equals(mode);
        mode = m;
        lastMode = m;
        int checked = toCourse ? R.id.ai_mode_course : R.id.ai_mode_chat;
        if (binding.aiModeGroup.getCheckedButtonId() != checked) {
            binding.aiModeGroup.check(checked);
        }
        binding.aiModeAction1.setImageResource(toCourse ? com.zhimu.slmp.base.R.drawable.ic_search
                : com.zhimu.slmp.base.R.drawable.ic_history);
        binding.aiModeAction1.setContentDescription(getString(toCourse ? R.string.ai_action_search_course
                : R.string.ai_action_history));
        binding.aiModeAction2.setImageResource(toCourse ? com.zhimu.slmp.base.R.drawable.ic_history
                : com.zhimu.slmp.base.R.drawable.ic_add_comment);
        binding.aiModeAction2.setContentDescription(getString(toCourse ? R.string.ai_action_watch_history
                : R.string.ai_action_new_chat));
        FragmentManager fm = getChildFragmentManager();
        if (fm.isStateSaved() || chat == null || course == null) {
            return;
        }
        Fragment show = toCourse ? course : chat;
        Fragment hide = toCourse ? chat : course;
        if (!show.isHidden() && hide.isHidden()) {
            return;
        }
        if (changed && fromUser && show.getView() != null) {
            show.getView().setAlpha(0f);
            show.getView().animate().alpha(1f).setDuration(200).start();
        }
        fm.beginTransaction().hide(hide).show(show).commitNowAllowingStateLoss();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden && chat != null) {
            chat.onLeave();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
