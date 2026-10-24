package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行情更正记录（t_market_price_correction）：涨跌超过 20% 时先待复核，通过后生效。 */
@Getter
@Setter
@TableName("t_market_price_correction")
public class MarketPriceCorrection extends BaseEntity {

    private Long priceId;
    private LocalDate priceDate;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal changeRateAfter;
    private String reason;
    /** PENDING_REVIEW / APPLIED / RETURNED */
    private String status;
    private Boolean pendingFlag;
    private Long correctedBy;
    private LocalDateTime correctedAt;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewNote;
}
