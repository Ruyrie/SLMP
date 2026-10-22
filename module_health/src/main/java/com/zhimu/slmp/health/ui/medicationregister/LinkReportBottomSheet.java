package com.zhimu.slmp.health.ui.medicationregister;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.health.model.vo.LinkableReportVO;

import java.util.List;

/**
 * D04“关联异常上报或诊断”底部面板：单选该批次或栏舍近 30 天的异常上报和诊断（API-I24），基于 OptionSheetDialogFragment，
 * 结果 value 为 report_id。
 */
public final class LinkReportBottomSheet {

    private LinkReportBottomSheet() {
    }

    public static void show(@NonNull FragmentManager fm, @NonNull String requestKey,
                            @Nullable List<LinkableReportVO> reports, @Nullable Long selectedReportId) {
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(requestKey)
                .title("关联异常上报或诊断")
                .description("近 30 天该批次或栏舍的异常上报和诊断")
                .selected(selectedReportId == null ? null : String.valueOf(selectedReportId));
        if (reports != null) {
            for (LinkableReportVO r : reports) {
                b.option(String.valueOf(r.getReportId()), r.getLabel() == null ? "" : r.getLabel(),
                        r.getDiagnosisId() == null ? "异常上报" : "AI 诊断");
            }
        }
        b.build().showSafely(fm);
    }
}
