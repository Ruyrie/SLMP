package com.zhimu.slmp.stock.ui.common;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 单据只读状态的 more_vert 菜单（技术准备文档 2.4.4、UI 文档 M10 只读状态），完全由 actions 决定：
 * EDITABLE“修改”“作废”“查看修改记录”；REQUEST_ONLY“提交修改申请 · 需审核”“查看修改记录”；PENDING 只保留“查看修改申请”。
 */
public final class RecordMenu {

    public static final int EDIT = 1;
    public static final int VOID = 2;
    public static final int REQUEST = 3;
    public static final int VIEW_REQUEST = 4;
    public static final int CHANGE_LOG = 5;

    private RecordMenu() {
    }

    /** 菜单点按回调 */
    public interface Callback {
        void onRecordMenu(int id);
    }

    /** 按 actions 算出菜单项（allowEdit 为 false 的单据没有“修改”页面，EDITABLE 也按申请处理） */
    @NonNull
    public static List<Integer> items(@Nullable StockActionsVO a, boolean allowEdit) {
        List<Integer> list = new ArrayList<>();
        String state = a == null ? null : a.editState;
        if (StockEnums.EDIT_PENDING.equals(state) || (a != null && a.pendingRequestId != null)) {
            list.add(VIEW_REQUEST);
            return list;
        }
        if (allowEdit && StockEnums.EDIT_EDITABLE.equals(state)) {
            if (a.canEdit) {
                list.add(EDIT);
            }
            if (a.canVoid == null || a.canVoid) {
                list.add(VOID);
            }
        } else if (a == null || a.canRequestChange == null || a.canRequestChange) {
            list.add(REQUEST);
        }
        list.add(CHANGE_LOG);
        return list;
    }

    public static boolean hasItems(@Nullable StockActionsVO a, boolean allowEdit) {
        return !items(a, allowEdit).isEmpty();
    }

    public static void show(@NonNull View anchor, @Nullable StockActionsVO a, boolean allowEdit,
                            @NonNull Callback cb) {
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        for (int id : items(a, allowEdit)) {
            switch (id) {
                case EDIT:
                    b.add(EDIT, anchor.getContext().getString(R.string.stock_menu_edit),
                            com.zhimu.slmp.base.R.drawable.ic_edit);
                    break;
                case VOID:
                    b.addDanger(VOID, anchor.getContext().getString(R.string.stock_menu_void),
                            com.zhimu.slmp.base.R.drawable.ic_delete);
                    break;
                case REQUEST:
                    b.addReview(REQUEST, anchor.getContext().getString(R.string.stock_menu_request_change),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note);
                    break;
                case VIEW_REQUEST:
                    b.add(VIEW_REQUEST, anchor.getContext().getString(R.string.stock_menu_view_request),
                            com.zhimu.slmp.base.R.drawable.ic_pending_actions);
                    break;
                default:
                    b.add(CHANGE_LOG, anchor.getContext().getString(R.string.stock_menu_change_log),
                            com.zhimu.slmp.base.R.drawable.ic_history);
                    break;
            }
        }
        b.setOnItemClickListener(cb::onRecordMenu).show();
    }
}
