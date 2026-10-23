package com.zhimu.server.biz.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 文件（t_file，05 号 5.1 节）：MinIO 对象元信息，不存完整地址。
 * farm_id 为用户端上传时的当前养殖场；FARM_QUAL、AI_IMAGE 存空（任务书第七节 6）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_file")
public class FileEntity extends AuditEntity {

    private String bucket;
    private String objectKey;
    private String originalName;
    private String fileKind;
    private String scene;
    private String mimeType;
    private String fileExt;
    private Long fileSize;
    private String sha256;
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private Long coverFileId;
    private Long parentFileId;
    private String uploadStatus;
    private String multipartUploadId;
    private String transcodeStatus;
    private String transcodeError;
    private Long farmId;
    private String uploaderType;
    private String clientType;
    private LocalDateTime shotAt;
    private Integer bound;
    private LocalDateTime expireAt;

    @TableLogic
    private Integer deleted;
}
