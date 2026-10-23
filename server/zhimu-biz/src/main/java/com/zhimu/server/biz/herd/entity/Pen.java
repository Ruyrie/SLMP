package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 栏舍（t_pen）：不提供删除，不设 deleted。 */
@Getter
@Setter
@FarmScoped
@TableName("t_pen")
public class Pen extends AuditEntity {

    private Long farmId;
    private String penCode;
    private String penName;
    /** PEN_TYPE：NURSERY / FATTENING / SOW / FARROWING / BROODING / BROILER / BREEDER */
    private String penType;
    private Long categoryId;
    private Integer capacity;
    private Integer stockCount;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private LocalDateTime disabledAt;
    private Long disabledBy;

    @Version
    private Integer version;

    private Long updatedBy;
}
