package com.zhimu.server.biz.farm.query;

import lombok.Getter;
import lombok.Setter;

/** F04 名称重名检查 */
@Getter
@Setter
public class NameCheckQuery {
    private String name;
    private Long excludeFarmId;
}
