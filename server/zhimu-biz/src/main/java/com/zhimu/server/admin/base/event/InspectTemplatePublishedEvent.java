package com.zhimu.server.admin.base.event;

/** 巡检模板发布事件；模块07/08 后续监听（3B 发布时调用，本批只定义事件类）。 */
public record InspectTemplatePublishedEvent(Long templateId, Long versionId, Integer versionNo) {
}
