package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;

/** API-D25 删除影响预判。 */
@Data
public class DeleteImpactVO {

    /** ADVANCE / NO_CHANGE_TODAY / NO_CHANGE_EARLIER / LOCKED */
    private String impact;
    /** 氟苯尼考粉 · 10月5日–10月9日 */
    private String recordTitle;
    private LocalDate releaseDateBefore;
    private LocalDate releaseDateAfter;
    private String notifyName;
    /** MANAGER / OWNER */
    private String notifyRole;
}
