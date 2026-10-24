package com.zhimu.server.admin.content.vo;

import lombok.Data;

/** 自动截取的封面候选。 */
@Data
public class CoverCandidateVO {

    private Long fileId;
    private String url;
}
