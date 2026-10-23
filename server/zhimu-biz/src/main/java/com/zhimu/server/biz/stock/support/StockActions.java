package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.vo.StockActionsVO;
import com.zhimu.server.common.util.BeijingTime;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单据可做的操作（App 的只读菜单完全由 actions 决定）。
 * <p>
 * 登记当天的单据可直接修改、作废（edit_deadline = 当天 23:59:59）；已发生出库的入库单与
 * 不是当天登记的出库单只能提交修改申请；已作废的单据不能再操作。
 */
public final class StockActions {

    private StockActions() {
    }

    /** 可编辑（当天、有效、未发生出库） */
    public static StockActionsVO editable(LocalDate createdDate) {
        StockActionsVO actions = new StockActionsVO();
        actions.setCanEdit(true);
        actions.setCanVoid(true);
        actions.setCanRequestChange(false);
        actions.setEditState(StockCodes.EDIT_EDITABLE);
        actions.setEditDeadline(createdDate != null
                ? BeijingTime.format(createdDate.atTime(23, 59, 59))
                : BeijingTime.format(BeijingTime.today().atTime(23, 59, 59)));
        return actions;
    }

    /** 只能提交修改申请（已过可编辑期限或已发生出库） */
    public static StockActionsVO requestOnly(boolean canRequest) {
        StockActionsVO actions = new StockActionsVO();
        actions.setCanEdit(false);
        actions.setCanVoid(false);
        actions.setCanRequestChange(canRequest);
        actions.setEditState(StockCodes.EDIT_REQUEST_ONLY);
        return actions;
    }

    /** 已作废：全部不可操作 */
    public static StockActionsVO voided() {
        StockActionsVO actions = requestOnly(false);
        actions.setCanVoid(false);
        return actions;
    }

    /** 批号详情：报废 / 出库能力 */
    public static StockActionsVO lot(boolean canScrap, boolean canOut) {
        StockActionsVO actions = requestOnly(false);
        actions.setCanScrap(canScrap);
        actions.setCanOut(canOut);
        return actions;
    }

    /** 判断单据是否登记当天（created_at 的北京日期 = 今天） */
    public static boolean registeredToday(LocalDateTime createdAt) {
        return createdAt != null && createdAt.toLocalDate().isEqual(BeijingTime.today());
    }
}
