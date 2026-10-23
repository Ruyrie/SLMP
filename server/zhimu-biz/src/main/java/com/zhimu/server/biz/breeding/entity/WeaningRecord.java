package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 断奶记录（t_weaning_record，模块05 文档 2.3.6）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_weaning_record")
public class WeaningRecord extends AuditEntity {

    private Long farmId;
    private Long animalId;
    private Long cycleId;
    private Long farrowingId;
    private LocalDate weanDate;
    private Integer weanedCount;
    private BigDecimal litterWeight;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
