package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-D34 禁用药拦截记录行。 */
@Getter
@Setter
public class BannedHitRowVO {

    private LocalDateTime hitAt;
    private String hitSource;
    private String hitSourceText;
    private String farmName;
    private String userName;
    private String keyword;
}
