package com.zhimu.slmp.today.ui.notice;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityNoticeDetailBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.NoticePayload;

import java.util.Collections;

/**
 * T07 通知详情：重要通知（campaign 图标 + “重要”）、普通通知只显示“平台通知”，指定养殖场的通知另加养殖场标签；
 * 配图 16:9 裁切，点击进入全屏预览；已撤回显示“该通知已撤回”与“返回消息中心”；菜单“删除消息”后返回 T06（结果带消息 ID）。
 */
public class NoticeDetailActivity extends BaseActivity {

    /** 结果：已删除的消息 ID（T06 显示“撤销”） */
    public static final String RESULT_DELETED_ID = "today_deleted_message_id";
    private static final String REQ_REJECT_TRANSFER = "today_reject_transfer";

    private NoticeDetailViewModel vm;
    private TodayActivityNoticeDetailBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), NoticeDetailViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.today_activity_notice_detail);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.today_notice_title));
        TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.today_menu_more);
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.scroll);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.today_menu_more) {
                View anchor = binding.topBar.baseToolbar.findViewById(R.id.today_menu_more);
                if (anchor != null) {
                    MoreMenuHelper.with(anchor)
                            .addDanger(R.id.today_action_delete, getString(R.string.today_message_delete),
                                    com.zhimu.slmp.base.R.drawable.ic_delete)
                            .setOnItemClickListener(id -> vm.delete())
                            .show();
                }
                return true;
            }
            return false;
        });
        binding.stateLayout.setOnRetryListener(v -> vm.retry());
        binding.revoked.icon.setImageResource(com.zhimu.slmp.base.R.drawable.ic_undo);
        binding.revoked.title.setText(R.string.today_notice_revoked_title);
        binding.revoked.desc.setText(R.string.today_notice_revoked_desc);
        binding.revoked.action.setOnClickListener(v -> finish());
        binding.actionBar.setOnPrimaryClickListener(v -> vm.acceptTransfer());
        binding.actionBar.setOnSecondaryClickListener(v -> vm.onRejectTransferClick());
        binding.image.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = (r - l) * 9 / 16;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (h > 0 && lp.height != h) {
                lp.height = h;
                v.post(() -> v.setLayoutParams(lp));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_REJECT_TRANSFER, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.rejectTransfer();
            }
        });

        vm.getDetail().observe(this, this::renderTags);
        vm.getNotice().observe(this, notice -> {
            String content = notice == null ? null : notice.getContent();
            // 服务端正文为仅含段落、换行、加粗和列表的受限 HTML；兼容历史纯文本换行。
            binding.content.setText(content == null ? "" : HtmlCompat.fromHtml(
                    content.replace("\r\n", "\n").replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT));
        });
        vm.getRevokedLine().observe(this, line -> {
            binding.revoked.getRoot().setVisibility(line == null ? View.GONE : View.VISIBLE);
            binding.revoked.meta.setText(line);
            binding.revoked.meta.setVisibility(line == null ? View.GONE : View.VISIBLE);
        });
        vm.getOpenPreview().observe(this, this::openPreview);
        vm.getFinishWithDeleted().observe(this, id -> {
            setResult(RESULT_OK, new Intent().putExtra(RESULT_DELETED_ID, id == null ? 0L : id));
            finish();
        });
        vm.getConfirmRejectTransfer().observe(this, v -> ConfirmDialogFragment.builder(REQ_REJECT_TRANSFER)
                .title(getString(R.string.today_notice_transfer_reject_title))
                .message(getString(R.string.today_notice_transfer_reject_message))
                .positive(getString(R.string.today_notice_reject))
                .negative(getString(R.string.today_detail_cancel))
                .danger(true)
                .build()
                .showSafely(getSupportFragmentManager()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.onVisible();
    }

    @Override
    protected void onPause() {
        vm.onInvisible();
        super.onPause();
    }

    /** 标签行：“重要”（primary + campaign）或无、“平台通知”（中性）、指定养殖场的养殖场标签 */
    private void renderTags(@Nullable MessageDetailVO vo) {
        binding.tags.removeAllViews();
        if (vo == null) {
            return;
        }
        NoticePayload n = vo.getNotice();
        if (n != null) {
            if ("IMPORTANT".equals(n.getLevel())) {
                addTag(getString(R.string.today_notice_important), StatusTagView.PRIMARY,
                        com.zhimu.slmp.base.R.drawable.ic_campaign);
            }
            addTag(getString(R.string.today_notice_platform), StatusTagView.NEUTRAL, 0);
            if (n.getTargetFarmNames() != null) {
                for (String farm : n.getTargetFarmNames()) {
                    addTag(farm, StatusTagView.FARM, 0);
                }
            }
        } else if (vo.getCategoryText() != null) {
            addTag(vo.getCategoryText(), StatusTagView.NEUTRAL, 0);
            if (vo.getFarm() != null && vo.getFarm().getName() != null) {
                addTag(vo.getFarm().getName(), StatusTagView.FARM, 0);
            }
        }
        binding.tags.setVisibility(binding.tags.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void addTag(CharSequence text, int type, int icon) {
        StatusTagView tag = new StatusTagView(this);
        tag.setStatus(text, type);
        tag.setTagIcon(icon);
        binding.tags.addView(tag);
    }

    /** 使用媒体组件的完整文件参数，支持全屏预览及临时地址过期重签名。 */
    private void openPreview(@Nullable FileVO file) {
        if (file == null) {
            return;
        }
        MediaViewer.openFiles(this, Collections.singletonList(file), 0, getString(R.string.today_notice_title));
    }
}
