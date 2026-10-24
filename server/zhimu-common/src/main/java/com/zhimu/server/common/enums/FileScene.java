package com.zhimu.server.common.enums;

import lombok.Getter;

/**
 * 上传场景（05 号文档 6.4 节）：决定校验规则、存储桶和保留期。
 */
@Getter
public enum FileScene implements CodeEnum {
    FARM_QUAL("资质图", false, Bucket.PRIVATE),
    AVATAR("头像", false, Bucket.PRIVATE),
    INSPECTION("巡检", true, Bucket.PRIVATE),
    ABNORMAL("异常上报", true, Bucket.PRIVATE),
    DISINFECT("消毒照片", false, Bucket.PRIVATE),
    DEATH("死淘照片", false, Bucket.PRIVATE),
    VOUCHER("收支凭证", false, Bucket.PRIVATE),
    AI_IMAGE("AI 提问附图", false, Bucket.PRIVATE),
    AI_VOICE("语音提问", false, Bucket.PRIVATE),
    NOTICE_IMAGE("通知配图", false, Bucket.PRIVATE),
    COURSE_VIDEO("课程视频", true, Bucket.COURSE),
    COURSE_COVER("课程封面", false, Bucket.COURSE),
    TOPIC_COVER("专题封面", false, Bucket.COURSE),
    KNOWLEDGE_MEDIA("知识条目图片视频", true, Bucket.COURSE),
    VIDEO_COVER("视频封面", false, Bucket.PRIVATE),
    EXPORT("导出文件", false, Bucket.PRIVATE);

    /** 存储桶名称由配置决定，这里只区分私有桶与课程桶 */
    public enum Bucket { PRIVATE, COURSE }

    private final String text;
    /** 是否允许视频 */
    private final boolean videoAllowed;
    private final Bucket bucket;

    FileScene(String text, boolean videoAllowed, Bucket bucket) {
        this.text = text;
        this.videoAllowed = videoAllowed;
        this.bucket = bucket;
    }

    /** 对象路径前缀：{场景小写}/ */
    public String pathPrefix() {
        return name().toLowerCase();
    }
}
