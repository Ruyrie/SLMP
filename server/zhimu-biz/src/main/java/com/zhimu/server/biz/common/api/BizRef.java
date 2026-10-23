package com.zhimu.server.biz.common.api;

import com.zhimu.server.common.enums.BizType;

/** 业务记录引用：biz_type + biz_id */
public record BizRef(BizType bizType, Long bizId) {
}