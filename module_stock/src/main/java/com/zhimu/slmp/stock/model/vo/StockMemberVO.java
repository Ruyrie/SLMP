package com.zhimu.slmp.stock.model.vo;

/** API-F16 本场成员（模块02 接口；M11 领用人，待定事项 2）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockMemberVO {
    public long userId;
    public String name;
    public String role;
    public String roleText;
}
