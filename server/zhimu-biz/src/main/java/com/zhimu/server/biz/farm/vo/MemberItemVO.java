package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 成员列表项（F16） */
@Data
public class MemberItemVO {
    private Long memberId;
    private Long userId;
    private String name;
    private String nameInitial;
    private String avatarUrl;
    private String phoneMasked;
    private String role;
    private String roleText;
    private String joinType;
    private String joinTypeText;
    private String joinedAt;
    private Boolean isMe;
    private Boolean allPens;
    private Boolean canAdjustPens;
    private List<PenBriefVO> pens;
    private Integer version;
}
