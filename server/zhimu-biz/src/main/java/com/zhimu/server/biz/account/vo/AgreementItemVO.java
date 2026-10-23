package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 协议条目 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgreementItemVO {
    private String version;
    private String title;
    private String url;
}
