package com.zhimu.server.biz.common.vo;

import com.zhimu.server.common.vo.MediaItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * U08 某条记录的照片和视频列表（管理端-04 2.5.10 BizMediaListVO）。
 * items 为 MediaItemVO（FileVO 全部字段 + sort_no、source_text、shot_by、shot_at），按 sort_no 排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizMediaListVO {

    private String bizType;
    private String bizTypeText;
    /** 记录描述，供 I09 来源说明 */
    private String recordDesc;
    @Builder.Default
    private List<MediaItemVO> items = new ArrayList<>();
}
