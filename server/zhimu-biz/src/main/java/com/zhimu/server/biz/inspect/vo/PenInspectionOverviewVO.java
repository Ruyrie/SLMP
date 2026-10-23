package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.util.List;

/** API-I12 栏舍近 7 天概况。day_tag：ABNORMAL / BACKFILL / OVERDUE / DONE / MISSED / PENDING / NONE。 */
@Data
public class PenInspectionOverviewVO {

    private long penId;
    private String penName;
    private List<String> batchNos;
    private List<String> responsibleNames;
    private int recent7DoneDays;
    private int recent7ShouldDays;
    private String today;
    private String backfillFrom;
    private String backfillTo;
    private List<DayCell> days;

    @Data
    public static class DayCell {
        private String date;
        private String dayTag;
        private String abnormalSource;
        private Long inspectionId;
    }
}
