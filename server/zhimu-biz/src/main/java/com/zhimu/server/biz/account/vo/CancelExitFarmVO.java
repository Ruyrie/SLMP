package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 注销后将要退出的养殖场（本人为场长或员工） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelExitFarmVO {
    private Long farmId;
    private String name;
    private String role;
    private String roleText;
}
