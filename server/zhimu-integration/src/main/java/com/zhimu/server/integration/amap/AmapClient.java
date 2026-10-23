package com.zhimu.server.integration.amap;

import java.util.List;

/**
 * 高德 Web 服务 v3。
 */
public interface AmapClient {

    boolean isEnabled();

    /**
     * 逆地理编码（养殖场地址校验）。失败或未配置 Key 时返回 null，不抛异常（失败不阻断提交）。
     *
     * @param lng 经度（GCJ-02）
     * @param lat 纬度（GCJ-02）
     */
    AmapAddress reverseGeocode(double lng, double lat);

    /**
     * 行政区划查询（行情地区、平台概览）。
     *
     * @param keywords    关键字（名称或 adcode），为空查全国
     * @param subdistrict 返回下级行政区的层数（0–3）
     * @return 匹配的行政区（树形）
     * @throws com.zhimu.server.common.error.BizException 50301 未配置 Key 或调用失败
     */
    List<AmapDistrict> districts(String keywords, int subdistrict);
}
