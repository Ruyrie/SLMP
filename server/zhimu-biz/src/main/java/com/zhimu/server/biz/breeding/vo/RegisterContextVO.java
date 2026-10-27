package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 登记表单带入（API-B11）：按 type 返回不同字段。 */
@Getter
@Setter
public class RegisterContextVO {

    /** REGISTER_TYPE：MATING / PREG_CHECK / FARROWING / WEANING */
    private String type;
    private BreedingAnimalOptionVO animal;
    private String breedName;
    private Integer gestationDays;
    private String minMatingDate;
    private Long cycleId;
    private String firstMatingDate;
    private Integer daysSinceMating;
    private String expectedDueDate;
    private Integer daysToDue;
    private DefaultPen defaultPen;
    private OffspringBreed offspringBreed;
    private Long farrowingId;
    private String farrowDate;
    private Integer nursingDays;
    private Integer liveBorn;

    /** 产仔默认入栏栏舍（只计批次存栏） */
    @Getter
    @Setter
    public static class DefaultPen {
        private long penId;
        private String penName;
        private int capacity;
        private int currentCount;
        private String unit;
    }

    /** 后代品种（按后代品种规则推导） */
    @Getter
    @Setter
    public static class OffspringBreed {
        private long breedId;
        private String breedName;
        private Long categoryId;
    }
}
