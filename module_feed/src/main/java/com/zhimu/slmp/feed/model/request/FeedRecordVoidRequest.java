package com.zhimu.slmp.feed.model.request;

/** API-G07 作废当天投喂记录（所有者、场长）：关联饲喂领用出库同步作废。 */
public class FeedRecordVoidRequest {

    public int version;

    public FeedRecordVoidRequest() {
    }

    public FeedRecordVoidRequest(int version) {
        this.version = version;
    }
}
