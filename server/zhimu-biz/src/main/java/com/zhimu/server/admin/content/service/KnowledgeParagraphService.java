package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.support.KnowledgeHtml;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;

import java.util.List;
import java.util.Set;

/**
 * 知识正文清洗、段落拆分与发布写入（管理端-02 2.7 节）。
 */
public interface KnowledgeParagraphService {

    /** 本条目历史版本（含草稿）出现过的段落键，复制粘贴来的键会重新生成 */
    Set<String> historicalKeys(Long knowledgeId);

    /** 校验正文中的视频与图片，补齐段落文本、图片地址并重建受限 HTML */
    KnowledgeHtml.Parsed resolve(Long knowledgeId, String content);

    /** 发布 / 回退：整体替换线上段落，按 para_key 继承近 30 天引用次数 */
    void replace(Long knowledgeId, Integer versionNo, KnowledgeHtml.Parsed parsed, boolean online);

    /** 线上版本段落 */
    List<AiKnowledgeParagraph> onlineParagraphs(Long knowledgeId);
}
