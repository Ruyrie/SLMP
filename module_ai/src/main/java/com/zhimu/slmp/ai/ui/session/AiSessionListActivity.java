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
 */
public class AiSessionListActivity extends BaseActivity {

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

        adapter = new AiSessionPagingAdapter(vm::onSessionClick);
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
