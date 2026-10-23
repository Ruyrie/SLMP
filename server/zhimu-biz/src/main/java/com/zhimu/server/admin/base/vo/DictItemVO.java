package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 字典项（ADM-D50–D54）。 */
@Data
public class DictItemVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    /** 空数组表示全部品类 */
    private List<DictScopeVO> scopes = new ArrayList<>();
    private Boolean remarkRequired;
    private Boolean isFallback;
    private Boolean isSystem;
    private Integer sortNo;
    private String status;
    private String statusText;
    private String disableReason;
    /** 使用量，待各业务模块；本批 0 */
    private Integer usageCount;
    private String usageLabel;
    /** 症状关联常见病症，待管理端-02；本批空数组 */
    private List<DictDiseaseVO> diseases = new ArrayList<>();
    /** 仅收支一级分类 */
    private String autoSourceText;
    /** 仅收支一级分类 */
    private Integer subcategoryCount;
    private Integer version;
}
