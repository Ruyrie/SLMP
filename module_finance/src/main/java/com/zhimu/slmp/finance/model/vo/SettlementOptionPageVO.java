package com.zhimu.slmp.finance.model.vo;

import com.zhimu.slmp.network.model.PageVO;

/**
 * API-E26 分页：另带本场待确认补录数
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SettlementOptionPageVO extends PageVO<SettlementOptionVO> {

    private int farmPendingSupplementCount;

    public int getFarmPendingSupplementCount() {
        return farmPendingSupplementCount;
    }
}
