package com.zhimu.server.biz.herd.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块04 计算口径与二维码解析的纯单元测试（文档 2.7 节测试要点）。 */
class HerdCalcTest {

    @Test
    void mortalityUsesFourDecimals() {
        assertThat(HerdSupport.mortality(5, 205)).isEqualByComparingTo(new BigDecimal("0.0244"));
        assertThat(HerdSupport.mortality(19, 898)).isEqualByComparingTo(new BigDecimal("0.0212"));
        assertThat(HerdSupport.mortality(0, 0)).isNull();
        assertThat(HerdSupport.mortality(null, 100)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void occupancyRateMatchesMock() {
        assertThat(HerdSupport.rate(200, 220)).isEqualByComparingTo(new BigDecimal("0.9091"));
        assertThat(HerdSupport.rate(870, 1600)).isEqualByComparingTo(new BigDecimal("0.5438"));
        assertThat(HerdSupport.rate(0, 400)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(HerdSupport.rate(1, 0)).isNull();
    }

    @Test
    void qrContentUsesTenCharacterToken() {
        String token = "k7Rm2xQ9pT";
        String content = QrCodes.content(token);
        assertThat(content).isEqualTo("https://web.zhimutong.example.com/s/b/k7Rm2xQ9pT");
        assertThat(QrCodes.parseToken(content)).isEqualTo(token);
        assertThat(QrCodes.parseToken("https://web.zhimutong.example.com/s/b/short")).isNull();
        assertThat(QrCodes.parseToken("https://other.example.com/s/b/k7Rm2xQ9pT")).isNull();
    }

    @Test
    void batchNoPadsSequenceAndExpandsAfter999() {
        assertThat(BatchNoGenerator.batchNo("2610", 31)).isEqualTo("ZM2610-031");
        assertThat(BatchNoGenerator.batchNo("2610", 1000)).isEqualTo("ZM2610-1000");
    }
}
