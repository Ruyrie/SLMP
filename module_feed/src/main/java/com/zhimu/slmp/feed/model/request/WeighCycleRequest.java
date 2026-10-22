package com.zhimu.slmp.feed.model.request;

/**
 * API-G23 修改 / 恢复称重周期（所有者、场长）：per_month（1–8）与 reset 二选一；
 * version 为批次生长记录的版本，WeighFormVO、WeighCycleVO 暂未返回（待定事项 1），没有时不传。
 */
public class WeighCycleRequest {

    public Integer perMonth;
    public Boolean reset;
    public Integer version;

    public static WeighCycleRequest perMonth(int perMonth, Integer version) {
        WeighCycleRequest r = new WeighCycleRequest();
        r.perMonth = perMonth;
        r.version = version;
        return r;
    }

    public static WeighCycleRequest reset(Integer version) {
        WeighCycleRequest r = new WeighCycleRequest();
        r.reset = true;
        r.version = version;
        return r;
    }
}
