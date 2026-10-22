package com.zhimu.slmp.herd.ui.mypens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.model.vo.MyPenBatchVO;
import com.zhimu.slmp.herd.model.vo.MyPenItemVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * L02 多批次栏舍点头部：选择要查看的批次（第 4 级底部面板，点选即关闭），结果 batch_id。
 */
public class PenBatchPickBottomSheet extends BaseBottomSheetDialogFragment {

    public static final String REQUEST_KEY = "herd_pick_batch";
    private static final String ARG_IDS = "ids";
    private static final String ARG_NOS = "nos";
    private static final String ARG_SUBS = "subs";
    private static final String ARG_WITHDRAWAL = "withdrawal";

    @NonNull
    public static PenBatchPickBottomSheet newInstance(@NonNull MyPenItemVO pen, @NonNull CharSequence title) {
        Bundle args = baseArgs(REQUEST_KEY, title, pen.getPenName(), null);
        List<MyPenBatchVO> batches = pen.getBatches();
        long[] ids = new long[batches.size()];
        ArrayList<String> nos = new ArrayList<>();
        ArrayList<String> subs = new ArrayList<>();
        boolean[] withdrawal = new boolean[batches.size()];
        String unit = HerdFormat.unit(pen.getUnitText());
        for (int i = 0; i < batches.size(); i++) {
            MyPenBatchVO b = batches.get(i);
            ids[i] = b.getBatchId();
            nos.add(b.getBatchNo());
            subs.add(b.getBreedTitle() + HerdFormat.DOT + b.getDayAge() + " 日龄" + HerdFormat.DOT
                    + HerdFormat.qty(b.getQtyInPen(), unit));
            withdrawal[i] = b.isInWithdrawal();
        }
        args.putLongArray(ARG_IDS, ids);
        args.putStringArrayList(ARG_NOS, nos);
        args.putStringArrayList(ARG_SUBS, subs);
        args.putBooleanArray(ARG_WITHDRAWAL, withdrawal);
        PenBatchPickBottomSheet f = new PenBatchPickBottomSheet();
        f.setArguments(args);
        return f;
    }

    public static long getBatchId(@NonNull Bundle result) {
        return result.getLong(RouterPath.EXTRA_BATCH_ID);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        long[] ids = args.getLongArray(ARG_IDS);
        ArrayList<String> nos = args.getStringArrayList(ARG_NOS);
        ArrayList<String> subs = args.getStringArrayList(ARG_SUBS);
        boolean[] withdrawal = args.getBooleanArray(ARG_WITHDRAWAL);
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin);
        list.setPadding(pad, 0, pad, pad);
        int count = ids == null ? 0 : ids.length;
        for (int i = 0; i < count; i++) {
            ListRowView row = new ListRowView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_list_gap);
            row.setLayoutParams(lp);
            row.setGroupPosition(GroupPosition.of(i, count));
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_folder_open, ListRowView.TONE_SECONDARY);
            row.setTitle(nos == null ? "" : nos.get(i));
            row.setSubtitle(subs == null ? "" : subs.get(i));
            if (withdrawal != null && withdrawal[i]) {
                row.setStatusTag("休药中", StatusTagView.WARNING, 0);
            }
            row.setShowChevron(true);
            final long id = ids[i];
            ClickThrottle.setOnClickListener(row, v -> {
                Bundle result = new Bundle();
                result.putLong(RouterPath.EXTRA_BATCH_ID, id);
                setResultAndDismiss(result);
            });
            list.addView(row);
        }
        return list;
    }
}
