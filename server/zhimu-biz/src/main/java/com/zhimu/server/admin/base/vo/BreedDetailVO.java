package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 品种详情（ADM-D04）。统计类字段本批没有模块04/05，返回 0 而不是 null。 */
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
    /** 在养批次，待模块04；本批 0 */
    private Integer activeBatchCount;
    /** 分布养殖场，待模块04；本批 0 */
    private Integer farmCount;
    /** 在档种畜，待模块05；本批 0 */
    private Integer breedingAnimalCount;
    /** BREED_TAB：WEIGHT / FEED / IMMUNE_INSPECT / BREEDING / INCUBATION / IMMUNE */
    private List<String> tabs = new ArrayList<>();
    private String lastModifiedByName;
    private LocalDateTime lastModifiedAt;
    private Integer version;
}
