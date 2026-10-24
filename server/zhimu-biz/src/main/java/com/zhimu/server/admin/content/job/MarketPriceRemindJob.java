package com.zhimu.server.admin.content.job;

import com.zhimu.server.admin.content.service.MarketPriceAdminService;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天 10:00：今天仍有未发布行情时，给拥有 WA-C02:HANDLE 的管理员发一条管理端消息（同一天只发一次）。 */
@Component
@RequiredArgsConstructor
public class MarketPriceRemindJob {

    private final MarketPriceAdminService marketPriceAdminService;
    private final AdminMessageService adminMessageService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Shanghai")
    @JobLock("market-price-remind")
    public void run() {
        ContentQueries.MarketDateQuery query = new ContentQueries.MarketDateQuery();
        query.setDate(BeijingTime.today());
        int count = marketPriceAdminService.progress(query).getUnpublishedCount();
        if (count <= 0) {
            return;
        }
        AdminMessageCommand command = AdminMessageCommand.builder()
                .msgType("MARKET_PRICE_UNPUBLISHED")
                .level("NORMAL")
                .title("今天还有 " + count + " 项行情未发布")
                .summary("10:00 仍未发布，用户端暂时显示上一个有数据的日期。")
                .bizType("MARKET_PRICE")
                .routePage("WA-C02")
                .mergeKey("MARKET_PRICE_UNPUBLISHED:" + BeijingTime.today())
                .build();
        adminMessageService.sendToPermission("WA-C02:HANDLE", command);
    }
}
