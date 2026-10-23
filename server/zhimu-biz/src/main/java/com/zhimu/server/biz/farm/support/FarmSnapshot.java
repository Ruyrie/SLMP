package com.zhimu.server.biz.farm.support;

import java.math.BigDecimal;
import java.util.List;

/** 注册信息快照（t_farm_audit.snapshot_json；资料变更的 before_json 复用名称、地址与资质图字段） */
public record FarmSnapshot(
        String name,
        Long mainCategoryId,
        Integer scale,
        String contactName,
        String contactPhone,
        String address,
        String province,
        String city,
        String district,
        String township,
        String adcode,
        BigDecimal longitude,
        BigDecimal latitude,
        Integer locAccuracy,
        List<Long> qualificationFileIds) {

    public List<Long> fileIds() {
        return qualificationFileIds == null ? List.of() : qualificationFileIds;
    }
}
