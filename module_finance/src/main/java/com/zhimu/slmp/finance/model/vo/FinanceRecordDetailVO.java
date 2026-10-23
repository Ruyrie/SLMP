package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.util.List;

/**
 * API-E04 收支详情（E03 编辑回填、E04 作废取 version）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FinanceRecordDetailVO extends FinanceRecordVO {

    private List<MediaItemVO> vouchers;
    @Nullable
    private UserBriefVO createdBy;
    private int version;

    @NonNull
    public List<MediaItemVO> getVouchers() {
        return vouchers == null ? java.util.Collections.emptyList() : vouchers;
    }

    @Nullable
    public UserBriefVO getCreatedBy() {
        return createdBy;
    }

    public int getVersion() {
        return version;
    }
}
