package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-D53 字典项拖动排序。 */
@Data
public class ItemOrderRequest {

    @NotEmpty(message = "请选择要排序的字典项")
    private List<Long> itemIds = new ArrayList<>();

    /** 症状按品类分段排序时必填 */
    private Long categoryId;
}
