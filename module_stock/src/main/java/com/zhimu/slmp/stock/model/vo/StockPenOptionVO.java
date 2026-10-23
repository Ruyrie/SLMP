package com.zhimu.slmp.stock.model.vo;

/** API-L07 栏舍下拉（usage = OPTIONS，模块04 接口；M11 关联栏舍，待定事项 2）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockPenOptionVO {
    public long penId;
    public String penName;
    public int stockCount;
    public boolean disabled;
    public String unitText;
}
