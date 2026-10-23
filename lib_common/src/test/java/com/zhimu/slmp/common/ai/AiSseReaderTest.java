package com.zhimu.slmp.common.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

/**
 * SSE 按行读取：event + 多行 data、心跳注释行、\r\n、缺少结尾空行、网络分块落在中文字符中间。
 */
public class AiSseReaderTest {

    @Test
    public void eventsWithNamesAndMultiLineData() throws IOException {
        List<AiSseEvent> events = AiSseReader.readAll(
                "event: meta\ndata: {\"message_id\":5501}\n\n"
                        + ": ping\n\n"
                        + "event: delta\ndata: 第一行\ndata: 第二行\n\n"
                        + "id: 7\nretry: 1000\nevent: done\ndata:{}\n\n");
        assertEquals(4, events.size());
        assertEquals("meta", events.get(0).getEvent());
        assertEquals("{\"message_id\":5501}", events.get(0).getData());
        assertTrue(events.get(1).isComment());
        assertEquals("ping", events.get(1).getData());
        assertEquals("delta", events.get(2).getEvent());
        assertEquals("第一行\n第二行", events.get(2).getData());
        assertEquals("done", events.get(3).getEvent());
        assertEquals("{}", events.get(3).getData());
    }

    @Test
    public void openAiStyle_crlfAndDoneMarker_andMissingTrailingBlankLine() throws IOException {
        List<AiSseEvent> events = AiSseReader.readAll(
                "data: {\"a\":1}\r\n\r\n\r\ndata: [DONE]");
        assertEquals(2, events.size());
        assertNull(events.get(0).getEvent());
        assertEquals("{\"a\":1}", events.get(0).getData());
        assertFalse(events.get(0).isDoneMarker());
        assertTrue(events.get(1).isDoneMarker());
    }

    @Test
    public void chineseSplitAcrossNetworkChunks() throws IOException {
        byte[] bytes = "data: 休药期为 20 天\n\n".getBytes(StandardCharsets.UTF_8);
        BufferedSource source = Okio.buffer(new OneByteSource(bytes));
        AiSseReader reader = new AiSseReader(source);
        AiSseEvent e = reader.next();
        assertEquals("休药期为 20 天", e.getData());
        assertNull(reader.next());
    }

    /** 每次只返回 1 个字节，模拟分块到达 */
    private static final class OneByteSource implements Source {
        private final byte[] bytes;
        private int pos;

        OneByteSource(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public long read(Buffer sink, long byteCount) {
            if (pos >= bytes.length) {
                return -1;
            }
            sink.writeByte(bytes[pos++]);
            return 1;
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }

        @Override
        public void close() {
        }
    }
}
