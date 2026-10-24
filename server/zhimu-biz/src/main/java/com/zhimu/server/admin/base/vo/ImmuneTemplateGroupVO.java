package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ADM-D14 免疫模板列表：按品类分组的每个启用品种一行；没有模板的品种 template_id 为空。
 */
@Getter
@Setter
public class ImmuneTemplateGroupVO {

    private Long categoryId;
    private String categoryName;
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        /** 未配置模板时为空 */
        private Long templateId;
        private Long breedId;
        private String breedName;
        private String name;
        private Integer itemCount;
        private Integer onlineVersionNo;
        private Integer activeBatchCount;
        private Boolean hasDraft;
        private Boolean hasByDate;
    }
}
