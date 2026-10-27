package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeTargetMapper;
import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.common.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeMessageQueryServiceTest {
    private final NoticeMapper notices = mock(NoticeMapper.class);
    private final NoticeTargetMapper targets = mock(NoticeTargetMapper.class);
    private final FileService files = mock(FileService.class);
    private final NoticeAudienceService audience = mock(NoticeAudienceService.class);
    private final NoticeMessageQueryService service = new NoticeMessageQueryService(
            notices, targets, mock(ContentLookupMapper.class), audience, files);

    @BeforeEach
    void setUp() {
        ContentTableInfo.ensure();
        when(targets.selectList(any())).thenReturn(List.of());
        when(audience.farms(List.of())).thenReturn(List.of());
    }

    @Test
    void signsCurrentImageForEachReadInsteadOfUsingTheMessageSnapshot() {
        Notice notice = notice("SENT");
        when(notices.selectById(81L)).thenReturn(notice);
        when(files.fileVo(91L)).thenReturn(
                FileVO.builder().fileId(91L).url("https://storage.test/first-signature").build(),
                FileVO.builder().fileId(91L).url("https://storage.test/renewed-signature").build());

        var first = service.payload(81L);
        var renewed = service.payload(81L);

        assertThat(first.get("content")).isEqualTo("<p>通知正文</p>");
        assertThat(((FileVO) first.get("image")).getUrl()).endsWith("first-signature");
        assertThat(((FileVO) renewed.get("image")).getUrl()).endsWith("renewed-signature");
        assertThat(renewed.get("revoked")).isEqualTo(false);
    }

    @Test
    void revokedNoticeOmitsBodyAndImageForAlreadyReadAndUnreadRecipients() {
        Notice notice = notice("REVOKED");
        notice.setRevokedAt(LocalDateTime.of(2026, 10, 25, 12, 0));
        when(notices.selectById(81L)).thenReturn(notice);

        var payload = service.payload(81L);

        assertThat(payload).containsEntry("revoked", true)
                .containsEntry("revoked_at", notice.getRevokedAt())
                .doesNotContainKeys("content", "image");
        verify(files, never()).fileVo(any());
    }

    @Test
    void missingHistoricalNoticeAllowsTheExistingSnapshotFallback() {
        assertThat(service.payload(99L)).isNull();
        assertThat(service.revokedIds(List.of())).isEmpty();
    }

    private static Notice notice(String status) {
        Notice notice = new Notice();
        notice.setId(81L);
        notice.setStatus(status);
        notice.setTitle("平台通知");
        notice.setContent("<p>通知正文</p>");
        notice.setImageFileId(91L);
        return notice;
    }
}
