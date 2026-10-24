package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 禁用药拦截记录行（只读，ADM-D34）。 */
@Getter
@Setter
public class BannedHitRow {

    private LocalDateTime hitAt;
    private String hitSource;
    private String farmName;
    private String userName;
    private String keyword;
}
