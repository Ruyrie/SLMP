package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 修改配种（API-B13）。 */
@Data
public class MatingUpdateRequest {

    private String matingDate;
    private String matingMethod;
    private String boarInfo;
    private Integer version;
}
