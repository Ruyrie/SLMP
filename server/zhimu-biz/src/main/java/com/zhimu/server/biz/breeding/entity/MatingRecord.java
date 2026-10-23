package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 配种记录（t_mating_record，模块05 文档 2.3.3）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_mating_record")
public class MatingRecord extends AuditEntity {

    private Long farmId;
    private Long animalId;
    private Long cycleId;
    private LocalDate matingDate;
    /** MATING_METHOD：ARTIFICIAL / NATURAL */
    private String matingMethod;
    private String boarInfo;
    private Boolean isFirst;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
