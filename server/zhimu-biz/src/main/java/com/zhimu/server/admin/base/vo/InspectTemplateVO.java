package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-D40 / D41 巡检模板详情与用户端预览。 */
@Getter
@Setter
public class InspectTemplateVO {

    private Long templateId;
    private String name;
    private IdNameVO category;
    private Integer onlineVersionNo;
    private String publishedByName;
    private LocalDateTime publishedAt;
    /** 使用本模板的育肥猪栏舍数 */
    private Integer penCount;
    private Boolean hasDraft;
    private List<InspectDraftChangeVO> draftChanges = new ArrayList<>();
    /** 有草稿时为草稿项，否则为线上项 */
    private List<InspectItemVO> items = new ArrayList<>();
    /** 线上版本中启用的项目（用户端预览） */
    private List<InspectItemVO> previewItems = new ArrayList<>();
    private Integer version;
}
