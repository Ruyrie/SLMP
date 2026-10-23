package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** API-D35 消毒剂下拉查询：keyword 选填。 */
@Getter
@Setter
public class DisinfectantQuery {

    private String keyword;
}
