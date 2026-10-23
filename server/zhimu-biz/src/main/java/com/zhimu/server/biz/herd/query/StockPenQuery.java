package com.zhimu.server.biz.herd.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-L04 按栏舍存栏参数。 */
@Getter
@Setter
public class StockPenQuery extends PageQuery {

    /** CODE 编号升序 / STOCK 存栏降序 / OCCUPANCY 占用率降序，停用栏舍始终在末尾 */
    private String sort;
    private String keyword;
}
