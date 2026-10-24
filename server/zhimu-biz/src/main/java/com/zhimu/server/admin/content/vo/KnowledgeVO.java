package com.zhimu.server.admin.content.vo;

import lombok.Data;

/** 知识写接口返回。 */
@Data
public class KnowledgeVO {

    private Long id;
    private String status;
    private Integer onlineVersionNo;
    private Integer newVersionNo;
    private Integer affectedAnswers30d;
    private Integer version;
}
