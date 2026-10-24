package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-D15 / D16 / D23 免疫模板详情。有草稿时 items 为草稿项，否则为线上项。 */
@Getter
@Setter
public class ImmuneTemplateVO {

    private Long templateId;
    private String name;
    private IdNameVO breed;
    private IdNameVO category;
    private Integer onlineVersionNo;
    private String publishedByName;
    private LocalDateTime publishedAt;
    /** 按本模板生成计划的在养批次数 */
    private Integer activeBatchCount;
    /** 有按日期项目时：在档种畜分布养殖场数 */
    private Integer breedingFarmCount;
    private Boolean hasDraft;
    private List<ImmuneDraftChangeVO> draftChanges = new ArrayList<>();
    private List<ImmuneItemVO> items = new ArrayList<>();
    private Integer version;
}
