package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前账号（AuthTokenVO.user、SessionVO.user、AccountOverviewVO.user） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUserVO {
    private Long userId;
    private String name;
    private String nameInitial;
    private String phoneMasked;
}
