package com.zhimu.server.biz.common.api;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.vo.MediaItemVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 业务记录与文件的关联（t_file + t_biz_media，公共能力 biz.common 实现）。业务模块只提交 file_id 数组。
 */
public interface BizMediaService {

    /**
     * 校验这些文件可以被业务记录引用：存在、本人上传、上传成功（READY 或视频压缩中 PROCESSING）、场景为 scene、
     * 没有被其他记录引用（已关联到 allowBoundTo 中记录的除外）。不合格抛 40001，field_errors 的字段名为 fieldName。
     */
    void validateForBind(List<Long> fileIds, FileScene scene, Long uploaderUserId, String fieldName,
                         BizRef... allowBoundTo);

    /** 把文件关联到记录并替换原有关联：按列表顺序 sort_no 从 1 开始，不在新列表中的解除关联；同时把文件标记为已引用 */
    void replace(BizType bizType, Long bizId, Long farmId, List<Long> fileIds);

    /** 复制关联（例如资料变更通过后把 FARM_CHANGE 的资质图复制到 FARM），sourceBiz 记为来源 */
    void copy(BizType fromType, Long fromId, BizType toType, Long toId, Long farmId);

    /** 解除记录的全部关联 */
    void removeAll(BizType bizType, Long bizId);

    /** 记录的照片和视频（带 2 小时临时地址），按 sort_no 排序 */
    List<MediaItemVO> list(BizType bizType, Long bizId);

    /** 批量查询多条记录的照片和视频 */
    Map<Long, List<MediaItemVO>> listBatch(BizType bizType, Collection<Long> bizIds);

    /** 记录关联的 file_id（按 sort_no） */
    List<Long> listFileIds(BizType bizType, Long bizId);
}