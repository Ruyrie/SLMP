package com.zhimu.slmp.stock.model.vo;

/** 批号（API-M25 列表；M05）；days_to_expire 过期时为负；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugLotItemVO {
    public long lotId;
    public long drugId;
    public String drugName;
    public String category;
    public String categoryText;
    public String lotNo;
    public String expireDate;
    public QtyVO stock;
    public String lotStatus;
    public String lotStatusText;
    public Integer daysToExpire;
    public boolean priorityOut;
    public String statusNote;
}
