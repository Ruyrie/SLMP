package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

/** 免疫模板未发布修改摘要（客户端按字段拼“猪伪狂犬病活疫苗 · 加强：117 日龄改为 120 日龄”）。 */
@Getter
@Setter
public class ImmuneDraftChangeVO {

    /** ITEM_CHANGE */
    private String changeType;
    private String vaccineName;
    private String doseRound;
    /** 变化字段名，例如 计划时间、剂量；新增 / 删除时为空 */
    private String fieldName;
    private String before;
    private String after;
}
