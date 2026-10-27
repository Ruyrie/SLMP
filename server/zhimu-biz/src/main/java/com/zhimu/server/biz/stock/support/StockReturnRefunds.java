package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/** 退款后原库存单被冻结，财务更正只能通过专用退款入口。 */
public final class StockReturnRefunds {
    public static final String LOCKED_MESSAGE = "已登记退款，不能修改退货数量或作废原单；退款信息仅支持登记当天更正";
    private StockReturnRefunds() { }
    public static boolean supported(StockOutbound outbound) {
        return outbound != null && StockCodes.MATERIAL_DRUG.equals(outbound.getMaterialType())
                && StockCodes.OUT_RETURN.equals(outbound.getOutType());
    }
    public static boolean refunded(StockOutbound outbound) {
        return outbound != null && (outbound.getFinanceRecordId() != null || outbound.getRefundRegisteredAt() != null);
    }
    public static void requireUnrefunded(StockOutbound outbound) {
        if (refunded(outbound)) throw new BizException(ErrorCode.STATE_CHANGED, LOCKED_MESSAGE);
    }
}
