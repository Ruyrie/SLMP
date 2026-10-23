package com.zhimu.server.biz.health.request;

import lombok.Data;

/** 入栏前确认 / 加入 / 撤销、启用计划等只需要 version 的请求体。 */
@Data
public class VersionRequest {

    private Integer version;
}
