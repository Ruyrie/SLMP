package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 分片上传初始化 / 续传（ADM-S35）。 */
@Data
public class MultipartInitVO {

    private Long fileId;
    private Long partSize;
    private Integer partCount;
    private String originalName;
    private List<PartUrlVO> partUrls = new ArrayList<>();
    private List<UploadedPartVO> uploadedParts = new ArrayList<>();
    private LocalDateTime urlExpireAt;

    @Data
    public static class PartUrlVO {
        private Integer partNo;
        private String url;
    }

    @Data
    public static class UploadedPartVO {
        private Integer partNo;
        private String etag;
    }
}
