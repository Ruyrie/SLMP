package com.zhimu.slmp.breed.ui.archive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedActivityChangeLogBinding;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.breed.ui.common.BreedUi;
import com.zhimu.slmp.breed.ui.common.RowBinder;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 查看修改记录（本组件内页面，显式 Intent 打开）：参数 EXTRA_BIZ_TYPE、EXTRA_BIZ_ID，标题“{耳号} 修改记录”。
 */
public class ChangeLogActivity extends BaseActivity {

    private static final String EXTRA_SUBJECT = "breed_log_subject";

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String bizType, long bizId,
                                @Nullable String subject) {
        return new Intent(context, ChangeLogActivity.class)
                .putExtra(RouterPath.EXTRA_BIZ_TYPE, bizType)
                .putExtra(RouterPath.EXTRA_BIZ_ID, bizId)
                .putExtra(EXTRA_SUBJECT, subject);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BreedActivityChangeLogBinding binding = BreedActivityChangeLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ChangeLogViewModel vm = obtainViewModel(BreedInjector.factory(), ChangeLogViewModel.class);
        observeBase(vm);
        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        TopAppBarHelper.setupBack(binding.breedTopBar.baseToolbar, subject == null || subject.isEmpty()
                ? getString(R.string.breed_log_title) : subject + " " + getString(R.string.breed_log_title));
        binding.breedList.setLayoutManager(new LinearLayoutManager(this));
        binding.breedList.addItemDecoration(new GroupedListDecoration(this));
        LogAdapter adapter = new LogAdapter();
        binding.breedList.setAdapter(adapter);
        binding.breedState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.breedState::render);
        vm.getLogs().observe(this, adapter::submitList);
    }

    static final class LogAdapter extends ListAdapter<RecordLogVO, LogAdapter.Holder> {

        LogAdapter() {
            super(new DiffUtil.ItemCallback<RecordLogVO>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecordLogVO a, @NonNull RecordLogVO b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecordLogVO a, @NonNull RecordLogVO b) {
                    return a.getId() == b.getId();
                }
            });
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(BreedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            RecordLogVO log = getItem(position);
            BreedItemRowBinding b = holder.binding;
            RowBinder.reset(b, position, getItemCount());
            RowBinder.icon(b, com.zhimu.slmp.base.R.drawable.ic_history, BreedUi.TONE_NEUTRAL);
            String who = log.getOperator() == null ? "" : BreedTextFormatter.nz(log.getOperator().getName());
            StringBuilder changes = new StringBuilder();
            for (RecordLogVO.Change c : log.getChanges()) {
                if (changes.length() > 0) {
                    changes.append('\n');
                }
                changes.append(BreedTextFormatter.nz(c.getFieldName())).append("：")
                        .append(BreedTextFormatter.nz(c.getBefore())).append(" → ").append(BreedTextFormatter.nz(c.getAfter()));
            }
            String extra = log.getChangeRequestNo() != null ? "修改申请 " + log.getChangeRequestNo()
                    : log.getReason();
            RowBinder.text(b, DateFormatter.formatDateTime(log.getCreatedAt()) + BreedTextFormatter.DOT + who,
                    changes.length() == 0 ? log.getOpActionText() : changes.toString(), extra);
            b.breedRowSubtitle.setMaxLines(6);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final BreedItemRowBinding binding;

            Holder(@NonNull BreedItemRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
