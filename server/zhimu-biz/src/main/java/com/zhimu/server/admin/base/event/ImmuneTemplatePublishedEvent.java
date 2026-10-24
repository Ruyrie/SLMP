package com.zhimu.server.admin.base.event;

/** 免疫模板发布事件；模块08 在批次入栏时按线上版本生成计划，本事件供后续监听使用。 */
public record ImmuneTemplatePublishedEvent(Long templateId, Long versionId, Integer versionNo) {
}
