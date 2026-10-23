package com.zhimu.slmp.finance.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.ai.AiDirectConfig;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Test;

import java.util.List;

/**
 * E12 测试直连的真实调用验证（默认跳过，避免每次构建都请求模型）：环境变量 ZMT_AI_LIVE=1 且 debug 包配置了 Key 时，
 * 用 ZM2610-019（不在休药期）请求一次千问，检查输出能整理为合规的 SaleAdviceVO。
 */
public class DirectAdviceLiveTest {

    @Test
    public void generateWithQwen() throws Exception {
        assumeTrue("设置 ZMT_AI_LIVE=1 时运行", "1".equals(System.getenv("ZMT_AI_LIVE")));
        assumeTrue("debug 包未配置 Key", AiDirectConfig.isEnabled());
        List<SaleBatchOptionVO> batches = TestRules.mock("batch_options.json",
                new TypeToken<List<SaleBatchOptionVO>>() {
                }.getType());
        MarketTrendVO trend = TestRules.mock("trend_30.json", MarketTrendVO.class);
        DirectAdviceRepository repo = new DirectAdviceRepository(AiChatClient.getDefault(), GsonFactory.get());
        SaleAdviceVO a = repo.generate(batches.get(1), trend).blockingGet();
        System.out.println("status=" + a.getStatus() + " conclusion=" + a.getConclusion() + " window="
                + a.getWindowStart() + "~" + a.getWindowEnd() + " risks=" + a.getRisks() + " source=" + a.getSourceLabel());
        assertEquals(FinanceConst.GEN_SUCCESS, a.getStatus());
        assertTrue(FinanceConst.SELL_SOON.equals(a.getConclusion()) || FinanceConst.KEEP_FEEDING.equals(a.getConclusion()));
        assertNotNull(a.getWindowStart());
        assertTrue(a.getRisks().size() <= 3);
        assertNotNull(a.getSourceLabel());
    }
}
