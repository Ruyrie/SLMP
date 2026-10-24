package com.zhimu.server.admin.content.mapper;

import lombok.Data;

/** 通用 id → 名称行（管理员、品类、品种、养殖场等）。 */
@Data
public class NameRow {
    private Long id;
    private String name;
}
