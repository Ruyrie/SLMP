package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 提交结果查询（API-B32）：超时后确认记录是否已保存。 */
@Getter
@Setter
public class SubmissionVO {

    private boolean saved;
    /** SUBMISSION_TYPE：MATING / PREG_CHECK / FARROWING / WEANING / INCUBATION / HATCH */
    private String type;
    private Long recordId;
    private RegisterResultVO result;
}
