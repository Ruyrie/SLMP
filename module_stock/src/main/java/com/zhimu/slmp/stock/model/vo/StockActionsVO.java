package com.zhimu.slmp.stock.model.vo;

/** 单据可做的操作（只读菜单完全由它决定）：edit_state EDITABLE / REQUEST_ONLY / PENDING；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockActionsVO {
    public boolean canEdit;
    public String editDeadline;
    public Long pendingRequestId;
    public String editState;
    public Boolean canVoid;
    public Boolean canRequestChange;
    public Boolean canScrap;
    public Boolean canOut;
    public Boolean canRefund;
    public Boolean canEditRefund;
}
