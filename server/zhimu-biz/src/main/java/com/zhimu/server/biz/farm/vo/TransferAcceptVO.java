package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 接受转让结果（F30） */
@Data
public class TransferAcceptVO {
    private Long farmId;
    private String farmName;
    private String role;
}
