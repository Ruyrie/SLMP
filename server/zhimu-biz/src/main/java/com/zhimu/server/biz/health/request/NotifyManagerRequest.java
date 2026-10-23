package com.zhimu.server.biz.health.request;

import lombok.Data;

/** API-D27 通知场长：action 选填（DELETE / UPDATE）。 */
@Data
public class NotifyManagerRequest {

    private String action;
}
