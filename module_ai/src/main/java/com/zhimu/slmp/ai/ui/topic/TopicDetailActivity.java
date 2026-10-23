package com.zhimu.slmp.ai.ui.topic;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiActivityTopicDetailBinding;
import com.zhimu.slmp.ai.databinding.AiItemTopicDetailCourseBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * K07 专题详情：学习进度卡、课程列表（已学完 / 学到一半 / 未观看，整行可点进 K05）、底部“继续学习”从续学位置进入 K05。
 */
public class TopicDetailActivity extends BaseActivity {

    private AiActivityTopicDetailBinding binding;
    private TopicDetailViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AiActivityTopicDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(AiInjector.factory(), TopicDetailViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.aiTopicTopBar.baseToolbar, getString(R.string.ai_topic_title));
        binding.aiTopicTopBar.baseToolbar.inflateMenu(R.menu.ai_menu_share);
        TopAppBarHelper.tintMenu(binding.aiTopicTopBar.baseToolbar);
        binding.aiTopicTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.share();
            return true;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.aiTopicTopBar.baseAppBar, binding.aiTopicScroll);
        binding.aiTopicState.setOnRetryListener(v -> vm.retry());
        binding.aiTopicAction.setOnClickListener(v -> vm.continueLearning());

        vm.getUiState().observe(this, s -> {
            binding.aiTopicState.render(s);
            binding.aiTopicBottom.setVisibility(s != null && s.is(com.zhimu.slmp.base.mvvm.UiState.Status.CONTENT)
                    ? View.VISIBLE : View.GONE);
        });
        vm.getTopic().observe(this, this::render);
        vm.getProgressText().observe(this, t -> binding.aiTopicProgressText.setText(t));
        vm.getResumeText().observe(this, t -> binding.aiTopicResume.setText(t));
        vm.getActionText().observe(this, t -> binding.aiTopicAction.setText(t));
        vm.getOpenPlay().observe(this, args -> {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_COURSE_ID, args.getCourseId());
            b.putString(RouterPath.EXTRA_ENTRY_TYPE, args.getEntryType());
            Router.open(this, RouterPath.K05, b);
        });
        vm.getShareText().observe(this, text -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, getString(R.string.ai_share)));
        });
    }

    private void render(@Nullable TopicDetailVO t) {
        if (t == null) {
            return;
        }
        binding.aiTopicNameBig.setText(t.getName());
        binding.aiTopicCategory.setText(t.getCategoryName());
        binding.aiTopicMetaText.setText("共 " + t.getCourseCount() + " 节 · 约 "
                + Math.round(t.getTotalDurationSec() / 60.0) + " 分钟 · 平台知识库");
        binding.aiTopicIntro.setText(t.getIntro());
        int p = t.getCourseCount() == 0 ? 0 : Math.round(t.getLearnedCount() * 100f / t.getCourseCount());
        binding.aiTopicProgressBar.setProgressCompat(p, false);
        binding.aiTopicListTitle.setText(getString(R.string.ai_topic_course_list, t.getCourses().size()));
        binding.aiTopicCourses.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (CourseCardVO c : t.getCourses()) {
            AiItemTopicDetailCourseBinding row = AiItemTopicDetailCourseBinding.inflate(inf, binding.aiTopicCourses, false);
            String no = c.getSectionNo() == null ? "" : c.getSectionNo() + ". ";
            row.aiTdcTitle.setText(no + c.getTitle());
            String status = c.getLearnStatus();
            if (AiCodes.LEARN_COMPLETED.equals(status)) {
                row.aiTdcTag.setStatus(getString(R.string.ai_topic_tag_done), StatusTagView.NEUTRAL);
                row.aiTdcTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_check);
                row.aiTdcTime.setText(AiText.duration(c.getDurationSec()));
            } else if (AiCodes.LEARN_LEARNING.equals(status)) {
                row.aiTdcTag.setStatus(getString(R.string.ai_topic_tag_half), StatusTagView.PRIMARY);
                row.aiTdcTime.setText("看到 " + AiText.duration(c.getPositionSec()) + " / " + AiText.duration(c.getDurationSec()));
            } else {
                row.aiTdcTag.setStatus(getString(R.string.ai_topic_tag_none), StatusTagView.NEUTRAL);
                row.aiTdcTime.setText(AiText.duration(c.getDurationSec()));
            }
            boolean seen = status != null && !AiCodes.LEARN_NOT_STARTED.equals(status);
            row.aiTdcProgress.setVisibility(seen ? View.VISIBLE : View.GONE);
            if (seen) {
                row.aiTdcProgress.setProgress(AiCodes.LEARN_COMPLETED.equals(status) ? 100
                        : c.getProgressRatio() == null ? 0 : Math.round(c.getProgressRatio().floatValue() * 100));
            }
            Glide.with(row.aiTdcThumb).load(c.getCoverUrl()).centerCrop().into(row.aiTdcThumb);
            row.getRoot().setOnClickListener(v -> vm.openCourse(c));
            binding.aiTopicCourses.addView(row.getRoot());
        }
    }
}
