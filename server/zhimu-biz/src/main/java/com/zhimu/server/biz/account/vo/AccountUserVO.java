package com.zhimu.server.biz.account.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    /**
     * 头像临时地址；没有头像时为 null。App 依赖字段存在（全局 null 不输出，这里显式保留）。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String avatarUrl;
}
