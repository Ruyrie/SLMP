package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S17 冻结结果：收到冻结消息的在场成员数与养殖场名称。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreezeResultVO {

    private Integer memberCount;
    private String farmName;
}
