package com.zhimu.slmp.finance.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * E11 行情折线（UI 设计文档 2.2）：平均价 1 dp 虚线并标“平均 14.71”，最新价实心点、最低价空心点；
 * 没有发布价格的日期断开；按住显示“10月12日 · 14.90 元”。
 */
public class PriceTrendChartView extends TrendChartView {

    public PriceTrendChartView(@NonNull Context context) {
        this(context, null);
    }

    public PriceTrendChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFormatter(new Formatter() {
            @NonNull
            @Override
            public String axis(float value) {
                return ChartKit.scale(value, 2);
            }

            @NonNull
            @Override
            public String date(@NonNull String date) {
                return FinanceFormat.monthDay(date);
            }

            @NonNull
            @Override
            public String bubble(@NonNull String date, float value) {
                return FinanceFormat.monthDay(date) + " · " + ChartKit.scale(value, 2) + " 元";
            }
        });
    }

    public void setSeries(@NonNull List<MarketTrendVO.PricePoint> series, @Nullable BigDecimal avgPrice) {
        List<String> ds = new ArrayList<>();
        List<Float> vs = new ArrayList<>();
        for (MarketTrendVO.PricePoint p : series) {
            ds.add(p.getDate());
            vs.add(p.getPrice() == null ? null : p.getPrice().floatValue());
        }
        setData(ds, vs, avgPrice == null ? null : avgPrice.floatValue(),
                avgPrice == null ? null : "平均 " + FinanceFormat.price(avgPrice), false, true, null);
        setContentDescription(series.isEmpty() ? null : "价格走势，" + DateFormatter.formatDateRange(
                series.get(0).getDate(), series.get(series.size() - 1).getDate()));
    }
}
