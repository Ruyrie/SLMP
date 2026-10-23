package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;

/** API-D30 任务数与提醒预估（纯计算）。 */
@Data
public class DisinfectPlanPreviewVO {

    private Boolean ready;
    /** 全周期执行次数 */
    private Integer times;
    /** 全周期任务数 = 次数 × 栏舍数 */
    private Integer taskCount;
    /** 首批（90 天内）生成条数 */
    private Integer generateNowCount;
    private String readyText;
    private String penText;
    private String remindText;
    /** 修改时的生效日 */
    private LocalDate regenerateFrom;
}
