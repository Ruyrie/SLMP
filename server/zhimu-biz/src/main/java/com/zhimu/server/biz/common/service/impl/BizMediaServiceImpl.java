package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务记录与文件关联（t_biz_media + t_file）。
 * <p>
 * 唯一键 (biz_type, biz_id, file_id) 不含 deleted：重新绑定同一文件时先查含 deleted 的行，存在则复活（deleted=0、更新 sort_no），
 * 不能直接 insert。跨养殖场查询统一走 {@link TenantSkip}，由调用方按记录所属养殖场判权。
 */
@Service
@RequiredArgsConstructor
public class BizMediaServiceImpl implements BizMediaService {

    private final BizMediaMapper bizMediaMapper;
    private final FileMapper fileMapper;
    private final FileVoAssembler fileVoAssembler;
    private final ObjectProvider<AccountQueryService> accountQueryService;

    @Override
    public void validateForBind(List<Long> fileIds, FileScene scene, Long uploaderUserId, String fieldName,
                                BizRef... allowBoundTo) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        String field = fieldName == null || fieldName.isBlank() ? "file_ids" : fieldName;
        for (Long fileId : fileIds) {
            if (fileId == null) {
                throw CommonErrors.fieldError(field, "文件不存在或已删除");
            }
            FileEntity file = fileMapper.selectById(fileId);
            if (file == null) {
                throw CommonErrors.fieldError(field, "文件不存在或已删除");
            }
            if (uploaderUserId != null && !uploaderUserId.equals(file.getCreatedBy())) {
                throw CommonErrors.fieldError(field, "文件不存在或不是本人上传");
            }
            if (!UploadStatus.READY.name().equals(file.getUploadStatus())) {
                throw CommonErrors.fieldError(field, "文件还没有上传完成");
            }
            if (scene != null && !scene.name().equals(file.getScene())) {
                throw CommonErrors.fieldError(field, "文件上传场景不匹配");
            }
            if (isBoundToOther(fileId, allowBoundTo)) {
                throw CommonErrors.fieldError(field, "文件已用在其他记录中，请在原记录中删除");
            }
        }
    }

    private boolean isBoundToOther(Long fileId, BizRef[] allowBoundTo) {
        List<BizMedia> rows = TenantSkip.call(() -> bizMediaMapper.selectActiveByFile(fileId));
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        if (allowBoundTo == null || allowBoundTo.length == 0) {
            return true;
        }
        for (BizMedia row : rows) {
            boolean allowed = false;
            for (BizRef ref : allowBoundTo) {
                if (ref != null && ref.bizType() != null && ref.bizType().name().equals(row.getBizType())
                        && ref.bizId() != null && ref.bizId().equals(row.getBizId())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void replace(BizType bizType, Long bizId, Long farmId, List<Long> fileIds) {
        if (bizType == null || bizId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        List<Long> target = distinct(fileIds);
        LocalDateTime now = BeijingTime.now();
        TenantSkip.run(() -> {
            // Retiring a cancellation attachment takes the same file lock before marking it deleted.
            for (Long fileId : target.stream().sorted().toList()) {
                if (fileMapper.selectActiveForUpdate(fileId) == null) {
                    throw CommonErrors.fieldError("file_ids", "文件不存在或已删除");
                }
            }
            for (int i = 0; i < target.size(); i++) {
                Long fileId = target.get(i);
                int sortNo = i + 1;
                BizMedia existing = bizMediaMapper.selectIncludingDeleted(bizType.name(), bizId, fileId);
                if (existing == null) {
                    BizMedia row = new BizMedia();
                    row.setBizType(bizType.name());
                    row.setBizId(bizId);
                    row.setFileId(fileId);
                    row.setFarmId(farmId);
                    row.setSortNo(sortNo);
                    row.setDeleted(0);
                    bizMediaMapper.insert(row);
                } else {
                    // 唯一键包含已删除的行：复活并更新 sort_no，不能直接 insert
                    bizMediaMapper.revive(existing.getId(), sortNo, farmId, now);
                }
                fileMapper.updateBound(fileId, 1, now);
            }
            List<Long> removed;
            if (target.isEmpty()) {
                removed = fileIdsOf(bizMediaMapper.selectActiveByBiz(bizType.name(), bizId));
                bizMediaMapper.deleteByBiz(bizType.name(), bizId, now);
            } else {
                removed = fileIdsOf(bizMediaMapper.selectActiveByBiz(bizType.name(), bizId));
                bizMediaMapper.deleteNotIn(bizType.name(), bizId, target, now);
                removed.removeAll(target);
            }
            for (Long fileId : removed) {
                if (bizMediaMapper.countActiveByFile(fileId) == 0) {
                    fileMapper.updateBound(fileId, 0, now);
                }
            }
        });
    }

    @Override
    @Transactional
    public void copy(BizType fromType, Long fromId, BizType toType, Long toId, Long farmId) {
        if (fromType == null || fromId == null || toType == null || toId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        List<Long> source = TenantSkip.call(() -> fileIdsOf(bizMediaMapper.selectActiveByBiz(fromType.name(), fromId)));
        if (source.isEmpty()) {
            return;
        }
        replace(toType, toId, farmId, source);
        TenantSkip.run(() -> bizMediaMapper.markSource(toType.name(), toId, fromType.name(), fromId, BeijingTime.now()));
    }

    @Override
    @Transactional
    public void removeAll(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        TenantSkip.run(() -> {
            List<Long> fileIds = fileIdsOf(bizMediaMapper.selectActiveByBiz(bizType.name(), bizId));
            bizMediaMapper.deleteByBiz(bizType.name(), bizId, now);
            for (Long fileId : fileIds) {
                if (bizMediaMapper.countActiveByFile(fileId) == 0) {
                    fileMapper.updateBound(fileId, 0, now);
                }
            }
        });
    }

    @Override
    public List<MediaItemVO> list(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return new ArrayList<>();
        }
        return TenantSkip.call(() -> assemble(bizType, bizMediaMapper.selectActiveByBiz(bizType.name(), bizId)));
    }

    @Override
    public Map<Long, List<MediaItemVO>> listBatch(BizType bizType, Collection<Long> bizIds) {
        Map<Long, List<MediaItemVO>> result = new LinkedHashMap<>();
        if (bizType == null || bizIds == null || bizIds.isEmpty()) {
            return result;
        }
        List<Long> ids = bizIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        List<BizMedia> rows = TenantSkip.call(() -> bizMediaMapper.selectActiveByBizIds(bizType.name(), ids));
        Map<Long, List<BizMedia>> grouped = new LinkedHashMap<>();
        for (BizMedia row : rows) {
            grouped.computeIfAbsent(row.getBizId(), key -> new ArrayList<>()).add(row);
        }
        Map<Long, FileEntity> files = loadFiles(rows);
        Map<Long, String> names = loadUploaderNames(files.values());
        Map<Long, FileEntity> covers = fileVoAssembler.loadCovers(files.values());
        for (Long id : ids) {
            List<BizMedia> media = grouped.getOrDefault(id, List.of());
            List<MediaItemVO> items = new ArrayList<>(media.size());
            for (BizMedia row : media) {
                FileEntity file = files.get(row.getFileId());
                if (file == null) {
                    continue;
                }
                items.add(fileVoAssembler.toMediaItem(file, row.getSortNo(), sourceText(row, bizType),
                        names.get(file.getCreatedBy()), file.getShotAt(), covers));
            }
            result.put(id, items);
        }
        return result;
    }

    @Override
    public List<Long> listFileIds(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return new ArrayList<>();
        }
        return TenantSkip.call(() -> fileIdsOf(bizMediaMapper.selectActiveByBiz(bizType.name(), bizId)));
    }

    private List<MediaItemVO> assemble(BizType bizType, List<BizMedia> rows) {
        List<MediaItemVO> items = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return items;
        }
        Map<Long, FileEntity> files = loadFiles(rows);
        Map<Long, String> names = loadUploaderNames(files.values());
        Map<Long, FileEntity> covers = fileVoAssembler.loadCovers(files.values());
        for (BizMedia row : rows) {
            FileEntity file = files.get(row.getFileId());
            if (file == null) {
                continue;
            }
            items.add(fileVoAssembler.toMediaItem(file, row.getSortNo(), sourceText(row, bizType),
                    names.get(file.getCreatedBy()), file.getShotAt(), covers));
        }
        return items;
    }

    private Map<Long, FileEntity> loadFiles(List<BizMedia> rows) {
        Map<Long, FileEntity> files = new LinkedHashMap<>();
        List<Long> ids = rows.stream().map(BizMedia::getFileId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return files;
        }
        for (FileEntity file : fileMapper.selectBatchIds(ids)) {
            files.put(file.getId(), file);
        }
        return files;
    }

    private Map<Long, String> loadUploaderNames(Collection<FileEntity> files) {
        Map<Long, String> names = new LinkedHashMap<>();
        AccountQueryService service = accountQueryService.getIfAvailable();
        if (service == null) {
            return names;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (FileEntity file : files) {
            if (file.getCreatedBy() != null) {
                userIds.add(file.getCreatedBy());
            }
        }
        if (userIds.isEmpty()) {
            return names;
        }
        try {
            Map<Long, AccountBrief> briefs = service.listBriefs(userIds);
            if (briefs != null) {
                briefs.forEach((id, brief) -> {
                    if (brief != null && brief.name() != null) {
                        names.put(id, brief.name());
                    }
                });
            }
        } catch (RuntimeException e) {
            // 姓名只用于展示，查询失败不影响媒体列表
        }
        return names;
    }

    /** 来源标签：文件最初来源业务类型的文字，没有来源时用当前记录类型 */
    private static String sourceText(BizMedia row, BizType bizType) {
        String sourceType = row.getSourceBizType();
        if (sourceType == null || sourceType.isBlank()) {
            return ChangeBizTypes.textOf(bizType);
        }
        BizType type = com.zhimu.server.common.enums.CodeEnum.of(BizType.class, sourceType);
        return type == null ? ChangeBizTypes.textOf(bizType) : ChangeBizTypes.textOf(type);
    }

    private static List<Long> fileIdsOf(List<BizMedia> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (BizMedia row : rows) {
            if (row.getFileId() != null) {
                ids.add(row.getFileId());
            }
        }
        return ids;
    }

    private static List<Long> distinct(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(fileIds.stream().filter(java.util.Objects::nonNull).toList()));
    }
}
