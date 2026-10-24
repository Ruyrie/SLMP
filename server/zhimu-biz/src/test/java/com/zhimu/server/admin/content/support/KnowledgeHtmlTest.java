package com.zhimu.server.admin.content.support;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeHtmlTest {

    @Test
    void parsesBlocksAndKeepsStableKeys() {
        String html = "<h2 data-para-key=\"p1\"><strong>小标题</strong></h2>"
                + "<p data-para-key=\"p1\">正文<script>alert(1)</script></p>"
                + "<ul data-para-key=\"p3\"><li>第一项</li><li><strong>第二项</strong></li></ul>"
                + "<div data-type=\"video\" data-course-id=\"9\" data-para-key=\"p4\"></div>"
                + "<img data-para-key=\"p5\" data-file-id=\"88\" src=\"https://cdn.example.com/a.jpg\"/>";
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse(html, Set.of("p1", "p3", "p4", "p5"));

        assertThat(parsed.paragraphs()).hasSize(5);
        assertThat(parsed.paragraphs()).extracting(KnowledgeHtml.Paragraph::paraType)
                .containsExactly("HEADING", "TEXT", "LIST", "VIDEO", "IMAGE");
        assertThat(parsed.paragraphs().get(0).contentHtml()).isEqualTo("<strong>小标题</strong>");
        assertThat(parsed.paragraphs().get(1).contentHtml()).doesNotContain("script");
        assertThat(parsed.paragraphs().get(1).contentText()).isEqualTo("正文");
        // 重复键保留第一个，第二个重新生成
        assertThat(parsed.paragraphs().get(1).paraKey()).isNotEqualTo("p1");
        assertThat(parsed.paragraphs().get(3).courseId()).isEqualTo(9L);
        assertThat(parsed.paragraphs().get(4).imageFileId()).isEqualTo(88L);
        assertThat(parsed.content()).contains("data-para-key").contains("data-course-id=\"9\"");
        assertThat(parsed.contentText()).contains("正文").contains("小标题").contains("第一项");
    }

    @Test
    void copiedKeyOutsideHistoryIsRegenerated() {
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse(
                "<p data-para-key=\"copied\">复制来的段落</p>", Set.of("history-key"));
        assertThat(parsed.paragraphs()).hasSize(1);
        assertThat(parsed.paragraphs().get(0).paraKey()).isNotEqualTo("copied")
                .matches("[a-z0-9]{12}");
    }

    @Test
    void imageWithoutHttpsSourceIsRejectedByParserUrl() {
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse("<img data-file-id=\"1\" src=\"http://a/b.jpg\"/>", null);
        assertThat(parsed.paragraphs().get(0).imageUrl()).isNull();
    }
}
