package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.impl.BizMediaServiceImpl;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.MediaItemVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BizMediaService：唯一键 + 逻辑删除的复活、校验、复制、批量列表。
 */
class BizMediaServiceTest {

    private final BizMediaMapper bizMediaMapper = mock(BizMediaMapper.class);
    private final FileMapper fileMapper = mock(FileMapper.class);
    private final FileVoAssembler assembler = mock(FileVoAssembler.class);
    private final BizMediaServiceImpl service = new BizMediaServiceImpl(bizMediaMapper, fileMapper, assembler,
            CommonFlowFixture.provider(null));

    @org.junit.jupiter.api.BeforeEach
    void lockAvailableFiles() {
        when(fileMapper.selectActiveForUpdate(any())).thenReturn(new FileEntity());
    }

    @Test
    void replaceRejectsRetiredCancellationFile() {
        when(fileMapper.selectActiveForUpdate(501L)).thenReturn(null);
        assertThatThrownBy(() -> service.replace(BizType.FEED_RECORD, 7001L, 1001L, List.of(501L)))
                .isInstanceOf(BizException.class);
        verify(bizMediaMapper, never()).insert(any(BizMedia.class));
    }

    @Test
    void replaceRevivesLogicallyDeletedRowInsteadOfInserting() {
        BizMedia deleted = media(77L, BizType.FEED_RECORD, 7001L, 501L, 1);
        when(bizMediaMapper.selectIncludingDeleted("FEED_RECORD", 7001L, 501L)).thenReturn(deleted);
        when(bizMediaMapper.selectActiveByBiz("FEED_RECORD", 7001L)).thenReturn(List.of());

        service.replace(BizType.FEED_RECORD, 7001L, 1001L, List.of(501L));

        verify(bizMediaMapper).revive(eq(77L), eq(1), eq(1001L), any(LocalDateTime.class));
        verify(bizMediaMapper, never()).insert(any(BizMedia.class));
        verify(fileMapper).updateBound(eq(501L), eq(1), any(LocalDateTime.class));
        verify(bizMediaMapper).deleteNotIn(eq("FEED_RECORD"), eq(7001L), eq(List.of(501L)), any(LocalDateTime.class));
    }

    @Test
    void replaceInsertsNewRowsWithSortNoStartingAtOne() {
        when(bizMediaMapper.selectIncludingDeleted(anyString(), any(), any())).thenReturn(null);
        when(bizMediaMapper.selectActiveByBiz("FEED_RECORD", 7001L)).thenReturn(List.of());

        service.replace(BizType.FEED_RECORD, 7001L, 1001L, List.of(501L, 502L));

        ArgumentCaptor<BizMedia> captor = ArgumentCaptor.forClass(BizMedia.class);
        verify(bizMediaMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(BizMedia::getSortNo).containsExactly(1, 2);
        assertThat(captor.getAllValues()).allSatisfy(row -> {
            assertThat(row.getBizType()).isEqualTo("FEED_RECORD");
            assertThat(row.getBizId()).isEqualTo(7001L);
            assertThat(row.getFarmId()).isEqualTo(1001L);
            assertThat(row.getDeleted()).isZero();
        });
        verify(fileMapper).updateBound(eq(501L), eq(1), any(LocalDateTime.class));
        verify(fileMapper).updateBound(eq(502L), eq(1), any(LocalDateTime.class));
    }

    @Test
    void replaceEmptyListUnbindsAllFlies() {
        when(bizMediaMapper.selectActiveByBiz("FEED_RECORD", 7001L)).thenReturn(List.of(
                media(1L, BizType.FEED_RECORD, 7001L, 501L, 0)));
        when(bizMediaMapper.countActiveByFile(501L)).thenReturn(0L);

        service.replace(BizType.FEED_RECORD, 7001L, 1001L, List.of());

        verify(bizMediaMapper).deleteByBiz(eq("FEED_RECORD"), eq(7001L), any(LocalDateTime.class));
        verify(fileMapper).updateBound(eq(501L), eq(0), any(LocalDateTime.class));
    }

    @Test
    void removeAllUnbindsFilesWithoutOtherRecords() {
        when(bizMediaMapper.selectActiveByBiz("INSPECTION", 7001L)).thenReturn(List.of(
                media(1L, BizType.INSPECTION, 7001L, 501L, 0)));
        when(bizMediaMapper.countActiveByFile(501L)).thenReturn(0L);

        service.removeAll(BizType.INSPECTION, 7001L);

        verify(bizMediaMapper).deleteByBiz(eq("INSPECTION"), eq(7001L), any(LocalDateTime.class));
        verify(fileMapper).updateBound(eq(501L), eq(0), any(LocalDateTime.class));
    }

    @Test
    void copyReplacesTargetAndMarksSource() {
        when(bizMediaMapper.selectActiveByBiz("FARM_CHANGE", 2001L)).thenReturn(List.of(
                media(1L, BizType.FARM_CHANGE, 2001L, 501L, 0)));
        when(bizMediaMapper.selectIncludingDeleted(anyString(), any(), any())).thenReturn(null);
        when(bizMediaMapper.selectActiveByBiz("FARM", 3001L)).thenReturn(List.of());

        service.copy(BizType.FARM_CHANGE, 2001L, BizType.FARM, 3001L, 1001L);

        verify(bizMediaMapper).markSource(eq("FARM"), eq(3001L), eq("FARM_CHANGE"), eq(2001L),
                any(LocalDateTime.class));
        verify(fileMapper).updateBound(eq(501L), eq(1), any(LocalDateTime.class));
    }

    @Test
    void listReturnsItemsOrderedBySortNoAndSourceText() {
        BizMedia first = media(1L, BizType.INSPECTION, 7001L, 501L, 0);
        first.setSortNo(1);
        first.setSourceBizType("ABNORMAL_REPORT");
        BizMedia second = media(2L, BizType.INSPECTION, 7001L, 502L, 0);
        second.setSortNo(2);
        when(bizMediaMapper.selectActiveByBiz("INSPECTION", 7001L)).thenReturn(List.of(first, second));
        FileEntity file1 = file(501L, 10001L, 1001L, "INSPECTION", "IMAGE", 1);
        FileEntity file2 = file(502L, 10001L, 1001L, "INSPECTION", "IMAGE", 1);
        when(fileMapper.selectBatchIds(anyList())).thenReturn(List.of(file1, file2));
        when(assembler.loadCovers(any())).thenReturn(Map.of());
        when(assembler.toMediaItem(any(), any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            FileEntity file = invocation.getArgument(0);
            MediaItemVO item = new MediaItemVO();
            item.setFileId(file.getId());
            item.setSortNo(invocation.getArgument(1));
            item.setSourceText(invocation.getArgument(2));
            return item;
        });

        List<MediaItemVO> items = service.list(BizType.INSPECTION, 7001L);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getFileId()).isEqualTo(501L);
        assertThat(items.get(0).getSortNo()).isEqualTo(1);
        assertThat(items.get(0).getSourceText()).isEqualTo("异常上报");
        assertThat(items.get(1).getSourceText()).isEqualTo("巡检记录");
    }

    @Test
    void listBatchGroupsByBizId() {
        BizMedia row1 = media(1L, BizType.INSPECTION, 7001L, 501L, 0);
        BizMedia row2 = media(2L, BizType.INSPECTION, 7002L, 502L, 0);
        when(bizMediaMapper.selectActiveByBizIds(eq("INSPECTION"), eq(List.of(7001L, 7002L))))
                .thenReturn(List.of(row1, row2));
        FileEntity file1 = file(501L, 10001L, 1001L, "INSPECTION", "IMAGE", 1);
        FileEntity file2 = file(502L, 10001L, 1001L, "INSPECTION", "IMAGE", 1);
        when(fileMapper.selectBatchIds(anyList())).thenReturn(List.of(file1, file2));
        when(assembler.loadCovers(any())).thenReturn(Map.of());
        when(assembler.toMediaItem(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            MediaItemVO item = new MediaItemVO();
            item.setFileId(((FileEntity) invocation.getArgument(0)).getId());
            return item;
        });

        Map<Long, List<MediaItemVO>> result = service.listBatch(BizType.INSPECTION, List.of(7001L, 7002L));

        assertThat(result).containsOnlyKeys(7001L, 7002L);
        assertThat(result.get(7001L)).hasSize(1);
        assertThat(result.get(7002L)).hasSize(1);
    }

    @Test
    void listFileIdsReturnsSortedIds() {
        BizMedia first = media(1L, BizType.INSPECTION, 7001L, 501L, 0);
        BizMedia second = media(2L, BizType.INSPECTION, 7001L, 502L, 0);
        when(bizMediaMapper.selectActiveByBiz("INSPECTION", 7001L)).thenReturn(List.of(first, second));
        assertThat(service.listFileIds(BizType.INSPECTION, 7001L)).containsExactly(501L, 502L);
    }

    @Test
    void validateForBindRejectsFileUsedByOtherRecord() {
        when(fileMapper.selectById(501L)).thenReturn(file(501L, 10001L, 1001L, "INSPECTION", "IMAGE", 1));
        BizMedia bound = media(1L, BizType.INSPECTION, 7002L, 501L, 0);
        when(bizMediaMapper.selectActiveByFile(501L)).thenReturn(List.of(bound));

        assertThatThrownBy(() -> service.validateForBind(List.of(501L), FileScene.INSPECTION, 10001L, "file_ids",
                new BizRef(BizType.ABNORMAL_REPORT, 7001L)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException ex = (BizException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    assertThat(String.valueOf(ex.getData())).contains("file_ids");
                });
    }

    @Test
    void validateForBindAllowsSameRecordAndProcessingVideo() {
        FileEntity processing = file(501L, 10001L, 1001L, "INSPECTION", "VIDEO", 1);
        processing.setTranscodeStatus("PROCESSING");
        when(fileMapper.selectById(501L)).thenReturn(processing);
        when(bizMediaMapper.selectActiveByFile(501L)).thenReturn(List.of(
                media(1L, BizType.INSPECTION, 7001L, 501L, 0)));

        service.validateForBind(List.of(501L), FileScene.INSPECTION, 10001L, "file_ids",
                new BizRef(BizType.INSPECTION, 7001L));
    }

    @Test
    void validateForBindRejectsNotReadyFile() {
        FileEntity uploading = file(501L, 10001L, 1001L, "INSPECTION", "VIDEO", 0);
        uploading.setUploadStatus("UPLOADING");
        when(fileMapper.selectById(501L)).thenReturn(uploading);

        assertThatThrownBy(() -> service.validateForBind(List.of(501L), FileScene.INSPECTION, 10001L, "file_ids"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateForBindRejectsWrongUploaderAndScene() {
        when(fileMapper.selectById(501L)).thenReturn(file(501L, 10002L, 1001L, "INSPECTION", "IMAGE", 0));
        assertThatThrownBy(() -> service.validateForBind(List.of(501L), FileScene.INSPECTION, 10001L, "file_ids"))
                .isInstanceOf(BizException.class);

        FileEntity wrongScene = file(502L, 10001L, 1001L, "DEATH", "IMAGE", 0);
        when(fileMapper.selectById(502L)).thenReturn(wrongScene);
        assertThatThrownBy(() -> service.validateForBind(List.of(502L), FileScene.INSPECTION, 10001L, "file_ids"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateForBindRejectsMissingFile() {
        when(fileMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.validateForBind(List.of(999L), FileScene.INSPECTION, 10001L, "file_ids"))
                .isInstanceOf(BizException.class);
    }

    private static BizMedia media(Long id, BizType bizType, Long bizId, Long fileId, int deleted) {
        BizMedia row = new BizMedia();
        row.setId(id);
        row.setBizType(bizType.name());
        row.setBizId(bizId);
        row.setFileId(fileId);
        row.setFarmId(1001L);
        row.setSortNo(1);
        row.setDeleted(deleted);
        return row;
    }

    static FileEntity file(Long id, Long createdBy, Long farmId, String scene, String kind, int bound) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setBucket("zmt-private");
        file.setObjectKey(scene.toLowerCase() + "/20261019/" + id + ".jpg");
        file.setFileKind(kind);
        file.setScene(scene);
        file.setMimeType("IMAGE".equals(kind) ? "image/jpeg" : "video/mp4");
        file.setFileSize(1024L);
        file.setUploadStatus("READY");
        file.setTranscodeStatus("NONE");
        file.setFarmId(farmId);
        file.setUploaderType("USER");
        file.setClientType("APP_ANDROID");
        file.setCreatedBy(createdBy);
        file.setBound(bound);
        return file;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
