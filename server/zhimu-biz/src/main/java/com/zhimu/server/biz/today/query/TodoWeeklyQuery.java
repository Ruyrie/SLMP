package com.zhimu.server.biz.today.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** API-T05 周视图分组。 */
@Getter
@Setter
public class TodoWeeklyQuery {

    private LocalDate weekStart;
    private String scope;
    private String category;
    /** 每天最多几项，1–20，默认 5 */
    private Integer perDay;
}
