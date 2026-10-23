package com.zhimu.server.biz.breeding.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 待产 / 待出雏查询（API-B22、B23）：keyword 为耳号，或批次号、栏舍名。 */
@Getter
@Setter
public class BreedingDueQuery extends PageQuery {

    private String keyword;

    public String keywordOrNull() {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        return value.isEmpty() ? null : value;
    }
}
