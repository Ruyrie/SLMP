package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

/** 通用 {id, name} 只读行（管理员、养殖场、用户）。 */
@Getter
@Setter
public class IdNameRow {

    private Long id;
    private String name;
}
