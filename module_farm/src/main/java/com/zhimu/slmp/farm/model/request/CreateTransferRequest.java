package com.zhimu.slmp.farm.model.request;

/**
 * 发起转让（API-F29，要求 Idempotency-Key）：受让场长的 member_id。
 */
public class CreateTransferRequest {

    private final long toMemberId;

    public CreateTransferRequest(long toMemberId) {
        this.toMemberId = toMemberId;
    }

    public long getToMemberId() {
        return toMemberId;
    }
}
