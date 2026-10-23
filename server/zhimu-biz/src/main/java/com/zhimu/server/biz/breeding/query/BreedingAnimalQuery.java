package com.zhimu.server.biz.breeding.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 种畜列表查询（API-B02）：status 为 ANIMAL_FILTER（默认 ALL），keyword 匹配耳号。 */
@Getter
@Setter
public class BreedingAnimalQuery extends PageQuery {

    private String status;
    private String keyword;
    private Long penId;

    public String statusOrDefault() {
        return status == null || status.isBlank() ? "ALL" : status;
    }

    public String keywordOrNull() {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        return value.isEmpty() ? null : value;
    }
}
