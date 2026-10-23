package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 名称重名检查（F04） */
@Data
public class NameCheckVO {
    private Boolean available;
    private String message;
}
