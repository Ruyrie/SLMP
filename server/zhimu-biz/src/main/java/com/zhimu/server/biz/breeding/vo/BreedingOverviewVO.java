package com.zhimu.server.biz.breeding.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 繁育概况（API-B01）：员工不输出 counts、due_soon、due_soon_total、pen_options。 */
@Getter
@Setter
public class BreedingOverviewVO {

    /** FARM_KIND：LIVESTOCK / POULTRY */
    private String farmKind;
    private String farmKindText;
    /** 母猪 / 母牛 / 母羊 */
    private String animalLabel;
    private String countUnit;
    private String countUnitText;
    private Counts counts;
    private int farrowDueCount;
    private int hatchDueCount;
    private List<DueSoonItem> dueSoon;
    private Integer dueSoonTotal;
    private List<PenBriefVO> penOptions;
    private boolean hasIncubation;
    private List<SwitchFarm> switchFarms;

    /** 分段计数：all 不含已淘汰和死亡 */
    @Getter
    @Setter
    public static class Counts {
        private int all;
        private int empty;
        private int mated;
        private int pregnant;
        private int lactating;
        private int exited;
    }

    /** 距预产 ≤ 3 天（含今天与已超期）的母猪 */
    @Getter
    @Setter
    public static class DueSoonItem {
        private long animalId;
        private String earTag;
        private String expectedDueDate;
        private int daysToDue;
    }

    /** 本账号其他有孵化记录的禽类养殖场 */
    @Getter
    @Setter
    public static class SwitchFarm {
        private long farmId;
        private String farmName;
        private int hatchDueCount;
    }
}
