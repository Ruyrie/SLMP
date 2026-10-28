package com.zhimu.server.biz.ai.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 课程、专题与知识原文的分享地址：USER_WEB_BASE_URL（与批次二维码同一配置）+ Web 用户端路由
 * （web/apps/user/src/router/ai.ts：WU-K02 /knowledge/:id、WU-K03 /courses?topic=、WU-K04 /courses/:id）。
 */
@Component
public class AiShareLinks {

    private final String base;

    public AiShareLinks(@Value("${zhimu.herd.qr.web-base-url:http://127.0.0.1:5173}") String webBaseUrl) {
        String value = webBaseUrl == null ? "" : webBaseUrl.trim();
        this.base = value.replaceAll("/+$", "");
    }

    /** 专题：养殖课堂页打开专题抽屉 */
    public String topic(Long topicId) {
        return topicId == null || base.isEmpty() ? null : base + "/courses?topic=" + topicId;
    }

    /** 知识原文 */
    public String knowledge(Long knowledgeId) {
        return knowledgeId == null || base.isEmpty() ? null : base + "/knowledge/" + knowledgeId;
    }

    /** 课程播放页 */
    public String course(Long courseId) {
        return courseId == null || base.isEmpty() ? null : base + "/courses/" + courseId;
    }
}
