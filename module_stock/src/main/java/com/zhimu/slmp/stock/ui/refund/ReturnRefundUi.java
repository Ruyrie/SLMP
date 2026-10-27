package com.zhimu.slmp.stock.ui.refund;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.FragmentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.lifecycle.LiveData;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockViewRefundBinding;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.util.StockCalc;
import java.util.ArrayList;
import java.util.List;

/** 通用详情与 M11 只读详情使用相同到账信息及操作，权限完全来自服务端 actions。 */
public final class ReturnRefundUi {
    private ReturnRefundUi() {}

    public static void attach(FragmentActivity activity, StockViewRefundBinding view, ReturnRefundViewModel vm,
                              LiveData<StockOutboundVO> source, Runnable refresh) {
        source.observe(activity, vm::bind);
        vm.getRecord().observe(activity, value -> render(view, value));
        view.stockRefundAction.setOnClickListener(unused -> {
            if (vm.open(ReturnRefundViewModel.isRefunded(vm.getRecord().getValue())))
                ReturnRefundBottomSheet.show(activity.getSupportFragmentManager());
        });
        ActivityResultLauncher<Intent> financeLauncher = Router.registerForResult(activity, result -> refresh.run());
        view.stockRefundFinance.setOnClickListener(unused -> {
            StockOutboundVO value = vm.getRecord().getValue();
            if (value != null && value.financeRecordId != null)
                Router.openForResult(activity, financeLauncher, RouterPath.E04, financeArgs(value));
        });
        vm.getSaved().observe(activity, unused -> {
            ReturnRefundBottomSheet.close(activity.getSupportFragmentManager());
            activity.setResult(Activity.RESULT_OK);
            refresh.run();
        });
        vm.getRefreshRecord().observe(activity, unused -> refresh.run());
    }

    public static Bundle financeArgs(StockOutboundVO value) {
        Bundle args = new Bundle();
        if (value.financeRecordId != null) args.putLong(RouterPath.EXTRA_HIGHLIGHT_ID, value.financeRecordId);
        args.putString(RouterPath.EXTRA_RECORD_FILTER_TYPE, "INCOME");
        if (value.refundDate != null && value.refundDate.length() >= 7) args.putString(RouterPath.EXTRA_MONTH, value.refundDate.substring(0, 7));
        return args;
    }

    private static void render(StockViewRefundBinding view, StockOutboundVO value) {
        boolean visible = ReturnRefundViewModel.isReturn(value);
        view.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        boolean refunded = ReturnRefundViewModel.isRefunded(value);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"退款状态", refunded ? "已登记退款" : "尚未登记到账退款"});
        if (refunded) {
            rows.add(new String[]{"实际到账", StockCalc.money(value.refundAmount) + " 元"});
            rows.add(new String[]{"到账日期", DateFormatter.formatDate(value.refundDate)});
            rows.add(new String[]{"退款说明", value.refundRemark});
            rows.add(new String[]{"登记人", value.refundRegisteredBy == null ? null : value.refundRegisteredBy.getName()});
            rows.add(new String[]{"登记时间", DateFormatter.formatDateTime(value.refundRegisteredAt)});
        }
        StockUi.renderKv(view.stockRefundRows, rows);
        boolean actionable = refunded ? ReturnRefundViewModel.canCorrect(value) : ReturnRefundViewModel.canRegister(value);
        view.stockRefundAction.setVisibility(actionable ? View.VISIBLE : View.GONE);
        view.stockRefundAction.setText(refunded ? R.string.stock_refund_edit : R.string.stock_refund_register);
        view.stockRefundNotice.setText(value.refundBlockReason != null && !value.refundBlockReason.isEmpty() ? value.refundBlockReason
                : refunded ? "已自动生成关联收入。退款仅支持登记当天更正，退货数量和作废操作已锁定。"
                : "收到实际退款后登记，系统会自动关联一笔经营收入。每张退货单仅登记一次。" );
        view.stockRefundFinance.setVisibility(refunded && value.financeRecordId != null ? View.VISIBLE : View.GONE);
    }
}
