package com.zhimu.server.integration.ipregion;

/**
 * IP 属地（登录日志、操作留痕显示）。
 */
public interface IpRegionResolver {

    /**
     * @return “省 市”，例如“广东省 深圳市”（直辖市只显示一次，如“北京市”；国外只显示国家名）；
     *         内网 IP 返回“内网”；无法解析或数据文件未加载时返回 null
     */
    String resolve(String ip);
}
