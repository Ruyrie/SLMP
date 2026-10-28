package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 品种详情（ADM-D04）。统计类字段按 breed_id 实时统计。 */
@Data
public class BreedDetailVO {

    private Long id;
    private String name;
    private String description;
    private CategoryVO category;
    private Integer gestationDays;
    private Integer incubationDays;
    private Integer pregnancyCheckDays;
    private BreedRefVO offspringBreed;
    private String status;
    private String statusText;
    private String disableReason;
    /** 在养批次（t_batch ACTIVE） */
    private Integer activeBatchCount;
    /** 在养批次分布的养殖场数（去重） */
    private Integer farmCount;
    /** 在档种畜（未淘汰、未死亡） */
    private Integer breedingAnimalCount;
    /** BREED_TAB：WEIGHT / FEED / IMMUNE_INSPECT / BREEDING / INCUBATION / IMMUNE */
    private List<String> tabs = new ArrayList<>();
    private String lastModifiedByName;
    private LocalDateTime lastModifiedAt;
    private Integer version;
}
