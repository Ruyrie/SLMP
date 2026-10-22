package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;

/**
 * 资质图（RegistrationVO、ChangeSnapshotVO 中）：在 FileVO 的 file_id、url、thumb_url、width、height 之外，
 * 有 sort_no、reshoot（审核结果中标“需重拍”）、is_new（资料变更 after 中标“新增”）。
 */
public class QualFileVO extends FileVO {

    private int sortNo;
    @Nullable
    private Boolean reshoot;
    @Nullable
    private Boolean isNew;

    public int getSortNo() {
        return sortNo;
    }

    public boolean isReshoot() {
        return Boolean.TRUE.equals(reshoot);
    }

    public boolean isNew() {
        return Boolean.TRUE.equals(isNew);
    }
}
