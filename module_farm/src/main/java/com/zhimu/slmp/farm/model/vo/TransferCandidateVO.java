package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 转让候选场长（TransferPageVO.candidates；客户端暂定类名）。
 */
public class TransferCandidateVO {

    private long memberId;
    private long userId;
    @Nullable
    private String name;
    @Nullable
    private String nameInitial;
    @Nullable
    private String phoneMasked;

    public long getMemberId() {
        return memberId;
    }

    public long getUserId() {
        return userId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getNameInitial() {
        return nameInitial;
    }

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransferCandidateVO)) {
            return false;
        }
        TransferCandidateVO that = (TransferCandidateVO) o;
        return memberId == that.memberId && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, name);
    }
}
