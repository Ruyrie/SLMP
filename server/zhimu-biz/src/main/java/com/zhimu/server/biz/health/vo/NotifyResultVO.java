package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D27 通知场长结果。 */
@Data
public class NotifyResultVO {

    private List<String> notifiedNames = new ArrayList<>();
}
