package com.zhimu.slmp.finance.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * E13 报表趋势图（UI 设计文档 2.2）：STOCK 期末存栏折线并在出入栏拐点标注“出栏 202”“入栏 200”；
 * MORTALITY 逐日累计死亡率折线；FEED 逐日饲料用量柱状图（柱宽 6 dp）；BALANCE 累计结余折线。
 */
public class ReportTrendChartView extends TrendChartView {

    @NonNull
    private String metric = FinanceConst.METRIC_STOCK;

    public ReportTrendChartView(@NonNull Context context) {
        this(context, null);
    }

    public ReportTrendChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFormatter(new Formatter() {
            @NonNull
            @Override
            public String axis(float value) {
                return axisText(value);
            }

            @NonNull
            @Override
            public String date(@NonNull String date) {
                return FinanceFormat.monthDay(date);
            }

            @NonNull
            @Override
            public String bubble(@NonNull String date, float value) {
                return FinanceFormat.monthDay(date) + " · " + valueText(value);
            }
        });
    }

    @NonNull
    String axisText(float v) {
        if (FinanceConst.METRIC_MORTALITY.equals(metric)) {
            return ChartKit.scale(v * 100, 2) + "%";
        }
        return FinanceFormat.num(BigDecimal.valueOf(Math.round(v)));
    }

    @NonNull
    String valueText(float v) {
        switch (metric) {
            case FinanceConst.METRIC_MORTALITY:
                return ChartKit.scale(v * 100, 2) + "%";
            case FinanceConst.METRIC_FEED:
                return FinanceFormat.kg(BigDecimal.valueOf(Math.round(v)));
            case FinanceConst.METRIC_BALANCE:
                return FinanceFormat.yuan(BigDecimal.valueOf(Math.round(v)));
            default:
                return FinanceFormat.num(BigDecimal.valueOf(Math.round(v)));
        }
    }

    public void setTrend(@NonNull ReportTrendVO t) {
        metric = t.getMetric() == null ? FinanceConst.METRIC_STOCK : t.getMetric();
        List<String> ds = new ArrayList<>();
        List<Float> vs = new ArrayList<>();
        for (ReportTrendVO.TrendPoint p : t.getPoints()) {
            ds.add(p.getDate());
            vs.add(p.getValue() == null ? null : p.getValue().floatValue());
        }
        List<Mark> marks = new ArrayList<>();
        if (FinanceConst.METRIC_STOCK.equals(metric)) {
            for (ReportTrendVO.StockEvent e : t.getEvents()) {
                int i = ds.indexOf(e.getDate());
                if (i >= 0) {
                    marks.add(new Mark(i, (FinanceConst.EVENT_IN.equals(e.getType()) ? "入栏 " : "出栏 ")
                            + e.getCount()));
                }
            }
        }
        setData(ds, vs, null, null, FinanceConst.METRIC_FEED.equals(metric), false, marks);
    }
}
