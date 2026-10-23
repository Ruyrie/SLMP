package com.zhimu.server.biz.inspect.request;

import lombok.Data;

/** API-I22 登记 / 修改处理结果。 */
@Data
public class HandleSaveRequest {

    private String diagnostician;
    private String treatment;
    private Boolean usedDrug;
    private Integer curedCount;
    private Integer culledCount;
    private String aiMatch;
    private Boolean confirmClose;
    private Boolean toMedication;
    private Integer version;
}
