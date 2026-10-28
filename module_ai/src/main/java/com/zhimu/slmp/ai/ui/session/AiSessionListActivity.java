package com.zhimu.slmp.ai.ui.session;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiActivitySessionListBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.ui.common.FooterAdapter;
import com.zhimu.slmp.ai.ui.common.PagingStates;
import com.zhimu.slmp.ai.ui.common.SingleViewAdapter;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.ReasonDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;

import kotlin.Unit;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * K02 历史会话：说明横幅、按日期分组（今天 / 昨天 / 近 7 天 / 更早）的本人会话、搜索（结果不分组）、扩展 FAB“新对话”。
 * 点会话以 RESULT_OK 带 session_id 返回 K01；“新对话”“去提问”带 new_session 返回。
 * 长按会话：“重命名”（1–40 字）、“删除”（二次确认）；删除过会话时返回 K01 带已删除的 ID。
 */
public class AiSessionListActivity extends BaseActivity {

    private static final String REQ_RENAME = "ai_session_rename";
    private static final String REQ_DELETE = "ai_session_delete";
    private static final String KEY_SESSION_ID = "session_id";
    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;

    private AiActivitySessionListBinding binding;
    private AiSessionListViewModel vm;
    private AiSessionPagingAdapter adapter;
    private boolean sawLoading;
    private OnBackPressedCallback exitSearch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AiActivitySessionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(AiInjector.factory(), AiSessionListViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.aiSessionTopBar.baseToolbar, getString(R.string.ai_session_title));
        binding.aiSessionTopBar.baseToolbar.inflateMenu(R.menu.ai_menu_search);
        TopAppBarHelper.tintMenu(binding.aiSessionTopBar.baseToolbar);
        binding.aiSessionTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.setSearching(true);
            return true;
        });

        NoticeView notice = new NoticeView(this);
        notice.setLevel(NoticeView.LEVEL_NORMAL);
        notice.setNotice(getString(R.string.ai_session_notice_title), getString(R.string.ai_session_notice_text));
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(4 * getResources().getDisplayMetrics().density);
        notice.setLayoutParams(lp);
        SingleViewAdapter header = new SingleViewAdapter(notice);

        adapter = new AiSessionPagingAdapter(vm::onSessionClick, this::showSessionMenu);
        binding.aiSessionList.setLayoutManager(new LinearLayoutManager(this));
        binding.aiSessionList.setAdapter(new ConcatAdapter(header,
                adapter.withLoadStateFooter(new FooterAdapter(adapter::retry))));
        vm.getSessions().observe(this, data -> adapter.submitData(getLifecycle(), data));
        adapter.addLoadStateListener(this::onLoadStates);
        binding.aiSessionRefresh.setOnRefreshListener(adapter::refresh);
        binding.aiSessionRefresh.setOnChildScrollUpCallback((p, c) -> binding.aiSessionList.canScrollVertically(-1));
        binding.aiSessionState.render(UiState.loading());
        binding.aiSessionState.setOnRetryListener(v -> adapter.retry());
        binding.aiSessionState.setOnEmptyActionListener(v -> vm.onNewChat());
        TopAppBarHelper.bindLiftOnScroll(binding.aiSessionTopBar.baseAppBar, binding.aiSessionList);

        binding.aiSessionFab.setOnClickListener(v -> vm.onNewChat());
        binding.aiSessionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 4 && binding.aiSessionFab.isExtended()) {
                    binding.aiSessionFab.shrink();
                } else if (dy < -4 && !binding.aiSessionFab.isExtended()) {
                    binding.aiSessionFab.extend();
                }
            }
        });

        binding.aiSessionSearchBack.setOnClickListener(v -> vm.setSearching(false));
        binding.aiSessionSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onKeywordChanged(s.toString());
            }
        });
        exitSearch = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                vm.setSearching(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, exitSearch);
        vm.getSearching().observe(this, on -> {
            boolean s = Boolean.TRUE.equals(on);
            exitSearch.setEnabled(s);
            binding.aiSessionTopBar.baseAppBar.setVisibility(s ? View.GONE : View.VISIBLE);
            binding.aiSessionSearchBar.setVisibility(s ? View.VISIBLE : View.GONE);
            header.setVisible(!s);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (s) {
                binding.aiSessionSearchInput.requestFocus();
                if (imm != null) {
                    imm.showSoftInput(binding.aiSessionSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            } else {
                binding.aiSessionSearchInput.setText("");
                hideKeyboard(binding.aiSessionSearchInput);
            }
        });
        vm.getOpenSession().observe(this, id -> {
            Intent data = new Intent().putExtra(RouterPath.EXTRA_SESSION_ID, id);
            setResult(RESULT_OK, data);
            finish();
        });
        vm.getNewChat().observe(this, v -> {
            setResult(RESULT_OK, new Intent().putExtra(RouterPath.EXTRA_NEW_SESSION, true));
            finish();
        });
        // 删除过会话：返回 K01 时带上已删除的 ID（K01 正在看的会话被删则回到新对话）
        vm.getDeletedIds().observe(this, ids -> setResult(RESULT_OK,
                new Intent().putExtra(AiExtras.RESULT_DELETED_SESSION_IDS, ids)));
        getSupportFragmentManager().setFragmentResultListener(REQ_RENAME, this, (key, result) -> {
            Bundle extras = ReasonDialogFragment.getExtras(result);
            if (ReasonDialogFragment.isConfirmed(result) && extras != null) {
                vm.rename(extras.getLong(KEY_SESSION_ID), ReasonDialogFragment.getReason(result));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (key, result) -> {
            Bundle extras = ConfirmDialogFragment.getExtras(result);
            if (ConfirmDialogFragment.isConfirmed(result) && extras != null) {
                vm.delete(extras.getLong(KEY_SESSION_ID));
            }
        });
    }

    /** 长按会话：“重命名”“删除” */
    private void showSessionMenu(@NonNull View anchor, @NonNull AiSessionItemVO s) {
        MoreMenuHelper.with(anchor)
                .add(MENU_RENAME, getString(R.string.ai_session_menu_rename), com.zhimu.slmp.base.R.drawable.ic_edit)
                .addDanger(MENU_DELETE, getString(R.string.ai_session_menu_delete),
                        com.zhimu.slmp.base.R.drawable.ic_delete)
                .setOnItemClickListener(id -> {
                    Bundle extras = new Bundle();
                    extras.putLong(KEY_SESSION_ID, s.getId());
                    if (id == MENU_RENAME) {
                        ReasonDialogFragment.builder(REQ_RENAME)
                                .title(getString(R.string.ai_session_rename_title))
                                .label(getString(R.string.ai_session_rename_label))
                                .helper(getString(R.string.ai_session_rename_helper))
                                .maxLength(AiText.MAX_SESSION_TITLE)
                                .prefill(s.getTitle())
                                .positive(getString(R.string.ai_session_rename_ok))
                                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                                .extras(extras)
                                .build()
                                .showSafely(getSupportFragmentManager());
                    } else if (id == MENU_DELETE) {
                        ConfirmDialogFragment.builder(REQ_DELETE)
                                .title(getString(R.string.ai_session_delete_title))
                                .message(getString(R.string.ai_session_delete_message))
                                .positive(getString(R.string.ai_session_menu_delete))
                                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                                .danger(true)
                                .extras(extras)
                                .build()
                                .showSafely(getSupportFragmentManager());
                    }
                })
                .show();
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        if (states.getRefresh() instanceof LoadState.Loading) {
            sawLoading = true;
        } else {
            binding.aiSessionRefresh.setRefreshing(false);
        }
        boolean searching = vm.hasKeyword();
        UiState s = PagingStates.of(states, adapter.getItemCount(), sawLoading,
                searching ? getString(R.string.ai_session_no_result) : getString(R.string.ai_session_empty_title),
                searching ? null : getString(R.string.ai_session_empty_message));
        if (s != null) {
            if (s.is(UiState.Status.EMPTY)) {
                binding.aiSessionState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_history,
                        searching ? getString(R.string.ai_session_no_result) : getString(R.string.ai_session_empty_title),
                        searching ? null : getString(R.string.ai_session_empty_message),
                        searching ? null : getString(R.string.ai_session_empty_action),
                        com.zhimu.slmp.base.R.drawable.ic_add_comment, StateLayout.EMPTY_ACTION_TONAL,
                        v -> vm.onNewChat());
            }
            binding.aiSessionState.render(s);
        }
        String err = PagingStates.refreshError(states, adapter.getItemCount());
        if (err != null) {
            showMessage(err);
        }
        return Unit.INSTANCE;
    }
}
