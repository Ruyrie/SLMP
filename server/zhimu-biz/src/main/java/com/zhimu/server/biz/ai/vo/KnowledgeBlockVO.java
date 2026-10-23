package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * 知识正文段落。
 */
@Data
public class KnowledgeBlockVO {

    private String paraKey;
    /** TEXT / HEADING / QUOTE / LIST / IMAGE */
    private String paraType;
    private String text;
    private String contentHtml;
    private String imageUrl;
}
