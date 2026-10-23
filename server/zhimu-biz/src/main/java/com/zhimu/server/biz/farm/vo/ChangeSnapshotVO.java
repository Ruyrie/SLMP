package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 资料变更前后快照（FarmChangeVO.before / after） */
@Data
public class ChangeSnapshotVO {
    private String name;
    private String address;
    private List<QualFileVO> qualificationFiles;
    /** 以下只在 after 输出 */
    private String province;
    private String city;
    private String district;
    private String township;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer locAccuracy;
}
