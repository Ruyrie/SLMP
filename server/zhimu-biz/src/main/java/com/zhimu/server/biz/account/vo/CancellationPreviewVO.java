package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** API-A19 注销预览 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationPreviewVO {
    private String phoneMasked;
    private String personalDataDesc;
    private String phoneReleaseDate;
    private String checklistVersion;
    private Boolean canCancel;
    private List<String> blockReasons;
    private String cancelAvailableDate;
    private String farmCleanupDate;
    private List<CancelOwnedFarmVO> ownedFarms;
    private Integer transferableFarmCount;
    private List<CancelExitFarmVO> exitFarms;
}
