package com.zhimu.server.admin.content.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeRichTextTest {

    @Test
    void keepsOnlyAllowedTagsAndStripsScriptsAndAttributes() {
        String clean = NoticeRichText.sanitize(
                "<p onclick=\"x\">各养殖场：<b>注意</b></p><script>alert(1)</script>"
                        + "<ol><li>第一条</li></ol><img src=\"x\" onerror=\"alert(1)\">");
        assertThat(clean).contains("<p>各养殖场：<strong>注意</strong></p>")
                .contains("<ol><li>第一条</li></ol>")
                .doesNotContain("script", "onclick", "onerror", "<img");
        assertThat(NoticeRichText.plainText(clean)).isEqualTo("各养殖场：注意\n第一条");
    }

    @Test
    void countsPlainTextNotHtml() {
        assertThat(NoticeRichText.contentLength("<p>你好</p><p>牧通</p>")).isEqualTo(5);
        assertThat(NoticeRichText.contentLength("&nbsp;")).isZero();
    }

    @Test
    void summaryTruncatesByCodePoint() {
        String summary = NoticeRichText.summary("<p>一二三四五六七八九十</p>", 5);
        assertThat(summary).isEqualTo("一二三四五…");
    }
}
