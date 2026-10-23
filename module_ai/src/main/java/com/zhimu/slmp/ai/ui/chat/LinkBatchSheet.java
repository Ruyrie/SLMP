package com.zhimu.slmp.ai.ui.chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiItemLinkBatchBinding;
import com.zhimu.slmp.ai.databinding.AiSheetLinkBatchBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.vo.LinkableBatchesVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;

import java.util.ArrayList;
import java.util.List;

/**
 * “关联批次”底部面板（K01，技术文档 2.6.5）：每次打开调 API-K05；说明与空状态由服务端按角色返回（员工只列负责栏舍）；
 * 按批次号倒序，当前项“当前”标签；末尾“不关联批次”。没有确认按钮，单选后 150 ms 关闭。复用父 Fragment 的 AiChatViewModel。
 */
public class LinkBatchSheet extends BaseBottomSheetDialogFragment {

    private static final String TAG = "ai_link_batch";
    private static final long CLOSE_DELAY_MS = 150;

    private AiChatViewModel vm;
    private AiSheetLinkBatchBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void show(@NonNull FragmentManager fm) {
        LinkBatchSheet s = new LinkBatchSheet();
        s.setArguments(baseArgs(null, null, null, null));
        s.showSafely(fm, TAG);
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.ai_link_title);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireParentFragment(), AiInjector.factory()).get(AiChatViewModel.class);
        binding = AiSheetLinkBatchBinding.inflate(inflater, container, false);
        binding.aiLinkReload.setOnClickListener(v -> vm.fetchLinkable());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getLinkableState().observe(getViewLifecycleOwner(), this::renderState);
        vm.getLinkableBatches().observe(getViewLifecycleOwner(), v -> renderState(vm.getLinkableState().getValue()));
        if (savedInstanceState == null) {
            vm.fetchLinkable();
        }
    }

    private void renderState(@Nullable UiState state) {
        if (binding == null || state == null) {
            return;
        }
        boolean loading = state.is(UiState.Status.LOADING);
        boolean error = state.is(UiState.Status.ERROR);
        binding.aiLinkLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.aiLinkError.setVisibility(error ? View.VISIBLE : View.GONE);
        LinkableBatchesVO data = vm.getLinkableBatches().getValue();
        boolean content = !loading && !error && data != null;
        binding.aiLinkList.removeAllViews();
        binding.aiLinkDivider.setVisibility(content ? View.VISIBLE : View.GONE);
        binding.aiLinkNone.getRoot().setVisibility(content ? View.VISIBLE : View.GONE);
        binding.aiLinkEmpty.setVisibility(View.GONE);
        if (!content) {
            return;
        }
        setDescription(data.getPanelHint());
        LinkedBatchVO current = vm.currentBatch();
        List<LinkedBatchVO> list = new ArrayList<>(data.getBatches());
        list.sort((a, b) -> AiText.nz(b.getBatchNo()).compareTo(AiText.nz(a.getBatchNo())));
        if (list.isEmpty() && data.getEmptyHint() != null) {
            binding.aiLinkEmpty.setText(data.getEmptyHint());
            binding.aiLinkEmpty.setVisibility(View.VISIBLE);
            binding.aiLinkDivider.setVisibility(View.GONE);
        }
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (LinkedBatchVO b : list) {
            AiItemLinkBatchBinding row = AiItemLinkBatchBinding.inflate(inf, binding.aiLinkList, false);
            boolean selected = current != null && current.getBatchId() == b.getBatchId();
            row.aiLinkTitle.setText(AiText.batchTitle(b));
            row.aiLinkSub.setText(AiText.batchSub(b));
            row.aiLinkRadio.setChecked(selected);
            row.aiLinkCurrent.setVisibility(selected ? View.VISIBLE : View.GONE);
            row.getRoot().setOnClickListener(v -> choose(row, b));
            binding.aiLinkList.addView(row.getRoot());
        }
        AiItemLinkBatchBinding none = binding.aiLinkNone;
        none.aiLinkTitle.setText(R.string.ai_link_none);
        none.aiLinkSub.setText(R.string.ai_link_none_sub);
        none.aiLinkRadio.setChecked(current == null);
        none.aiLinkCurrent.setVisibility(View.GONE);
        none.getRoot().setOnClickListener(v -> choose(none, null));
    }

    private void choose(@NonNull AiItemLinkBatchBinding row, @Nullable LinkedBatchVO batch) {
        row.aiLinkRadio.setChecked(true);
        handler.postDelayed(() -> {
            vm.selectBatch(batch);
            if (isAdded()) {
                dismissAllowingStateLoss();
            }
        }, CLOSE_DELAY_MS);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }
}
