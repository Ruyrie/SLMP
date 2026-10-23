package com.zhimu.slmp.ai.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiActivityWatchHistoryBinding;
import com.zhimu.slmp.ai.databinding.AiItemTopicCourseBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.vo.WatchHistoryVO;
import com.zhimu.slmp.ai.ui.common.FooterAdapter;
import com.zhimu.slmp.ai.ui.common.PagingStates;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Objects;
import java.util.function.Consumer;

import kotlin.Unit;

/**
 * K04 观看记录子页：96 × 54 缩略图 + 标题 + “看到 2:43”或“已学完”；点击进入 K05。
 */
public class WatchHistoryActivity extends BaseActivity {

    private AiActivityWatchHistoryBinding binding;
    private WatchHistoryViewModel vm;
    private Adapter adapter;
    private boolean sawLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AiActivityWatchHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(AiInjector.factory(), WatchHistoryViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.aiHistoryTopBar.baseToolbar, getString(R.string.ai_history_title));
        TopAppBarHelper.bindLiftOnScroll(binding.aiHistoryTopBar.baseAppBar, binding.aiHistoryList);
        adapter = new Adapter(vm::onItemClick);
        binding.aiHistoryList.setLayoutManager(new LinearLayoutManager(this));
        binding.aiHistoryList.setAdapter(adapter.withLoadStateFooter(new FooterAdapter(adapter::retry)));
        vm.getPagingData().observe(this, data -> adapter.submitData(getLifecycle(), data));
        adapter.addLoadStateListener(this::onLoadStates);
        binding.aiHistoryRefresh.setOnRefreshListener(adapter::refresh);
        binding.aiHistoryRefresh.setOnChildScrollUpCallback((p, c) -> binding.aiHistoryList.canScrollVertically(-1));
        binding.aiHistoryState.render(UiState.loading());
        binding.aiHistoryState.setOnRetryListener(v -> adapter.retry());
        vm.getOpenPlay().observe(this, args -> {
            Bundle b = new Bundle();
            b.putLong(AiExtras.EXTRA_COURSE_ID, args.getCourseId());
            b.putString(AiExtras.EXTRA_ENTRY_TYPE, args.getEntryType());
            Router.open(this, RouterPath.K05, b);
        });
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        if (states.getRefresh() instanceof LoadState.Loading) {
            sawLoading = true;
        } else {
            binding.aiHistoryRefresh.setRefreshing(false);
        }
        UiState s = PagingStates.of(states, adapter.getItemCount(), sawLoading, null, null);
        if (s != null) {
            binding.aiHistoryState.render(s);
        }
        String err = PagingStates.refreshError(states, adapter.getItemCount());
        if (err != null) {
            showMessage(err);
        }
        return Unit.INSTANCE;
    }

    static final class Adapter extends PagingDataAdapter<WatchHistoryVO, Adapter.Holder> {
        private final Consumer<WatchHistoryVO> onClick;

        Adapter(@NonNull Consumer<WatchHistoryVO> onClick) {
            super(new DiffUtil.ItemCallback<WatchHistoryVO>() {
                @Override
                public boolean areItemsTheSame(@NonNull WatchHistoryVO a, @NonNull WatchHistoryVO b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull WatchHistoryVO a, @NonNull WatchHistoryVO b) {
                    return Objects.equals(a.getLastWatchedAt(), b.getLastWatchedAt())
                            && Objects.equals(a.getLearnStatus(), b.getLearnStatus());
                }
            });
            this.onClick = onClick;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AiItemTopicCourseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WatchHistoryVO w = getItem(position);
            if (w == null) {
                return;
            }
            h.b.aiTcTitle.setText(w.getTitle());
            h.b.aiTcSub.setText(AiText.learnState(w.getLearnStatus(), w.getPositionSec()));
            h.b.aiTcProgress.setVisibility(android.view.View.GONE);
            Glide.with(h.b.aiTcThumb).load(w.getCoverUrl()).centerCrop().into(h.b.aiTcThumb);
            h.b.getRoot().setOnClickListener(v -> onClick.accept(w));
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final AiItemTopicCourseBinding b;

            Holder(@NonNull AiItemTopicCourseBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
