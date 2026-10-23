package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 妊检记录（t_pregnancy_check，模块05 文档 2.3.4）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_pregnancy_check")
public class PregnancyCheck extends AuditEntity {

    private Long farmId;
    private Long animalId;
    private Long cycleId;
    private LocalDate checkDate;
    /** PREG_RESULT：POSITIVE / NEGATIVE */
    private String result;
    /** PREG_CHECK_METHOD：ULTRASOUND / OBSERVATION / OTHER */
    private String checkMethod;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
