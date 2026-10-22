package com.zhimu.slmp.media.data;

/**
 * 上传场景 scene（05 号数据库文档 6.4 节 FILE_SCENE），决定服务端的校验规则。
 * 本期只用到图片场景；INSPECTION、ABNORMAL 允许视频，在模块07 时接入。
 */
public final class MediaScene {

    private MediaScene() {
    }

    /** 养殖场资质图（F02、F06） */
    public static final String FARM_QUAL = "FARM_QUAL";
    /** 死淘照片（L11） */
    public static final String DEATH = "DEATH";
    /** 消毒现场照片（D08） */
    public static final String DISINFECT = "DISINFECT";
    /** 巡检（I02，可含视频） */
    public static final String INSPECTION = "INSPECTION";
    /** 异常上报（I05，可含视频） */
    public static final String ABNORMAL = "ABNORMAL";
    /** 收支凭证 */
    public static final String VOUCHER = "VOUCHER";
    /** AI 提问附图（K01） */
    public static final String AI_IMAGE = "AI_IMAGE";
}
