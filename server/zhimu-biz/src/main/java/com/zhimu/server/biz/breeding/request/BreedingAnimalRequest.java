package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 新增母猪（API-B07）/ 编辑档案（API-B08）。 */
@Data
public class BreedingAnimalRequest {

    private String earTag;
    private Long breedId;
    private String birthDate;
    /** ANIMAL_SOURCE：PURCHASED / SELF_BRED */
    private String source;
    private Long penId;
    private Integer initialParity;
    /** B08 必填 */
    private Integer version;
}
