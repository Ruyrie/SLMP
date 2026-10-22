package com.zhimu.slmp.today.ui.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.ReasonDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityTodoDetailBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.RelatedBatchVO;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.util.InfoRow;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.TodayNavigator;
import com.zhimu.slmp.today.util.TodoTypeStyle;
import com.zhimu.slmp.today.util.Texts;

import java.util.List;

/**
 * T04 待办详情：作业类 · 养殖户底部“标记完成 / 去登记”、右上角菜单“忽略”；预警类与经营类 · 养殖户“忽略 / 去登记”；
 * 员工只有占满整行的“去登记”；已完成、已忽略不显示底部操作栏。忽略成功后 setResult 并关闭，上一页提示“已忽略这条待办”。
 */
public class TodoDetailActivity extends BaseActivity {

    /** 结果：已忽略（上一页提示并刷新） */
    public static final String RESULT_IGNORED = "today_ignored";

    private static final String REQ_MARK_DONE = "today_mark_done";
    private static final String REQ_IGNORE = "today_ignore";

    private TodoDetailViewModel vm;
    private TodayActivityTodoDetailBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), TodoDetailViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.today_activity_todo_detail);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.today_detail_title));
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.scroll);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.today_menu_more) {
                showMoreMenu();
                return true;
            }
            return false;
        });
        binding.stateLayout.setOnRetryListener(v -> vm.retry());
        binding.actionBar.setOnPrimaryClickListener(v -> vm.onRegisterClick());
        binding.actionBar.setOnSecondaryClickListener(v -> {
            if (Boolean.TRUE.equals(vm.getCanComplete().getValue())) {
                vm.onCompleteClick();
            } else {
                vm.onIgnoreClick();
            }
        });
        binding.relatedBatch.setOnClickListener(v -> vm.onRelatedBatchClick());

        getSupportFragmentManager().setFragmentResultListener(REQ_MARK_DONE, this, (key, result) -> {
            if (MarkDoneDialogFragment.isConfirmed(result)) {
                vm.confirmComplete();
            } else if (MarkDoneDialogFragment.isGoRegister(result)) {
                vm.onDialogDismissed();
                vm.onRegisterClick();
            } else {
                vm.onDialogDismissed();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_IGNORE, this, (key, result) -> {
            if (ReasonDialogFragment.isConfirmed(result)) {
                vm.confirmIgnore(ReasonDialogFragment.getReason(result));
            } else {
                vm.onDialogDismissed();
            }
        });

        vm.getDetail().observe(this, this::render);
        vm.getInfoRows().observe(this, this::renderRows);
        vm.getCanComplete().observe(this, v -> renderActions());
        vm.getCanIgnore().observe(this, v -> renderActions());
        vm.getCanRegister().observe(this, v -> renderActions());
        vm.getIgnoreInMenu().observe(this, inMenu -> {
            if (Boolean.TRUE.equals(inMenu)) {
                TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.today_menu_more);
            } else {
                binding.topBar.baseToolbar.getMenu().clear();
            }
            renderActions();
        });
        vm.getSubmitting().observe(this, this::renderSubmitting);
        vm.getOpenMarkDone().observe(this, this::showMarkDone);
        vm.getOpenIgnore().observe(this, v -> showIgnore());
        vm.getFinishWithResult().observe(this, this::finishWithResult);
        vm.getNavEvent().observe(this, this::open);
    }

    // ===================== 渲染 =====================

    private void render(@Nullable TodoDetailVO vo) {
        if (vo == null) {
            return;
        }
        String status = vo.getDisplayStatus();
        int type = TodoTypeStyle.STATUS_OVERDUE.equals(status) ? StatusTagView.ERROR
                : TodoTypeStyle.isClosed(vo) ? StatusTagView.NEUTRAL : StatusTagView.PRIMARY;
        binding.statusTag.setStatus(vo.getDisplayStatusText(), type);
        binding.statusTag.setTagIcon(TodoTypeStyle.STATUS_OVERDUE.equals(status) ? com.zhimu.slmp.base.R.drawable.ic_schedule
                : TodoTypeStyle.STATUS_DONE.equals(status) ? com.zhimu.slmp.base.R.drawable.ic_check : 0);

        RelatedBatchVO batch = vo.getRelatedBatch();
        if (batch != null) {
            ListRowView row = binding.relatedBatch;
            boolean withdrawal = !Texts.isEmpty(batch.getTagText());
            row.setLeadingIcon(withdrawal ? com.zhimu.slmp.base.R.drawable.ic_medication
                    : com.zhimu.slmp.base.R.drawable.ic_fence, withdrawal ? ListRowView.TONE_WARNING : ListRowView.TONE_SECONDARY);
            row.setTitle(Texts.join(" · ", java.util.Arrays.asList(batch.getBatchNo(), batch.getPenName())));
            row.setSubtitle(batch.getDesc());
            row.setStatusTag(batch.getTagText(), StatusTagView.WARNING, 0);
            row.setShowChevron(!withdrawal);
        }
        boolean showNotice = !TodoTypeStyle.isClosed(vo) && !Texts.isEmpty(vo.getNoticeText());
        binding.notice.setVisibility(showNotice ? View.VISIBLE : View.GONE);
        if (showNotice) {
            binding.notice.setLevel(NoticeView.LEVEL_NORMAL);
            binding.notice.setText(vo.getNoticeText());
        }
        renderActions();
    }

    private void renderRows(@Nullable List<InfoRow> rows) {
        binding.infoRows.removeAllViews();
        if (rows == null) {
            return;
        }
        for (InfoRow r : rows) {
            KeyValueRowView kv = new KeyValueRowView(this);
            kv.setKey(r.getLabel());
            kv.setValue(r.getValue());
            kv.setNote(r.getHint());
            binding.infoRows.addView(kv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        binding.infoRows.updatePositions();
    }

    /** 底部按钮按类别与角色显示（UI 设计文档 T04“底部按钮按类别和角色显示”） */
    private void renderActions() {
        TodoDetailVO vo = vm.getDetail().getValue();
        boolean register = Boolean.TRUE.equals(vm.getCanRegister().getValue());
        boolean complete = Boolean.TRUE.equals(vm.getCanComplete().getValue());
        boolean ignore = Boolean.TRUE.equals(vm.getCanIgnore().getValue())
                && !Boolean.TRUE.equals(vm.getIgnoreInMenu().getValue());
        if (vo == null || (!register && !complete && !ignore)) {
            binding.actionBar.setVisibility(View.GONE);
            return;
        }
        binding.actionBar.setVisibility(View.VISIBLE);
        int registerIcon = TodoTypeStyle.CATEGORY_WORK.equals(vo.getCategory()) && complete
                ? TodoTypeStyle.iconOf(vo.getTodoType()) : 0;
        binding.actionBar.setPrimary(register ? getString(R.string.today_detail_register) : null, registerIcon);
        if (complete) {
            binding.actionBar.setSecondary(getString(R.string.today_detail_mark_done), com.zhimu.slmp.base.R.drawable.ic_done);
        } else if (ignore) {
            binding.actionBar.setSecondary(getString(R.string.today_detail_ignore), 0);
        } else {
            binding.actionBar.setSecondary(null, 0);
        }
    }

    private void renderSubmitting(@Nullable Boolean submitting) {
        boolean s = Boolean.TRUE.equals(submitting);
        if (binding.actionBar.getSecondaryButton().getVisibility() == View.VISIBLE) {
            ButtonLoading.setLoading(binding.actionBar.getSecondaryButton(), s);
        }
        binding.actionBar.getPrimaryButton().setEnabled(!s);
    }

    // ===================== 弹窗 =====================

    private void showMoreMenu() {
        View anchor = binding.topBar.baseToolbar.findViewById(R.id.today_menu_more);
        if (anchor == null) {
            return;
        }
        MoreMenuHelper.with(anchor)
                .add(R.id.today_action_ignore, getString(R.string.today_detail_ignore), com.zhimu.slmp.base.R.drawable.ic_block)
                .setOnItemClickListener(id -> vm.onIgnoreClick())
                .show();
    }

    private void showMarkDone(@Nullable String tip) {
        MarkDoneDialogFragment.newInstance(REQ_MARK_DONE, getString(R.string.today_detail_mark_done_title), tip,
                        getString(R.string.today_detail_register), getString(R.string.today_detail_mark_done),
                        com.zhimu.slmp.base.R.drawable.ic_task_alt)
                .showSafely(getSupportFragmentManager());
    }

    private void showIgnore() {
        String error = vm.getReasonError().getValue();
        ReasonDialogFragment.builder(REQ_IGNORE)
                .title(getString(R.string.today_detail_ignore_title))
                .message(getString(R.string.today_detail_ignore_message))
                .label(getString(R.string.today_detail_ignore_label))
                .helper(error)
                .maxLength(TodoDetailViewModel.REASON_MAX)
                .prefill(vm.getPendingReason())
                .positive(getString(R.string.today_detail_ignore))
                .negative(getString(R.string.today_detail_cancel))
                .build()
                .showSafely(getSupportFragmentManager());
    }

    // ===================== 结果与跳转 =====================

    private void finishWithResult(@Nullable TodoChange change) {
        Intent data = new Intent().putExtra(RESULT_IGNORED, true);
        if (change != null && change.getTodoId() != null) {
            data.putExtra(RouterPath.EXTRA_TODO_ID, change.getTodoId().longValue());
        }
        setResult(RESULT_OK, data);
        finish();
    }

    private void open(@Nullable NavTarget target) {
        if (target == null) {
            return;
        }
        TodayNavigator.open(this, target);
        if ("T08".equals(target.getRoute())) {
            finish();
        }
    }
}
