package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.util.List;

/** 资质图（RegistrationVO、ChangeSnapshotVO 中） */
@Data
public class QualFileVO {
    private Long fileId;
    private String url;
    private String thumbUrl;
    private String mediaType;
    private String mimeType;
    private Long size;
    private Integer width;
    private Integer height;
    private String status;
    private Integer sortNo;
    /** 审核结果中标“需重拍” */
    private Boolean reshoot;
    /** 资料变更 after 中标“新增” */
    private Boolean isNew;
}
