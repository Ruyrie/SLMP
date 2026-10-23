package com.zhimu.server.integration.amap;

import java.util.List;

/**
 * 行政区划节点。
 *
 * @param adcode   行政区划代码
 * @param name     名称
 * @param level    country / province / city / district / street
 * @param center   中心点“经度,纬度”
 * @param citycode 城市区号（没有时为 null）
 * @param children 下级行政区（按 subdistrict 返回的层级）
 */
public record AmapDistrict(String adcode, String name, String level, String center, String citycode,
                           List<AmapDistrict> children) {
}
