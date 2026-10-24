package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

/** 通用 {id, name} 引用（品类、品种、操作人等）。 */
@Getter
@Setter
public class IdNameVO {

    private Long id;
    private String name;

    public IdNameVO() {
    }

    public IdNameVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static IdNameVO of(Long id, String name) {
        return new IdNameVO(id, name);
    }
}
