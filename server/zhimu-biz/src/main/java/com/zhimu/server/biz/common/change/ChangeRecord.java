package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.BizType;

/**
 * 原记录的定位信息与版本（U01/U02 的输入）。
 *
 * @param recordVersion 原记录版本：有 version 的表取 version，没有的取 updated_at（精确到毫秒）；与申请中的 record_version 比对
 * @param deleted       已删除
 * @param voided        已作废
 */
public record ChangeRecord(
        BizType bizType,
        Long bizId,
        Long farmId,
        Long batchId,
        Long penId,
        String recordLocator,
        String recordTitle,
        String recordSubtitle,
        String recordVersion,
        boolean deleted,
        boolean voided) {
}
