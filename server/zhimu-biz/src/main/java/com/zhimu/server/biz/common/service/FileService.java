package com.zhimu.server.biz.common.service;

import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 文件上传与访问（API-U05–U07，管理端-04 2.5.10 / 2.7.4）。
 * 不要求养殖场上下文：FARM_QUAL、AI_IMAGE 不校验养殖场状态且 farm_id 存空；其他场景有 X-Farm-Id 时记录，不校验状态。
 */
public interface FileService {

    /**
     * API-U05 上传图片或视频。
     *
     * @param file       multipart 字段 file
     * @param scene      上传场景（FARM_QUAL、INSPECTION、ABNORMAL、DISINFECT、DEATH、VOUCHER、AI_IMAGE）
     * @param cover      视频封面（选填，JPG）；为空时服务端截第 1 秒画面
     * @param durationMs 视频时长（App 已压缩时可选，服务端以 ffprobe 为准）
     * @param shotAt     视频拍摄时间（选填，图片忽略）
     * @param farmId     X-Farm-Id（可空）；FARM_QUAL、AI_IMAGE 存空
     */
    FileVO upload(MultipartFile file, FileScene scene, MultipartFile cover, Long durationMs,
                  LocalDateTime shotAt, Long farmId);

    /** API-U06 文件信息与新临时地址：本人上传的未关联文件，或有权查看任一关联记录 */
    FileVO get(Long id, Long userId);

    /** API-U07 删除尚未被记录引用的上传；已引用返回 40903 */
    void delete(Long id, Long userId);

    /** 文件存在且未删除时返回实体，否则 null */
    FileEntity find(Long id);
}
