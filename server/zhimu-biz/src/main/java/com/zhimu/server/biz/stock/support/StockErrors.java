package com.zhimu.server.biz.stock.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模块09 的错误与文案：40001 的 field_errors 键用请求 JSON 的 snake_case 路径，
 * 业务错误按 App 的文案逐字给出（App 只显示 message）。
 */
public final class StockErrors {

    private StockErrors() {
    }

    /** 40001 字段错误 */
    public static BizException field(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    /** 40001 整单错误（不带 field_errors） */
    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    /** 40401 记录不存在或不属于当前养殖场 */
    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND);
    }

    /** 40902 版本冲突 */
    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, "数据已被他人修改，请刷新后重试");
    }

    /**
     * 40902 库存已变化（手工登记类出库、领用、报废、报损接口，带当前结存）。
     * data.current_qty 为数值字符串（按请求的录入单位，不带千分位与单位），客户端据此显示当前结存并重拉选项。
     *
     * @param currentQtyText 消息里的结存写法（带单位）
     * @param currentQty     录入单位下的当前结存；null 时不带 data
     */
    public static BizException stockChanged(String currentQtyText, BigDecimal currentQty) {
        String message = currentQtyText == null
                ? "库存已变化，请刷新后重试"
                : "库存已变化，当前结存 " + currentQtyText + "，请刷新后重试";
        return new BizException(ErrorCode.VERSION_CONFLICT, message,
                currentQty == null ? null : Map.of("current_qty", StockQty.number(currentQty)));
    }

    /** 40903 单据已作废 */
    public static BizException voided(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    /** 42201 结存不足 */
    public static BizException notEnough(String message) {
        return new BizException(ErrorCode.STOCK_NOT_ENOUGH, message);
    }

    /** 42201 出库超过结存（与 App err_42201_loss.json 一致） */
    public static BizException notEnoughOut(BigDecimal currentQty, String unitText) {
        return notEnough("超过当前结存 " + StockQty.plain(currentQty) + " " + unitText
                + "，出库后库存不能为负数");
    }

    /** 42204 批号已过期 / 已停用，只能报废 */
    public static BizException lotUnavailable(String lotNo, boolean expired) {
        String message = expired
                ? "批号 " + lotNo + " 已过期，不能出库，只能登记报废"
                : "批号 " + lotNo + " 已被平台停用，不能出库，只能登记报废";
        return new BizException(ErrorCode.LOT_UNAVAILABLE, message);
    }

    /** 42204 停用批号入库（App err_42204_inbound.json） */
    public static BizException lotRecalledInbound(String lotNo) {
        return new BizException(ErrorCode.LOT_UNAVAILABLE, "批号 " + lotNo + " 已被平台停用，不能入库");
    }

    /** 42203 禁用药（入库文案） */
    public static BizException bannedInbound(String name) {
        return new BizException(ErrorCode.BANNED_DRUG, name + "为禁用药，不能入库");
    }

    /** 42203 禁用药（建档文案） */
    public static BizException bannedArchive(String name) {
        return new BizException(ErrorCode.BANNED_DRUG, name + "为禁用药，不能建档");
    }

    /** 42203 禁用药（领用 / 退货文案） */
    public static BizException bannedUse() {
        return new BizException(ErrorCode.BANNED_DRUG, "该药品属于禁用兽药，不能使用");
    }

    /** 40307 已发生出库，需要提交修改申请 */
    public static BizException changeRequestRequired(String message) {
        return new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, message);
    }

    /** 40905 名称重复 */
    public static BizException duplicateName(String name) {
        return new BizException(ErrorCode.NAME_DUPLICATE, "已有名为“" + name + "”的饲料");
    }

    /** 40905 本场已建档 */
    public static BizException duplicateDrug(String name) {
        return new BizException(ErrorCode.NAME_DUPLICATE, "本场已有" + name + "的档案，不能重复建档");
    }
}
