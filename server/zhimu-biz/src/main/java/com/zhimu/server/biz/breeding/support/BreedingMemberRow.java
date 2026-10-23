package com.zhimu.server.biz.breeding.support;

import lombok.Data;

/** 模块02 在场成员只读行（登记人角色回显用）。 */
@Data
public class BreedingMemberRow {
    private Long userId;
    private String role;
}
