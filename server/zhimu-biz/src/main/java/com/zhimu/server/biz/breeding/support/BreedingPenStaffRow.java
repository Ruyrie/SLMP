package com.zhimu.server.biz.breeding.support;

import lombok.Data;

/** 模块02 栏舍负责员工只读行（t_member_pen，在场成员）：待产 / 待出雏列表的“负责员工”。 */
@Data
public class BreedingPenStaffRow {
    private Long penId;
    private Long userId;
}
