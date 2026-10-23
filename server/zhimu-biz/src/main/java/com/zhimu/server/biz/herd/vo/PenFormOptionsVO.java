package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.vo.OptionVO;
import lombok.Data;

import java.util.List;

/** API-L10 栏舍表单选项（L05）：栏舍类型与同畜种启用品类。 */
@Data
public class PenFormOptionsVO {

    private List<OptionVO> penTypes;
    private List<CategoryOption> categories;

    /** 适用品类选项 */
    @Data
    public static class CategoryOption {
        private Long categoryId;
        private String categoryName;
        /** COUNT_UNIT：HEAD / BIRD */
        private String countUnit;
        private String countUnitText;
        private Boolean isBreeding;
    }
}
