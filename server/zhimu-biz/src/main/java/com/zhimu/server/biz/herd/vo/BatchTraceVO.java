package com.zhimu.server.biz.herd.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Anonymous traceability uses an explicit allowlist, never the management archive DTO. */
@Data
public class BatchTraceVO {
    private String batchNo;
    private String farmName;
    private String categoryName;
    private String breedName;
    private String source;
    private String status;
    private LocalDate entryDate;
    private LocalDate endDate;
    private Integer entryAgeDays;
    private BigDecimal entryAvgWeight;
    private LocalDateTime updatedAt;
    private List<Event> events;

    @Data
    public static class Event {
        private String type;
        private LocalDate date;
        private String name;
        private Integer quantity;
        private BigDecimal avgWeight;
        private LocalDate withdrawalEndDate;
        private LocalDateTime updatedAt;
    }
}
