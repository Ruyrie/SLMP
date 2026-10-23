package com.zhimu.server.biz.farm.query;

import lombok.Getter;
import lombok.Setter;

/** F18 可分配栏舍：不传 member_id 为邀请场景 */
@Getter
@Setter
public class PenOptionQuery {
    private Long memberId;
}
