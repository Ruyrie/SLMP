package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API-U09 启用的品类与品种树，字段与 App lib_common CategoryVO / CategoryBreedVO 完全一致。
 * 只含启用品类下的启用品种，按 sort_no。
 */
@Data
public class BaseCategoryVO {

    private Long id;
    private String name;
    private String species;
    private String speciesText;
    private String countUnit;
    private String countUnitText;
    /** JSON 键为 is_breeding（App 字段 isBreeding） */
    private Boolean isBreeding;
    private List<BaseCategoryBreedVO> breeds = new ArrayList<>();
}
