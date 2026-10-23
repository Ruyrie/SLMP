package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 症状关联常见病症（t_symptom_disease）：知识库随管理端-02，本批不写数据。 */
@Getter
@Setter
@TableName("t_symptom_disease")
public class SymptomDisease extends AdminDetailEntity {

    private Long symptomItemId;
    private Long knowledgeId;
    private Integer sortNo;
}
