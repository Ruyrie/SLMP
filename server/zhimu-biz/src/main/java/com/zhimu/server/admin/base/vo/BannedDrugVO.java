package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-D30 / D32 / D33 禁用药清单行。 */
@Getter
@Setter
public class BannedDrugVO {

    private Long id;
    private String name;
    private String alias;
    private String bannedClass;
    private String bannedClassText;
    private Long catalogId;
    private String reason;
    private LocalDateTime listedAt;
    private String listedByName;
    private Integer hitCount30d;
    private Integer version;
}
