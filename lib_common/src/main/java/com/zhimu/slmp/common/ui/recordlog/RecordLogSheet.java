package com.zhimu.slmp.common.ui.recordlog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.databinding.CommonRecordLogSheetBinding;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

/**
 * U14 查看修改记录（API-U14 GET /api/v1/record-logs?biz_type=&biz_id=）：各模块“查看修改记录”共用的只读底部面板。
 * 标题默认“修改记录”；按时间倒序，每项主文字“10月12日 14:20 · 刘芳”，辅助文字为操作、每个字段一行“字段：原值 → 新值”、
 * 原因与修改申请编号（{@link RecordLogTexts}）；加载中为骨架屏，空为“还没有修改记录”，失败可“重新加载”。
 * 面板打开即展开，最高占屏幕 70%，超出时在面板内滚动；数据由面板自己的 {@link RecordLogViewModel} 加载，页面只需：
 * <pre>
 * RecordLogSheet.show(getSupportFragmentManager(), "PEN", penId, null);
 * </pre>
 */
public class RecordLogSheet extends BaseBottomSheetDialogFragment {

    private static final String TAG = "common_record_log";
    private static final String ARG_EMPTY_TITLE = "common_record_log_empty_title";
    private static final float MAX_HEIGHT_RATIO = 0.7f;

    private CommonRecordLogSheetBinding binding;

    /**
     * 打开修改记录面板。
     *
     * @param bizType 记录类型（API-U14 的 biz_type，如 PEN、FEED_PLAN、IMMUNE_RECORD）
     * @param bizId   记录 ID；不大于 0（记录还没有保存）时直接显示空状态
     * @param title   面板标题，为空时“修改记录”
     * @return 是否已显示（同一时刻最多一个弹窗）
     */
    public static boolean show(@NonNull FragmentManager fm, @NonNull String bizType, long bizId,
                               @Nullable CharSequence title) {
        return show(fm, bizType, bizId, title, null);
    }

    /**
     * 同上，另指定空状态文字（如 E04“这条记录还没有修改过”），为空时“还没有修改记录”。
     */
    public static boolean show(@NonNull FragmentManager fm, @NonNull String bizType, long bizId,
                               @Nullable CharSequence title, @Nullable CharSequence emptyTitle) {
        RecordLogSheet sheet = new RecordLogSheet();
        Bundle args = baseArgs(null, title, null, null);
        args.putString(RouterPath.EXTRA_BIZ_TYPE, bizType);
        args.putLong(RouterPath.EXTRA_BIZ_ID, bizId);
        args.putCharSequence(ARG_EMPTY_TITLE, emptyTitle);
        sheet.setArguments(args);
        return sheet.showSafely(fm, TAG);
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        CharSequence title = super.getSheetTitle();
        return title == null || title.length() == 0 ? getString(R.string.common_record_log_title) : title;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = CommonRecordLogSheetBinding.inflate(inflater, container, false);
        CharSequence emptyTitle = requireArguments().getCharSequence(ARG_EMPTY_TITLE);
        if (emptyTitle != null && emptyTitle.length() > 0) {
            binding.commonRecordLogState.setEmptyTitle(emptyTitle);
        }
        // 面板中“无权查看”的按钮只关闭面板，不退出所在页面
        binding.commonRecordLogState.setOnNoPermissionActionListener(v -> dismiss());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecordLogViewModel vm = new ViewModelProvider(this, factory()).get(RecordLogViewModel.class);
        binding.commonRecordLogState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(getViewLifecycleOwner(), binding.commonRecordLogState::render);
        vm.getLogs().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onStart() {
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * MAX_HEIGHT_RATIO);
            ((BottomSheetDialog) dialog).getBehavior().setMaxHeight(max);
        }
        super.onStart();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(@Nullable List<RecordLogVO> logs) {
        if (binding == null) {
            return;
        }
        binding.commonRecordLogList.removeAllViews();
        if (logs == null) {
            return;
        }
        for (RecordLogVO log : logs) {
            ListRowView row = new ListRowView(requireContext());
            row.setTitle(RecordLogTexts.title(log));
            row.setSubtitleMaxLines(Integer.MAX_VALUE);
            row.setSubtitle(RecordLogTexts.content(log));
            binding.commonRecordLogList.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        binding.commonRecordLogList.updatePositions();
    }

    /** 面板自己的 ViewModel 工厂：经 ViewModelFactory 创建，arguments 交给 SavedStateHandle */
    @NonNull
    private ViewModelProvider.Factory factory() {
        BaseDataRepository repository = CoreInjector.get(requireContext()).baseDataRepository();
        Provider<ViewModel> creator = () -> new RecordLogViewModel(repository);
        Map<Class<? extends ViewModel>, Provider<ViewModel>> creators =
                Collections.singletonMap(RecordLogViewModel.class, creator);
        return new ViewModelFactory(creators);
    }
}
