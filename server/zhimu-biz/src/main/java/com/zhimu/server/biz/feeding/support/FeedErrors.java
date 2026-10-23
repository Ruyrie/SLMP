package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.math.BigDecimal;
import java.util.List;

/** 模块06 业务错误：40001 字段错误、40303 角色、40304 栏舍范围、40903 状态、42201 库存。 */
public final class FeedErrors {

    private FeedErrors() {
    }

    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message)
                .with("field_errors", List.of(new FieldErrorVO(field, message)));
    }

    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND, "这条记录不存在或已无权查看");
    }

    public static BizException roleForbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message);
    }

    public static BizException planForbidden() {
        return roleForbidden("你没有查看饲喂方案的权限");
    }

    public static BizException execForbidden() {
        return roleForbidden("你没有查看饲喂执行情况的权限");
    }

    public static BizException recordForbidden() {
        return roleForbidden("你没有修改这条记录的权限");
    }

    public static BizException penForbidden() {
        return new BizException(ErrorCode.PEN_FORBIDDEN,
                "你没有这个栏舍的登记权限")
                .with("detail", "你的负责栏舍已调整，该栏舍不在你负责的范围内。如需登记，请联系所有者或场长。");
    }

    public static BizException stateChanged(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
    }

    public static BizException changeRequired(String message) {
        return new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, message);
    }

    public static BizException stockNotEnough(Long feedId, BigDecimal stockQty, BigDecimal needQty, boolean staff) {
        String tail = staff ? "请联系所有者或场长先登记入库。" : "请先登记入库。";
        String message = "库存不足，不能确认投喂。本次需要 " + plain(needQty) + " 千克，结存 "
                + plain(stockQty) + " 千克，" + tail;
        return new BizException(ErrorCode.STOCK_NOT_ENOUGH, message)
                .with("feed_id", feedId)
                .with("stock_qty", stockQty)
                .with("need_qty", needQty);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
