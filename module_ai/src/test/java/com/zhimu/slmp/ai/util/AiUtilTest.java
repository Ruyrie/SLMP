package com.zhimu.slmp.ai.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.ai.data.AnswerTypeTag;
import com.zhimu.slmp.ai.model.AiCodes;

import org.junit.Test;

/**
 * 纯 Java 工具：流式正文缓冲（[^n] 与未闭合尾巴）、回答类型标记、输入计数与附图提示、观看秒数累计（拖动、2 倍速、失败累加）、
 * 知识正文白名单清洗、倍速文字。
 */
public class AiUtilTest {

    @Test
    public void streamTextBuffer_hidesCitationsAndPartialTail() {
        StreamTextBuffer b = new StreamTextBuffer();
        b.append("\n休药期为 20 天[");
        assertEquals("休药期为 20 天", b.visible());
        b.append("^1");
        assertEquals("休药期为 20 天", b.visible());
        b.append("]，从末次用药日起算[^12");
        assertEquals("休药期为 20 天，从末次用药日起算", b.visible());
        b.append("]。");
        assertEquals("休药期为 20 天，从末次用药日起算。", b.visible());
        assertTrue(b.raw().contains("[^12]"));
        assertEquals("a b", AiText.stripCitations("a[^3] b"));
    }

    @Test
    public void answerTypeTag_streamedAcrossChunks() {
        AnswerTypeTag t = new AnswerTypeTag();
        assertEquals("", t.feed("[["));
        assertEquals("", t.feed("EPIDE"));
        assertEquals("高烧", t.feed("MIC]]\n高烧"));
        assertEquals("、皮肤发紫", t.feed("、皮肤发紫"));
        assertEquals(AiCodes.ANSWER_EPIDEMIC, t.getType());

        AnswerTypeTag plain = new AnswerTypeTag();
        assertEquals("不需要全停", plain.feed("不需要全停"));
        assertEquals(AiCodes.ANSWER_NORMAL, plain.getType());

        String[] split = AnswerTypeTag.split("[[OFF_TOPIC]]\n这个问题与畜禽养殖无关，我不能回答。");
        assertEquals(AiCodes.ANSWER_OFF_TOPIC, split[0]);
        assertEquals("这个问题与畜禽养殖无关，我不能回答。", split[1]);
        assertEquals(AiCodes.ANSWER_NORMAL, AnswerTypeTag.split("[[WHAT]]正文")[0]);
    }

    @Test
    public void inputCountAndAttachHint() {
        assertNull(AiText.inputCount(479));
        assertEquals("485 / 500", AiText.inputCount(485));
        assertEquals("问题不能超过 500 字 · 500 / 500", AiText.inputCount(500));
        assertEquals("已附 2 张 · 最多 9 张", AiText.attachHint(2, 0));
        assertEquals("1 张图片上传失败，点按重试", AiText.attachHint(2, 1));
        assertNull(AiText.attachHint(0, 0));
        assertEquals("回答超时，点按重新生成", AiText.statusLine(AiCodes.STATUS_TIMEOUT, null, true));
        assertEquals("AI 助手暂时不可用，点按重新生成", AiText.statusLine(AiCodes.STATUS_FAILED, null, false));
        assertEquals("回答中断，点按重新生成", AiText.statusLine(AiCodes.STATUS_FAILED, null, true));
        assertEquals("ZM2610-031 已不在栏，之后的提问按通用养殖知识回答",
                AiText.invalidBatchTip(AiCodes.LINK_NOT_IN_STOCK, "ZM2610-031"));
        assertEquals("1.0x", AiText.speed(1f));
        assertEquals("1.25x", AiText.speed(1.25f));
        assertEquals("0.75x", AiText.speed(0.75f));
        assertEquals("看到 2:43", AiText.learnState(AiCodes.LEARN_LEARNING, 163));
    }

    @Test
    public void progressTracker_excludesSeekJumps_speedAndRetries() {
        ProgressTracker t = new ProgressTracker();
        t.onTick(0, 0, true, 1f);
        boolean due = false;
        for (int i = 1; i <= 10; i++) {
            due = t.onTick(i * 1000L, i * 1000L, true, 1f);
        }
        assertTrue(due);
        assertEquals(10, t.peekWatched());
        // 拖动到 100 秒：增量超过“间隔 × 倍速 + 1 秒”，不计
        t.onTick(100_000, 11_000, true, 1f);
        assertEquals(10, t.peekWatched());
        // 2 倍速：每秒前进 2 秒，计入
        t.onTick(102_000, 12_000, true, 2f);
        assertEquals(12, t.peekWatched());
        // 上报失败不扣除；成功后扣除
        t.commit(12);
        assertEquals(0, t.peekWatched());
        // 暂停时不计
        t.onTick(102_000, 20_000, false, 1f);
        assertEquals(0, t.peekWatched());
        for (int i = 0; i < 70; i++) {
            t.onTick(102_000 + (i + 1) * 1000L, 20_000 + (i + 1) * 1000L, true, 1f);
        }
        assertEquals(60, t.peekWatched());
    }

    @Test
    public void htmlSanitizer_whitelist() {
        String s = KnowledgeHtmlSanitizer.sanitize(
                "<p style=\"x\">先<strong class=\"a\">隔离</strong></p><script>alert(1)</script><ul><li onclick=\"y\">停止调运</li></ul><br/><img src=x>");
        assertEquals("先<strong>隔离</strong><ul><li>停止调运</li></ul><br>", s);
        assertFalse(s.contains("script"));
        assertEquals("", KnowledgeHtmlSanitizer.sanitize(null));
    }
}
