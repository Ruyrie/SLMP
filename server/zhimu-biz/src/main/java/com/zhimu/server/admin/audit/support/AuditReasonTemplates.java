package com.zhimu.server.admin.audit.support;

import com.zhimu.server.admin.audit.vo.ReasonTemplateVO;

import java.util.List;

/** 审核对话框的常用原因与通过意见预填（管理端-01 2.7、UI 文档 2.2）。 */
public final class AuditReasonTemplates {

    public static final String FARM_AUDIT_APPROVE = "资质图清晰完整，地图位置与地址一致。";
    public static final String FARM_CHANGE_APPROVE = "变更内容与资质材料一致，同意生效。";
    public static final String CHANGE_REQUEST_APPROVE = "修改原因充分，新值与原始单据一致，同意修改。";

    private AuditReasonTemplates() {
    }

    public static List<ReasonTemplateVO> farmAuditRejects() {
        return List.of(
                ReasonTemplateVO.of("资质图不清晰", "资质图不清晰，请重新拍摄并上传清晰、完整的证件照片。"),
                ReasonTemplateVO.of("缺少动物防疫条件合格证", "缺少动物防疫条件合格证，请补充后重新提交。"),
                ReasonTemplateVO.of("资质与养殖场名称不符", "资质材料上的名称与养殖场名称不一致，请核对后重新提交。"),
                ReasonTemplateVO.of("地图位置与地址不一致", "地图选点位置与填写的地址不一致，请重新选点后提交。"),
                ReasonTemplateVO.of("规模与资质证明不符", "设计规模与资质证明上的规模不一致，请核对后重新提交。")
        );
    }

    public static List<ReasonTemplateVO> farmChangeRejects() {
        return List.of(
                ReasonTemplateVO.of("名称与资质不符", "申请的名称与资质材料上的名称不一致，请核对后重新提交。"),
                ReasonTemplateVO.of("新位置与资质地址不一致", "新位置与资质材料上的地址不一致，请核对后重新提交。"),
                ReasonTemplateVO.of("新增资质图不清晰", "新增的资质图不清晰，请重新拍摄后提交。"),
                ReasonTemplateVO.of("变更说明不清楚", "变更说明不清楚，请补充修改原因和具体情况。")
        );
    }

    public static List<ReasonTemplateVO> changeRequestRejects() {
        return List.of(
                ReasonTemplateVO.of("与原始单据不一致", "与原始单据不一致，请核对单据后重新提交。"),
                ReasonTemplateVO.of("修改原因不充分", "修改原因不充分，请补充说明后重新提交。"),
                ReasonTemplateVO.of("新值超出业务范围", "新值超出业务允许范围，请核对后重新提交。"),
                ReasonTemplateVO.of("可由养殖户直接修改", "这条记录还可以直接修改，不需要提交修改申请。")
        );
    }
}
