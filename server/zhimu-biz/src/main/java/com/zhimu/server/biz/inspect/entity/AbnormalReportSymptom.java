package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** 上报症状（t_abnormal_report_symptom）：名称快照。 */
@Getter
@Setter
@FarmScoped
@TableName("t_abnormal_report_symptom")
public class AbnormalReportSymptom extends AuditEntity {

    private Long farmId;
    private Long reportId;
    private Long symptomId;
    private String symptomName;
    private Integer sortNo;
}
