package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-S11 近 90 天登录记录。 */
@Getter
@Setter
public class AdminLoginLogRow {

    private LocalDateTime time;
    private String clientType;
    private String ipRegion;
    private String result;
    private String failReason;
}
