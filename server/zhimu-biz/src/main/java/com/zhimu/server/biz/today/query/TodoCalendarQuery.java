package com.zhimu.server.biz.today.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** API-T03 日期汇总。 */
@Getter
@Setter
public class TodoCalendarQuery {

    private LocalDate startDate;
    private LocalDate endDate;

    /** CURRENT / ALL，默认 CURRENT */
    private String scope;

    /** WORK / WARNING / BUSINESS，可选 */
    private String category;
}
