package com.zhimu.server.admin.base.query;

import lombok.Data;

/** ADM-D50 字典项列表查询；SYMPTOM / PEN_TYPE 可按品类或畜种分段。 */
@Data
public class DictItemQuery {

    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP */
    private String species;
    private Long categoryId;
}
