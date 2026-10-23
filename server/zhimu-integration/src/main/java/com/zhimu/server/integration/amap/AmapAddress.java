package com.zhimu.server.integration.amap;

/**
 * 逆地理编码结果。直辖市的 city 与 province 相同；没有区县的地级市（如东莞）district 为 null。
 *
 * @param province         省，例如“广东省”
 * @param city             市，例如“深圳市”
 * @param district         区县，例如“南山区”
 * @param adcode           区县行政区划代码，例如“440305”
 * @param formattedAddress 格式化地址
 */
public record AmapAddress(String province, String city, String district, String adcode, String formattedAddress) {
}
