package com.zhimu.slmp.inspect.model.request;

/**
 * API-I22 登记 / 修改处理结果（I07）：合计 = 发病数时 confirm_close 为 true；有诊断结果时 ai_match 必填；
 * to_medication 要求 used_drug；修改时带 version，不带幂等键。
 */
public class HandleSaveRequest {

    public String diagnostician;
    public String treatment;
    public Boolean usedDrug;
    public int curedCount;
    public int culledCount;
    public String aiMatch;
    public Boolean confirmClose;
    public Boolean toMedication;
    public Integer version;
}
