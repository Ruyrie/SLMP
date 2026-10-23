package com.zhimu.server.biz.herd.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/** 模块04 常用业务异常与文案（模块04 文档 2.5.2、2.5.4、2.5.7）。 */
public final class HerdErrors {

    public static final String MSG_OWNER_OR_MANAGER = "这个页面只对所有者和场长开放";
    public static final String MSG_STAFF_ONLY = "我的栏舍只对员工开放";
    public static final String MSG_BATCH_FORBIDDEN = "你没有这个批次的查看权限";
    public static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_RECORD_DELETED = "该批次记录已被删除，无法查看";

    private HerdErrors() {
    }

    public static BizException roleForbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message == null ? MSG_OWNER_OR_MANAGER : message);
    }

    public static BizException penForbidden() {
        return new BizException(ErrorCode.PEN_FORBIDDEN, ErrorCode.PEN_FORBIDDEN.getDefaultMessage());
    }

    public static BizException batchForbidden() {
        return new BizException(ErrorCode.PEN_FORBIDDEN, MSG_BATCH_FORBIDDEN);
    }

    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND);
    }

    public static BizException deletedBatch() {
        return new BizException(ErrorCode.RECORD_DELETED, MSG_RECORD_DELETED);
    }

    public static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
    }

    public static BizException stateChanged(String message) {
        return new BizException(ErrorCode.STATE_CHANGED, message);
    }

    public static BizException batchEnded() {
        return new BizException(ErrorCode.BATCH_ENDED, "该批次已结束，不能登记");
    }

    public static BizException penDisabled(String message) {
        return new BizException(ErrorCode.PEN_DISABLED, message == null ? "这个栏舍已停用，请先启用" : message);
    }

    public static BizException penCategoryConflict(String message) {
        return new BizException(ErrorCode.PEN_CATEGORY_CONFLICT,
                message == null ? "同一栏舍只能放同一品类的批次" : message);
    }

    public static BizException penNotEmpty(int batchCount) {
        return new BizException(ErrorCode.PEN_NOT_EMPTY,
                "栏舍里还有 " + batchCount + " 个在栏批次，全部转出或出栏后才能停用");
    }

    public static BizException changeRequired(String message) {
        return new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, message);
    }

    public static BizException pendingExists() {
        return new BizException(ErrorCode.PENDING_EXISTS, "这条记录已有修改申请在审核中");
    }

    public static BizException paramInvalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException fieldInvalid(String field, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field_errors", java.util.List.of(new com.zhimu.server.common.error.FieldErrorVO(field, message)));
        return new BizException(ErrorCode.PARAM_INVALID, "参数校验失败", data);
    }

    /** 42205：数量超过栏舍在栏数；seenStockCount 与最新值不同时用“已变为”文案 */
    public static BizException countExceeded(String penName, int stockCount, Integer seenStockCount) {
        String message = seenStockCount != null && seenStockCount != stockCount
                ? penName + "的在栏数已变为 " + stockCount + " 头，不能超过 " + stockCount + " 头"
                : "不能超过 " + penName + " 的在栏数 " + stockCount + " 头";
        return new BizException(ErrorCode.COUNT_EXCEEDED, message, Map.of(
                "pen_id", 0L,
                "pen_name", penName,
                "stock_count", stockCount));
    }

    public static BizException countExceeded(Long penId, String penName, int stockCount, Integer seenStockCount) {
        BizException exception = countExceeded(penName, stockCount, seenStockCount);
        return exception.with("pen_id", penId);
    }

    /** 42206：入栏 / 转入超过容量，带 need_confirm 与明细 */
    public static BizException capacityConfirm(Long penId, String penName, String action, int capacity,
                                               int current, int after, int over) {
        String message = action + "后超过栏舍容量。" + penName + "容量 " + capacity + " 头，现有 " + current
                + " 头，" + action + "后共 " + after + " 头，超出 " + over + " 头。";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("need_confirm", true);
        data.put("pen_id", penId);
        data.put("capacity", capacity);
        data.put("current", current);
        data.put("after", after);
        data.put("over", over);
        return new BizException(ErrorCode.CAPACITY_CONFIRM, message, data);
    }

    public static BizException stateNotAllowed(String message) {
        return new BizException(ErrorCode.STATE_NOT_ALLOWED, message);
    }
}
