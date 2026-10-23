package com.zhimu.server.biz.herd.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-L06 批次列表 / 下拉参数。 */
@Getter
@Setter
public class BatchListQuery extends PageQuery {

    /** ACTIVE / ENDED / ALL；默认 ACTIVE，员工强制 ACTIVE */
    private String status;
    private String keyword;
    /** ENTRY_DATE 入栏日期近到远 / DAY_AGE 日龄大到小 */
    private String sort;
    private Long penId;
    private Long categoryId;
    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP */
    private String species;
    /** BATCH_PURPOSE：COMMERCIAL / BREEDING */
    private String purpose;
}
