package com.zhimu.server.biz.breeding.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 登记下拉候选查询（API-B04）：purpose 为 MATING / FARROWING / WEANING，size 默认 50。 */
@Getter
@Setter
public class BreedingOptionQuery extends PageQuery {

    private String purpose;
    private String keyword;

    public BreedingOptionQuery() {
        setSize(50);
    }

    public String keywordOrNull() {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        return value.isEmpty() ? null : value;
    }
}
