package com.zhimu.server.biz.today.request;

import lombok.Getter;
import lombok.Setter;

/** 打开消息（API-T15）：从推送打开时带 push_id。 */
@Getter
@Setter
public class MessageOpenRequest {

    private Long pushId;
}
