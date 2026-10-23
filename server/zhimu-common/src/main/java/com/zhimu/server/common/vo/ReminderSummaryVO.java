package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** 提醒设置摘要：每日汇总开关与时间、免打扰开关与时段（时刻输出为 HH:mm） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSummaryVO {
    private Boolean digestEnabled;
    private LocalTime digestTime;
    private Boolean quietEnabled;
    private LocalTime quietStart;
    private LocalTime quietEnd;
}
