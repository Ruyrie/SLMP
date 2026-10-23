package com.zhimu.slmp.ai.ui.knowledge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiActivityKnowledgeBinding;
import com.zhimu.slmp.ai.databinding.AiItemRelatedBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.model.vo.EmbeddedVideoVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeDetailVO;
import com.zhimu.slmp.ai.model.vo.RelatedKnowledgeVO;
import com.zhimu.slmp.ai.ui.play.PlayerController;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.List;

/**
 * K03 知识原文：从 K01 角标进入时自动滚到被引用段落（引用块顶部位于顶部栏下方 16 dp）；内嵌教学视频原位播放
 * （控件同 K05，无画中画；点全屏进入 K05 横屏并接着播）；相关条目进入新的 K03（不带引用标注）；share 调起系统分享。
 */
public class KnowledgeDetailActivity extends BaseActivity {

    private AiActivityKnowledgeBinding binding;
    private KnowledgeDetailViewModel vm;
    private KnowledgeBlockAdapter blockAdapter;
    private PlayerController player;
    private boolean playerStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AiActivityKnowledgeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(AiInjector.factory(), KnowledgeDetailViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.aiKnowledgeTopBar.baseToolbar, getString(R.string.ai_knowledge_title));
        binding.aiKnowledgeTopBar.baseToolbar.inflateMenu(R.menu.ai_menu_share);
        TopAppBarHelper.tintMenu(binding.aiKnowledgeTopBar.baseToolbar);
        binding.aiKnowledgeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.share();
            return true;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.aiKnowledgeTopBar.baseAppBar, binding.aiKnowledgeScroll);
        binding.aiKnowledgeState.setOnRetryListener(v -> vm.retry());
        binding.aiKnowledgeState.setNoPermissionActionText(getString(R.string.ai_back));

        blockAdapter = new KnowledgeBlockAdapter();
        binding.aiKnowledgeBlocks.setLayoutManager(new LinearLayoutManager(this));
        binding.aiKnowledgeBlocks.setAdapter(blockAdapter);

        binding.aiKnowledgeVideo.post(() -> {
            ViewGroup.LayoutParams lp = binding.aiKnowledgeVideo.getLayoutParams();
            lp.height = Math.round(binding.aiKnowledgeVideo.getWidth() * 9f / 16f);
            binding.aiKnowledgeVideo.setLayoutParams(lp);
        });
        float radius = 16 * getResources().getDisplayMetrics().density;
        binding.aiKnowledgeVideo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        binding.aiKnowledgeVideo.setClipToOutline(true);
        binding.aiKnowledgePlay.setOnClickListener(v -> vm.playEmbedded());
        binding.aiKnowledgeCover.setOnClickListener(v -> vm.playEmbedded());

        player = new PlayerController(binding.aiKnowledgePlayer, binding.aiKnowledgeControls, new PlayerController.Callback() {
            @Override
            public void onTick(long positionMs, long bufferedMs, boolean playing, float speed) {
                vm.onPlaybackTick(positionMs, bufferedMs, playing, speed);
            }

            @Override
            public void onState(@NonNull PlayerState state) {
                vm.onPlayerStateChanged(state);
            }

            @Override
            public void onUserPause() {
                vm.onUserPause();
            }

            @Override
            public void onSeekFinished(long positionMs) {
                vm.onSeekFinished(positionMs);
            }

            @Override
            public void onControl(int viewId) {
                if (viewId == R.id.ai_ctrl_fullscreen) {
                    openFullscreen();
                } else if (viewId == R.id.ai_ctrl_message) {
                    player.retry();
                }
            }
        });
        player.setEmbedded();

        vm.getUiState().observe(this, s -> binding.aiKnowledgeState.render(s));
        vm.getDetail().observe(this, this::renderHeader);
        vm.getBlocks().observe(this, b -> renderBlocks());
        vm.getHighlightIndex().observe(this, i -> renderBlocks());
        vm.getUpdateNotice().observe(this, n -> {
            binding.aiKnowledgeNotice.setVisibility(n == null ? View.GONE : View.VISIBLE);
            if (n != null) {
                binding.aiKnowledgeNotice.setLevel(NoticeView.LEVEL_NORMAL);
                binding.aiKnowledgeNotice.setNotice(null, n);
            }
        });
        vm.getRelatedItems().observe(this, this::renderRelated);
        vm.getScrollToBlock().observe(this, this::scrollToBlock);
        vm.getPlayCommand().observe(this, cmd -> {
            binding.aiKnowledgeCoverBlock.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> binding.aiKnowledgeCoverBlock.setVisibility(View.GONE)).start();
            binding.aiKnowledgePlayer.setVisibility(View.VISIBLE);
            binding.aiKnowledgeControls.getRoot().setVisibility(View.VISIBLE);
            playerStarted = true;
            player.create();
            player.play(cmd);
        });
        vm.getEmbeddedState().observe(this, s -> {
            if (s == PlayerState.ERROR || s == PlayerState.NETWORK_LOST) {
                binding.aiKnowledgeControls.getRoot().setVisibility(View.VISIBLE);
                player.showMessage(getString(s == PlayerState.ERROR ? R.string.ai_play_failed
                        : R.string.ai_play_network_lost), com.zhimu.slmp.base.R.drawable.ic_refresh, null, null);
            }
        });
        vm.getShareText().observe(this, text -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, getString(R.string.ai_share)));
        });
        vm.getOpenKnowledge().observe(this, id -> {
            Bundle b = new Bundle();
            b.putLong(AiExtras.EXTRA_KNOWLEDGE_ID, id);
            Router.open(this, RouterPath.K03, b);
        });
    }

    private void renderHeader(@Nullable KnowledgeDetailVO d) {
        if (d == null) {
            return;
        }
        binding.aiKnowledgeName.setText(d.getTitle());
        binding.aiKnowledgeCategory.setText(d.getCategoryName());
        String source = d.getSourceTypeText() != null ? d.getSourceTypeText()
                : AiCodes.SOURCE_DISEASE.equals(d.getSourceType()) ? "常见病症"
                : AiCodes.SOURCE_DRUG_CATALOG.equals(d.getSourceType()) ? "兽药目录" : "平台知识库";
        String updated = d.getUpdatedAt() == null ? "" : " · " + DateFormatter.formatDate(d.getUpdatedAt(), false) + "更新";
        binding.aiKnowledgeMeta.setText(source + " · 版本 " + d.getVersionNo() + updated);
        EmbeddedVideoVO v = d.getEmbeddedVideo();
        binding.aiKnowledgeVideoBlock.setVisibility(v == null ? View.GONE : View.VISIBLE);
        if (v != null) {
            Glide.with(binding.aiKnowledgeCover).load(v.getCoverUrl()).centerCrop().into(binding.aiKnowledgeCover);
            binding.aiKnowledgeDuration.setText(AiText.duration(v.getDurationSec()));
            binding.aiKnowledgeVideoCaption.setText(getString(R.string.ai_knowledge_video_caption, AiText.nz(v.getTitle())));
        }
    }

    private void renderBlocks() {
        List<com.zhimu.slmp.ai.model.vo.KnowledgeBlockVO> list = vm.getBlocks().getValue();
        Integer hi = vm.getHighlightIndex().getValue();
        if (list != null) {
            blockAdapter.submit(list, hi == null ? -1 : hi);
        }
    }

    private void renderRelated(@Nullable List<RelatedKnowledgeVO> items) {
        binding.aiKnowledgeRelated.removeAllViews();
        boolean has = items != null && !items.isEmpty();
        binding.aiKnowledgeRelatedTitle.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) {
            return;
        }
        LayoutInflater inf = LayoutInflater.from(this);
        for (RelatedKnowledgeVO r : items) {
            AiItemRelatedBinding row = AiItemRelatedBinding.inflate(inf, binding.aiKnowledgeRelated, false);
            row.aiRelatedTitle.setText(r.getTitle());
            String source = r.getSourceTypeText() != null ? r.getSourceTypeText() : "版本 " + r.getVersionNo();
            row.aiRelatedSub.setText(AiText.nz(r.getCategoryName()) + " · " + source);
            row.aiRelatedIcon.setImageResource(AiCodes.ICON_ARTICLE.equals(r.getIconType())
                    ? com.zhimu.slmp.base.R.drawable.ic_description
                    : AiCodes.SOURCE_DRUG_CATALOG.equals(r.getSourceType()) && r.getTitle() != null
                    && r.getTitle().contains("禁用") ? com.zhimu.slmp.base.R.drawable.ic_block
                    : com.zhimu.slmp.base.R.drawable.ic_menu_book);
            row.getRoot().setOnClickListener(v -> vm.onRelatedClick(r));
            binding.aiKnowledgeRelated.addView(row.getRoot());
        }
    }

    /** 引用块顶部位于顶部栏下方 16 dp */
    private void scrollToBlock(int index) {
        binding.aiKnowledgeBlocks.post(() -> {
            View child = binding.aiKnowledgeBlocks.getLayoutManager() == null ? null
                    : binding.aiKnowledgeBlocks.getLayoutManager().findViewByPosition(index);
            if (child == null) {
                return;
            }
            int[] a = new int[2];
            int[] b = new int[2];
            child.getLocationInWindow(a);
            binding.aiKnowledgeScroll.getLocationInWindow(b);
            int offset = Math.round(16 * getResources().getDisplayMetrics().density);
            binding.aiKnowledgeScroll.smoothScrollTo(0,
                    binding.aiKnowledgeScroll.getScrollY() + a[1] - b[1] - offset);
        });
    }

    /** 内嵌视频点全屏：暂停原位播放，打开 K05 横屏从当前位置接着播 */
    private void openFullscreen() {
        if (vm.getEmbeddedPlay().getValue() == null) {
            return;
        }
        player.pause();
        Bundle b = new Bundle();
        b.putLong(AiExtras.EXTRA_COURSE_ID, vm.getEmbeddedPlay().getValue().getId());
        b.putString(AiExtras.EXTRA_ENTRY_TYPE, AiCodes.ENTRY_KNOWLEDGE);
        b.putBoolean(AiExtras.EXTRA_FULLSCREEN, true);
        b.putLong(AiExtras.EXTRA_START_MS, player.position());
        Router.open(this, RouterPath.K05, b);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playerStarted) {
            player.create();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (playerStarted) {
            player.pause();
            vm.onLeave();
            player.release();
        }
    }
}
