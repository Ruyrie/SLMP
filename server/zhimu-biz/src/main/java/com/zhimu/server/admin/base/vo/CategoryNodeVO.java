package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 品类品种树（ADM-D01，数组；含已停用品类与品种）。 */
@Data
public class CategoryNodeVO {

    private Long id;
    private String name;
    private String species;
    private String speciesText;
    private String countUnit;
    private String countUnitText;
    private Boolean isBreeding;
    private String status;
    private String statusText;
    private Integer sortNo;
    private Integer breedCount;
    private List<BreedNodeVO> breeds = new ArrayList<>();

    /** 树中的品种（副文字用 gestation_days / incubation_days）。 */
    @Data
    public static class BreedNodeVO {
        private Long id;
        private String name;
        private String status;
        private String statusText;
        private Integer sortNo;
        private Integer gestationDays;
        private Integer incubationDays;
        private Boolean hasStdWeight;
        private Boolean hasFeedStd;
        private Boolean hasImmuneTemplate;
    }
}
