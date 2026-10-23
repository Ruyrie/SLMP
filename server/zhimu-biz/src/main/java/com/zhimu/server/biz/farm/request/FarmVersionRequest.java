package com.zhimu.server.biz.farm.request;

import lombok.Data;

/** 停用、重新启用（API-F11、F12）：version 取 basic.version */
@Data
public class FarmVersionRequest {
    private Integer version;
}
