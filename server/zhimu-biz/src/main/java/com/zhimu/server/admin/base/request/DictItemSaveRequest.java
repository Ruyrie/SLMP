package com.zhimu.server.admin.base.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-D51 / D52 新增、编辑字典项。 */
@Data
public class DictItemSaveRequest {

    @Size(max = 10, message = "名称最多 10 个字")
    private String name;

    @Size(max = 50, message = "说明最多 50 个字")
    private String description;

    @Size(max = 40, message = "图标名最多 40 个字")
    private String icon;

    private Boolean remarkRequired;

    private Integer sortNo;

    @Valid
    private List<Scope> scopes = new ArrayList<>();

    /** 仅症状使用；知识库随管理端-02，本批忽略不落库 */
    private List<Long> diseaseIds = new ArrayList<>();

    /** D52 必填 */
    private Integer version;

    @Data
    public static class Scope {
        /** SPECIES / CATEGORY */
        private String scopeType;
        private String species;
        private Long categoryId;
    }
}
