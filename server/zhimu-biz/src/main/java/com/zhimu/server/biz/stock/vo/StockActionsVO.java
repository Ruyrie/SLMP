package com.zhimu.server.biz.stock.vo;

import lombok.Data;

/**
 * 单据可做的操作（只读菜单完全由它决定）：edit_state EDITABLE / REQUEST_ONLY / PENDING。
 * 与 App 的 {@code StockActionsVO} 一致；没有的布尔项不输出（null）。
 */
@Data
public class StockActionsVO {

    private boolean canEdit;
    private String editDeadline;
    private Long pendingRequestId;
    private String editState;
    private Boolean canVoid;
    private Boolean canRequestChange;
    private Boolean canScrap;
    private Boolean canOut;
    private Boolean canRefund;
    private Boolean canEditRefund;
}
