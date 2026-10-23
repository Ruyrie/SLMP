package com.zhimu.server.biz.account.api;

import java.util.Collection;
import java.util.Map;

/**
 * 模块01 对内提供的账号查询与写入（模块01 文档 1.4 节的 AccountService、UserQueryService 中供其他模块调用的部分）。
 */
public interface AccountQueryService {

    /** 按手机号查有效账号（未注销）；没有返回 null */
    AccountBrief findByPhone(String phone);

    /** 按 ID 取账号（含已注销）；不存在返回 null */
    AccountBrief getBrief(Long userId);

    /** 按 ID 批量取姓名等简要信息；已注销账号的 name 为“已注销用户”；不存在的 ID 不在结果中 */
    Map<Long, AccountBrief> listBriefs(Collection<Long> userIds);

    /** 记录上次使用的养殖场（模块02 API-F03 切换养殖场时调用，原 API-A18） */
    void recordLastFarm(Long userId, Long farmId);
}